package com.cxcboss.glassorb.overlay

import android.view.WindowManager
import com.cxcboss.glassorb.model.GeometryConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundBlurPolicyTest {
    private val square = IntRect(100, 200, 460, 560)

    @Test
    fun `blur is enabled only when user capability api visibility and circular mask allow it`() {
        assertTrue(BackgroundBlurPolicy.isEligible(true, 31, true, true, square, square, 33, BlurShape.Circular))
        assertFalse(BackgroundBlurPolicy.isEligible(false, 31, true, true, square, square, 33, BlurShape.Circular))
        assertFalse(BackgroundBlurPolicy.isEligible(true, 30, true, true, square, square, 33, BlurShape.Circular))
        assertFalse(BackgroundBlurPolicy.isEligible(true, 31, false, true, square, square, 33, BlurShape.Circular))
        assertFalse(BackgroundBlurPolicy.isEligible(true, 31, true, false, square, square, 33, BlurShape.Circular))
        assertTrue(BackgroundBlurPolicy.isEligible(true, 31, true, true, square, square, 33, BlurShape.Ellipse))
        assertFalse(BackgroundBlurPolicy.isEligible(true, 31, true, true, square, square, 33, BlurShape.Uncertain))
        assertFalse(BackgroundBlurPolicy.isEligible(true, 31, true, true, null, square, 33, BlurShape.Circular))
        assertFalse(BackgroundBlurPolicy.isEligible(true, 31, true, true, square, square, 0, BlurShape.Circular))
    }

    @Test
    fun `independent orb axes select ellipse and force local blur fallback`() {
        assertEquals(BlurShape.Circular, BackgroundBlurPolicy.shapeFor(GeometryConfig(orbWidthDp = 128f, orbHeightDp = 128f)))
        assertEquals(BlurShape.Ellipse, BackgroundBlurPolicy.shapeFor(GeometryConfig(orbWidthDp = 160f, orbHeightDp = 96f)))
    }

    @Test
    fun `blur window policy is non touchable and never uses blur behind`() {
        val flags = BackgroundBlurPolicy.windowFlags()

        assertTrue(flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE != 0)
        assertTrue(flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE != 0)
        assertTrue(flags and WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN != 0)
        assertEquals(
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            flags,
        )
        assertEquals(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            BackgroundBlurPolicy.windowType(accessibilityWindow = false, apiLevel = 31))
        assertEquals(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            BackgroundBlurPolicy.windowType(accessibilityWindow = true, apiLevel = 31))
    }

    @Test
    fun `controller gates initial attach and destroys optional window when request becomes ineligible`() {
        val host = RecordingBlurWindow()
        val controller = BackgroundBlurController(host)
        val request = BackgroundBlurRequest(
            userEnabled = true,
            apiLevel = 31,
            crossWindowBlurEnabled = true,
            visible = true,
            windowBounds = square,
            apertureBounds = square,
            blurRadiusPx = 32,
            shape = BlurShape.Circular,
        )

        assertFalse(controller.show(request.copy(visible = false)))
        assertTrue(host.events.isEmpty())
        controller.update(request)
        controller.update(request)
        controller.update(request.copy(shape = BlurShape.Uncertain))
        controller.update(request.copy(shape = BlurShape.Uncertain))
        controller.update(request.copy(visible = false))
        controller.update(request)
        controller.hide()
        controller.hide()
        controller.destroy()
        controller.destroy()

        assertEquals(
            listOf(
                "show:32:${square.width}x${square.height}:${square.width}x${square.height}",
                "hide",
                "show:32:${square.width}x${square.height}:${square.width}x${square.height}",
                "hide", "destroy",
            ),
            host.events,
        )
    }

    @Test
    fun `ellipse aperture and blur radius update without recreating the window`() {
        val host = RecordingBlurWindow()
        val controller = BackgroundBlurController(host)
        val request = BackgroundBlurRequest(
            userEnabled = true,
            apiLevel = 31,
            crossWindowBlurEnabled = true,
            visible = true,
            windowBounds = IntRect(80, 180, 480, 580),
            apertureBounds = square,
            blurRadiusPx = 32,
            shape = BlurShape.Ellipse,
        )
        controller.update(request)
        controller.update(request.copy(apertureBounds = IntRect(132, 220, 428, 520), blurRadiusPx = 96))
        assertEquals(1, host.events.count { it.startsWith("show:") })
        assertTrue(host.events.any { it == "update:96:296x300" })
        controller.destroy()
        assertEquals(1, host.events.count { it == "destroy" })
    }

    @Test
    private class RecordingBlurWindow : BackgroundBlurWindow {
        val events = mutableListOf<String>()

        override fun show(windowBounds: IntRect, apertureBounds: IntRect, blurRadiusPx: Int): Boolean {
            events += "show:$blurRadiusPx:${windowBounds.width}x${windowBounds.height}:${apertureBounds.width}x${apertureBounds.height}"
            return true
        }

        override fun update(windowBounds: IntRect, apertureBounds: IntRect, blurRadiusPx: Int): Boolean {
            events += "update:$blurRadiusPx:${apertureBounds.width}x${apertureBounds.height}"
            return true
        }

        override fun hide() {
            events += "hide"
        }

        override fun destroy() {
            events += "destroy"
        }
    }
}
