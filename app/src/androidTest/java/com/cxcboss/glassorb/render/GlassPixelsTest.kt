package com.cxcboss.glassorb.render

import android.opengl.EGL14
import android.opengl.GLES30
import androidx.test.platform.app.InstrumentationRegistry
import com.cxcboss.glassorb.model.OrbConfig
import com.cxcboss.glassorb.overlay.OverlayState
import java.nio.ByteBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Real GLES readback catches opacity regressions that JVM/config tests cannot see. */
class GlassPixelsTest {
    @Test fun blackTransitionEndpointsMatchIdleFrame() = withRenderer { renderer ->
        val config = OrbConfig()
        val idle = render(renderer, RenderSnapshot(config, OverlayState.Collapsed, 0f))
        for (state in listOf(OverlayState.Expanding, OverlayState.Collapsing)) {
            val endpoint = render(renderer, RenderSnapshot(config, state, 0f))
            for (i in 0 until idle.capacity()) {
                assertEquals("same endpoint pixel $i in $state", idle.get(i), endpoint.get(i))
            }
        }
    }

    @Test fun collapsedCapsuleIsPureBlack() = withRenderer { renderer ->
        val pixels = render(renderer, RenderSnapshot(OrbConfig(), OverlayState.Collapsed, 0f))
        for (x in 60..130 step 10) {
            val p = pixel(pixels, x, 18)
            assertEquals("capsule red", 0, p[0])
            assertEquals("capsule green", 0, p[1])
            assertEquals("capsule blue", 0, p[2])
            assertEquals("capsule opacity", 255, p[3])
        }
        assertEquals("outside capsule", 0, pixel(pixels, 5, 160)[3])
    }

    @Test fun glassHasDarkTopAndTransparentLowerHemisphere() = withRenderer { renderer ->
        val config = OrbConfig().let { it.copy(motion = it.motion.copy(breathingAmplitude = 0f)) }
        val pixels = render(renderer, RenderSnapshot(config, OverlayState.Wave, 1f, timeSeconds = 0f))
        val top = pixel(pixels, 100, 20)
        val bottom = pixel(pixels, 100, 110)
        assertTrue("top alpha ${top[3]}", top[3] > 200)
        assertTrue("lower glass must transmit background; alpha ${bottom[3]}", bottom[3] < 170)
        assertTrue("top is darker than bottom", top[3] > bottom[3] + 50)
        assertEquals("no rectangular top-left projection corner", 0, pixel(pixels, 5, 5)[3])
        assertEquals("no rectangular top-right projection corner", 0, pixel(pixels, 195, 5)[3])
        assertEquals("no rectangular bottom-right projection corner", 0, pixel(pixels, 195, 195)[3])
        // The Android compositor expects premultiplied RGBA on a translucent TextureView.
        for (i in 0 until pixels.capacity() step 4) {
            val alpha = pixels.get(i + 3).toInt() and 255
            for (c in 0..2) assertTrue("premultiplied alpha", (pixels.get(i + c).toInt() and 255) <= alpha + 1)
        }
    }

    private fun render(renderer: GlOrbPipeline, snapshot: RenderSnapshot): ByteBuffer {
        renderer.render(snapshot, SIZE, SIZE)
        val pixels = ByteBuffer.allocateDirect(SIZE * SIZE * 4)
        GLES30.glReadPixels(0, 0, SIZE, SIZE, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, pixels)
        assertEquals(GLES30.GL_NO_ERROR, GLES30.glGetError())
        return pixels
    }

    private fun pixel(pixels: ByteBuffer, x: Int, yFromTop: Int): List<Int> {
        val offset = ((SIZE - 1 - yFromTop) * SIZE + x) * 4
        return (0..3).map { pixels.get(offset + it).toInt() and 255 }
    }

    private fun withRenderer(block: (GlOrbPipeline) -> Unit) {
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        assertTrue(EGL14.eglInitialize(display, IntArray(1), 0, IntArray(1), 0))
        val attributes = intArrayOf(EGL14.EGL_RENDERABLE_TYPE, 0x40, EGL14.EGL_SURFACE_TYPE,
            EGL14.EGL_PBUFFER_BIT, EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8, EGL14.EGL_NONE)
        val configs = arrayOfNulls<android.opengl.EGLConfig>(1)
        assertTrue(EGL14.eglChooseConfig(display, attributes, 0, configs, 0, 1, IntArray(1), 0))
        val context = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE), 0)
        val surface = EGL14.eglCreatePbufferSurface(display, configs[0],
            intArrayOf(EGL14.EGL_WIDTH, SIZE, EGL14.EGL_HEIGHT, SIZE, EGL14.EGL_NONE), 0)
        assertTrue(EGL14.eglMakeCurrent(display, surface, surface, context))
        var renderer: GlOrbPipeline? = null
        try {
            renderer = GlOrbPipeline(InstrumentationRegistry.getInstrumentation().targetContext, 1f)
            block(renderer)
        } finally {
            renderer?.release()
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(display, surface)
            EGL14.eglDestroyContext(display, context)
            EGL14.eglTerminate(display)
        }
    }

    private companion object { const val SIZE = 200 }
}
