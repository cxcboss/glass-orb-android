package com.cxcboss.glassorb.overlay

import android.Manifest
import android.app.NotificationManager
import android.os.Build
import android.os.SystemClock
import android.view.MotionEvent
import android.view.inspector.WindowInspector
import androidx.test.core.app.ActivityScenario
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.cxcboss.glassorb.data.OrbConfigRepository
import com.cxcboss.glassorb.model.OrbConfig
import com.cxcboss.glassorb.render.OrbTextureView
import com.cxcboss.glassorb.ui.MainActivity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@SdkSuppress(minSdkVersion = 29)
class OverlayIntegrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test fun foregroundOverlayGesturesMultitouchAndNotificationActions() {
        shell("appops set ${context.packageName} android:system_alert_window allow")
        if (Build.VERSION.SDK_INT >= 33) {
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        runBlocking { OrbConfigRepository.getInstance(context).replace(OrbConfig()) }
        ActivityScenario.launch(MainActivity::class.java).use { activity ->
            try {
                activity.onActivity { OrbOverlayService.start(it) }
                await { overlay()?.submittedState == OverlayState.Collapsed }
                val view = requireNotNull(overlay())
                val collapsedWidth = view.width
                tap(view)
                await { view.submittedState == OverlayState.Wave }
                assertTrue(view.width > collapsedWidth)

                // Regression: second finger cancels the sequence; remaining moves must not
                // re-enter SwipeTracking and strand subsequent taps.
                main {
                    val now = SystemClock.uptimeMillis()
                    val props = Array(2) { index -> MotionEvent.PointerProperties().apply {
                        id = index; toolType = MotionEvent.TOOL_TYPE_FINGER
                    } }
                    val coords = Array(2) { index -> MotionEvent.PointerCoords().apply {
                        x = view.width * 0.5f + index * 12f; y = 60f; pressure = 1f; size = 1f
                    } }
                    event(view, now, now, MotionEvent.ACTION_DOWN, 60f)
                    val multi = MotionEvent.obtain(now, now + 8, MotionEvent.ACTION_POINTER_DOWN or (1 shl 8),
                        2, props, coords, 0, 0, 1f, 1f, 0, 0, 0, 0)
                    view.dispatchTouchEvent(multi); multi.recycle()
                    event(view, now, now + 24, MotionEvent.ACTION_MOVE, -160f)
                    event(view, now, now + 32, MotionEvent.ACTION_UP, -160f)
                }
                SystemClock.sleep(100)
                assertEquals(OverlayState.Wave, view.submittedState)
                tap(view)
                await { view.submittedState == OverlayState.Thinking }
                await { view.submittedState == OverlayState.Wave }

                main {
                    val now = SystemClock.uptimeMillis()
                    event(view, now, now, MotionEvent.ACTION_DOWN, 180f)
                    event(view, now, now + 40, MotionEvent.ACTION_MOVE, -100f)
                    event(view, now, now + 80, MotionEvent.ACTION_UP, -100f)
                }
                await { view.submittedState == OverlayState.Collapsed }
                assertEquals(collapsedWidth, view.width)

                action("隐藏")
                await { overlay() == null && OverlayRuntime.status.value == OverlayRuntimeStatus.Hidden }
                action("显示")
                await { overlay()?.submittedState == OverlayState.Collapsed }
                action("停止")
                await { overlay() == null && OverlayRuntime.status.value == OverlayRuntimeStatus.Stopped }
            } finally {
                activity.onActivity { OrbOverlayService.stop(it) }
            }
        }
    }

    private fun action(title: String) {
        val notification = context.getSystemService(NotificationManager::class.java).activeNotifications.single { it.id == 27 }
        requireNotNull(notification.notification.actions.firstOrNull { it.title.toString() == title }).actionIntent.send()
    }

    private fun overlay(): OrbTextureView? {
        var result: OrbTextureView? = null
        main { result = WindowInspector.getGlobalWindowViews().filterIsInstance<OrbTextureView>().singleOrNull() }
        return result
    }

    private fun tap(view: OrbTextureView) = main {
        val now = SystemClock.uptimeMillis()
        event(view, now, now, MotionEvent.ACTION_DOWN, 35f)
        event(view, now, now + 30, MotionEvent.ACTION_UP, 35f)
    }

    private fun event(view: OrbTextureView, down: Long, time: Long, action: Int, y: Float) {
        val event = MotionEvent.obtain(down, time, action, view.width * 0.5f, y, 0)
        view.dispatchTouchEvent(event)
        event.recycle()
    }

    private fun await(condition: () -> Boolean) {
        val until = SystemClock.uptimeMillis() + 15_000
        while (!condition() && SystemClock.uptimeMillis() < until) SystemClock.sleep(50)
        assertTrue("Timed out waiting for overlay state", condition())
    }

    private fun main(block: () -> Unit) = instrumentation.runOnMainSync(block)
    private fun shell(command: String) = android.os.ParcelFileDescriptor.AutoCloseInputStream(
        instrumentation.uiAutomation.executeShellCommand(command)).bufferedReader().use { it.readText() }
}
