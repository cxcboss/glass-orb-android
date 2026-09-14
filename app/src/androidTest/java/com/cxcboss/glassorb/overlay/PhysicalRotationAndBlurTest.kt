package com.cxcboss.glassorb.overlay

import android.app.UiAutomation
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inspector.WindowInspector
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.cxcboss.glassorb.data.OrbConfigRepository
import com.cxcboss.glassorb.model.OrbConfig
import com.cxcboss.glassorb.render.OrbTextureView
import com.cxcboss.glassorb.ui.MainActivity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.Before
import java.io.File

/** Pixel checks on the actual compositor, not just matching coordinate formulas. */
class PhysicalRotationAndBlurTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)

    @Before fun allowTestNotifications() {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            automation.grantRuntimePermission(context.packageName, android.Manifest.permission.POST_NOTIFICATIONS)
        }
        // Instrumentation restarts the app process. Android can retain its
        // accessibility service in the crashed set after the previous run.
        shell("settings put secure enabled_accessibility_services ''")
        SystemClock.sleep(300)
    }

    @Test fun islandKeepsHardwarePositionAndAngleInEveryRotation() {
        shell("appops set ${context.packageName} android:system_alert_window allow")
        shell("settings put secure enabled_accessibility_services ${context.packageName}/.overlay.GlassOrbAccessibilityService")
        shell("settings put secure accessibility_enabled 1")
        val original = OrbConfigRepository.getInstance(context).config.value
        val config = OrbConfig().let { it.copy(
            geometry = it.geometry.copy(verticalOffsetDp = 12f, capsuleHeightDp = 48f,
                hideWhenLandscape = false, hideWhenStatusBarHidden = false),
            motion = it.motion.copy(breathingAmplitude = 0f, autoCollapseEnabled = false),
        ) }
        runBlocking { OrbConfigRepository.getInstance(context).replace(config) }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            try {
                await { AccessibilityOverlayBridge.isConnected() }
                scenario.onActivity { OrbOverlayService.start(it) }
                await { OverlayRuntime.status.value == OverlayRuntimeStatus.Visible }
                for (rotation in 0..3) {
                    assertTrue(automation.setRotation(rotation))
                    await { overlayRotation() == -90f * rotation }
                    scenario.onActivity { it.setContentView(View(it).apply { setBackgroundColor(Color.WHITE) }) }
                    SystemClock.sleep(1000)
                    val bitmap = requireNotNull(automation.takeScreenshot())
                    save(bitmap, "island-rotation-$rotation.png")
                    val current = IntRect(0, 0, bitmap.width, bitmap.height)
                    val size = RotationCoordinateSpace.portraitReferenceSize(current)
                    val density = context.resources.displayMetrics.density
                    val center = RotationCoordinateSpace.mapPortraitPoint(
                        FloatPoint(size.width / 2f, (12f + 24f) * density), current, rotation,
                    )
                    val halfLong = (45f * density).toInt()
                    // Long axis must stay parallel to the physical top edge.
                    for (offset in -halfLong..halfLong step 10) {
                        val x = center.x.toInt() + if (rotation % 2 == 0) offset else 0
                        val y = center.y.toInt() + if (rotation % 2 == 1) offset else 0
                        val pixel = bitmap.getPixel(x, y)
                        assertTrue("rotation=$rotation pixel=($x,$y) color=$pixel", Color.red(pixel) < 15 && Color.green(pixel) < 15 && Color.blue(pixel) < 15)
                    }
                    bitmap.recycle()
                    // Exercise the real rotated input target, then swipe
                    // towards the hardware top to collapse the expanded orb.
                    shell("input tap ${center.x.toInt()} ${center.y.toInt()}")
                    await { overlayState() == OverlayState.Wave }
                    val expanded = requireNotNull(automation.takeScreenshot())
                    save(expanded, "expanded-rotation-$rotation.png")
                    expanded.recycle()
                    val start = RotationCoordinateSpace.mapPortraitPoint(
                        FloatPoint(size.width / 2f, 90f * density), current, rotation)
                    val end = RotationCoordinateSpace.mapPortraitPoint(
                        FloatPoint(size.width / 2f, 16f * density), current, rotation)
                    shell("input swipe ${start.x.toInt()} ${start.y.toInt()} ${end.x.toInt()} ${end.y.toInt()} 220")
                    await { overlayState() == OverlayState.Collapsed }
                }
            } finally {
                scenario.onActivity { OrbOverlayService.stop(it) }
                automation.setRotation(UiAutomation.ROTATION_UNFREEZE)
                runBlocking { OrbConfigRepository.getInstance(context).replace(original) }
            }
        }
    }

    @Test fun actualOrbBlursBackgroundBelowItsForegroundAndTogglesWithoutReordering() {
        shell("appops set ${context.packageName} android:system_alert_window allow")
        shell("settings put secure enabled_accessibility_services ${context.packageName}/.overlay.GlassOrbAccessibilityService")
        shell("settings put secure accessibility_enabled 1")
        automation.setRotation(UiAutomation.ROTATION_FREEZE_0)
        val repository = OrbConfigRepository.getInstance(context)
        val original = repository.config.value
        val config = OrbConfig().let { it.copy(
            geometry = it.geometry.copy(verticalOffsetDp = 60f, hideWhenStatusBarHidden = false),
            motion = it.motion.copy(breathingAmplitude = 0f, autoCollapseEnabled = false),
            container = it.container.copy(strength = 0f, backgroundDimEnabled = false, backgroundBlurEnabled = false),
            glass = it.glass.copy(highlightAmount = 0f, shadowAmount = 0f, causticAmount = 0f),
            wave = it.wave.copy(intensity = 0f, bandFill = 0f, whiteBloom = 0f),
        ) }
        runBlocking { repository.replace(config) }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            try {
                await { AccessibilityOverlayBridge.isConnected() }
                scenario.onActivity { activity ->
                    activity.setContentView(object : View(activity) {
                        val paint = Paint()
                        override fun onDraw(canvas: Canvas) {
                            for (y in 0 until height step 12) for (x in 0 until width step 12) {
                                paint.color = if ((x / 12 + y / 12) % 2 == 0) Color.BLACK else Color.WHITE
                                canvas.drawRect(x.toFloat(), y.toFloat(), x + 12f, y + 12f, paint)
                            }
                        }
                    })
                    OrbOverlayService.start(activity)
                }
                await { overlayState() == OverlayState.Collapsed && overlayRotation() == 0f }
                // The previous test finishes in landscape. Wait for the
                // compositor/input transaction, not just submitted GL state.
                SystemClock.sleep(700)
                val density = context.resources.displayMetrics.density
                val initial = requireNotNull(automation.takeScreenshot())
                val screenWidth = initial.width
                initial.recycle()
                shell("input tap ${screenWidth / 2} ${(76f * density).toInt()}")
                await { overlayState() == OverlayState.Wave }
                SystemClock.sleep(600)
                val sharp = requireNotNull(automation.takeScreenshot())
                runBlocking { repository.replace(config.copy(container = config.container.copy(backgroundBlurEnabled = true))) }
                SystemClock.sleep(900)
                val blurred = requireNotNull(automation.takeScreenshot())
                save(sharp, "orb-blur-off.png"); save(blurred, "orb-blur-on.png")
                val roi = IntRect(screenWidth / 2 - 40, (144f * density).toInt(),
                    screenWidth / 2 + 40, (164f * density).toInt())
                val before = contrast(sharp, roi)
                val after = contrast(blurred, roi)
                assertTrue("Actual orb background contrast: $before -> $after", after < before * 0.5)
                runBlocking { repository.replace(config) }
                SystemClock.sleep(600)
                val restored = requireNotNull(automation.takeScreenshot())
                assertTrue("Orb blur toggle restores background", contrast(restored, roi) > before * 0.8)
                listOf(sharp, blurred, restored).forEach { it.recycle() }
            } finally {
                scenario.onActivity { OrbOverlayService.stop(it) }
                runBlocking { repository.replace(original) }
                automation.setRotation(UiAutomation.ROTATION_UNFREEZE)
            }
        }
    }

    @Test fun windowBlurReducesBackgroundDetailAndRestoresOnDisable() {
        shell("appops set ${context.packageName} android:system_alert_window allow")
        assertTrue("Device must support cross-window blur", context.getSystemService(WindowManager::class.java).isCrossWindowBlurEnabled)
        automation.setRotation(UiAutomation.ROTATION_FREEZE_0)
        val bounds = IntRect(300, 500, 780, 980)
        val blur = AndroidBackgroundBlurWindow(context, false)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            try {
                scenario.onActivity { activity ->
                    activity.setContentView(object : View(activity) {
                        val paint = Paint()
                        override fun onDraw(canvas: Canvas) {
                            for (y in 0 until height step 12) for (x in 0 until width step 12) {
                                paint.color = if ((x / 12 + y / 12) % 2 == 0) Color.BLACK else Color.WHITE
                                canvas.drawRect(x.toFloat(), y.toFloat(), x + 12f, y + 12f, paint)
                            }
                        }
                    })
                }
                main { assertTrue(blur.show(bounds, bounds, 0)) }
                SystemClock.sleep(500)
                val sharp = requireNotNull(automation.takeScreenshot())
                main { assertTrue(blur.update(bounds, bounds, 80)) }
                SystemClock.sleep(700)
                val blurred = requireNotNull(automation.takeScreenshot())
                save(sharp, "blur-off.png"); save(blurred, "blur-on.png")
                val roi = IntRect(450, 650, 630, 830)
                val before = contrast(sharp, roi)
                val after = contrast(blurred, roi)
                assertTrue("Background contrast must decrease: $before -> $after", after < before * 0.5)
                assertEquals("Outside aperture must remain sharp", contrast(sharp, IntRect(40, 650, 180, 830)),
                    contrast(blurred, IntRect(40, 650, 180, 830)), 1.0)
                main { blur.hide() }
                SystemClock.sleep(500)
                val restored = requireNotNull(automation.takeScreenshot())
                assertTrue("Disabling blur restores detail", contrast(restored, roi) > before * 0.9)
                main { assertTrue(blur.show(bounds, bounds, 80)) }
                SystemClock.sleep(500)
                val again = requireNotNull(automation.takeScreenshot())
                assertTrue("Re-enabling blur works", contrast(again, roi) < before * 0.5)
                listOf(sharp, blurred, restored, again).forEach { it.recycle() }
            } finally {
                main { blur.destroy() }
                automation.setRotation(UiAutomation.ROTATION_UNFREEZE)
            }
        }
    }

    private fun contrast(bitmap: Bitmap, roi: IntRect): Double {
        var sum = 0.0
        var square = 0.0
        var n = 0
        for (y in roi.top until roi.bottom) for (x in roi.left until roi.right) {
            val value = Color.red(bitmap.getPixel(x, y)).toDouble()
            sum += value; square += value * value; n++
        }
        return square / n - (sum / n) * (sum / n)
    }
    private fun save(bitmap: Bitmap, name: String) {
        File(context.getExternalFilesDir(null), name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    private fun overlayRotation(): Float? {
        var result: Float? = null
        fun visit(view: View) {
            if (view is OrbTextureView) result = view.rotation
            if (view is ViewGroup) for (i in 0 until view.childCount) visit(view.getChildAt(i))
        }
        main { WindowInspector.getGlobalWindowViews().forEach(::visit) }
        return result
    }
    private fun overlayState(): OverlayState? {
        var result: OverlayState? = null
        fun visit(view: View) {
            if (view is OrbTextureView) result = view.submittedState
            if (view is ViewGroup) for (i in 0 until view.childCount) visit(view.getChildAt(i))
        }
        main { WindowInspector.getGlobalWindowViews().forEach(::visit) }
        return result
    }
    private fun await(condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 15000
        while (!condition() && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(100)
        assertTrue("Timed out awaiting overlay", condition())
    }
    private fun main(block: () -> Unit) = instrumentation.runOnMainSync(block)
    private fun shell(command: String) = android.os.ParcelFileDescriptor.AutoCloseInputStream(
        automation.executeShellCommand(command)).bufferedReader().use { it.readText() }
}
