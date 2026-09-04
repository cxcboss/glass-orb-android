package com.cxcboss.glassorb.render

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

internal class GlRenderLoop(
    private val context: Context,
    private val surfaceTexture: SurfaceTexture,
    initialWidth: Int,
    initialHeight: Int,
    private val density: Float,
    private val onFailure: (Throwable) -> Unit,
) {
    private val thread = HandlerThread("GlassOrb-GL").apply { start() }
    private val handler = Handler(thread.looper)
    private val latestSnapshot = AtomicReference<RenderSnapshot?>(null)

    private var width = initialWidth.coerceAtLeast(1)
    private var height = initialHeight.coerceAtLeast(1)
    private var paused = false
    private var released = false
    private var frameScheduled = false
    private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var pipeline: GlOrbPipeline? = null
    private var loggedFirstFrame = false

    private val frame = object : Runnable {
        override fun run() {
            frameScheduled = false
            if (released || paused) return
            val snapshot = latestSnapshot.get() ?: return
            val frameStarted = SystemClock.uptimeMillis()
            try {
                pipeline?.render(snapshot, width, height)
                if (!loggedFirstFrame) {
                    loggedFirstFrame = true
                    Log.d(TAG, "First GLES frame rendered and swapped: ${width}x$height")
                }
                if (!EGL14.eglSwapBuffers(display, eglSurface)) {
                    val error = EGL14.eglGetError()
                    if (error == EGL14.EGL_CONTEXT_LOST) {
                        recreateContext()
                    } else {
                        error("eglSwapBuffers failed: 0x${error.toString(16)}")
                    }
                }
                val fps = if (snapshot.state is com.cxcboss.glassorb.overlay.OverlayState.Collapsed) {
                    snapshot.config.performance.collapsedFps
                } else {
                    snapshot.config.performance.expandedFps
                }.coerceAtLeast(1)
                // Rendering/swap already consumes part of the frame budget. Waiting a full
                // interval after swap would turn a 60 Hz target into roughly 30 Hz.
                scheduleFrame((1_000L / fps - (SystemClock.uptimeMillis() - frameStarted)).coerceAtLeast(0L))
            } catch (error: Throwable) {
                released = true
                teardownEgl()
                onFailure(error)
            }
        }
    }

    init {
        handler.post {
            try {
                initializeEgl()
                scheduleFrame(0)
            } catch (error: Throwable) {
                released = true
                teardownEgl()
                onFailure(error)
            }
        }
    }

    fun submit(snapshot: RenderSnapshot) {
        latestSnapshot.set(snapshot)
        handler.post { scheduleFrame(0) }
    }

    fun resize(width: Int, height: Int) {
        handler.post {
            this.width = width.coerceAtLeast(1)
            this.height = height.coerceAtLeast(1)
            pipeline?.invalidateTargets()
            scheduleFrame(0)
        }
    }

    fun setPaused(paused: Boolean) {
        handler.post {
            this.paused = paused
            if (!paused) scheduleFrame(0)
        }
    }

    fun releaseBlocking() {
        if (released && !thread.isAlive) return
        val latch = CountDownLatch(1)
        handler.post {
            released = true
            handler.removeCallbacks(frame)
            teardownEgl()
            latch.countDown()
            thread.quitSafely()
        }
        latch.await(1, TimeUnit.SECONDS)
    }

    private fun scheduleFrame(delayMillis: Long) {
        if (released || paused || frameScheduled || pipeline == null || latestSnapshot.get() == null) return
        frameScheduled = true
        handler.postDelayed(frame, delayMillis)
    }

    private fun initializeEgl() {
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        check(display != EGL14.EGL_NO_DISPLAY) { "OpenGL ES display unavailable" }
        val version = IntArray(2)
        check(EGL14.eglInitialize(display, version, 0, version, 1)) { "eglInitialize failed" }

        val attributes = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGLExt.EGL_OPENGL_ES3_BIT_KHR,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
            EGL14.EGL_NONE,
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val count = IntArray(1)
        check(EGL14.eglChooseConfig(display, attributes, 0, configs, 0, 1, count, 0) && count[0] > 0) {
            "RGBA8 OpenGL ES 3 configuration unavailable"
        }
        val config = requireNotNull(configs[0])
        eglContext = EGL14.eglCreateContext(
            display,
            config,
            EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE),
            0,
        )
        check(eglContext != EGL14.EGL_NO_CONTEXT) { "OpenGL ES 3 context creation failed" }
        eglSurface = EGL14.eglCreateWindowSurface(
            display,
            config,
            surfaceTexture,
            intArrayOf(EGL14.EGL_NONE),
            0,
        )
        check(eglSurface != EGL14.EGL_NO_SURFACE) { "Transparent EGL window surface creation failed" }
        check(EGL14.eglMakeCurrent(display, eglSurface, eglSurface, eglContext)) { "eglMakeCurrent failed" }
        pipeline = GlOrbPipeline(context, density)
        Log.d(TAG, "EGL initialized: version ${version[0]}.${version[1]}")
    }

    private fun recreateContext() {
        teardownEgl()
        if (!released) initializeEgl()
    }

    private fun teardownEgl() {
        pipeline?.release()
        pipeline = null
        if (display != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(
                display,
                EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_CONTEXT,
            )
            if (eglSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, eglSurface)
            if (eglContext != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, eglContext)
            EGL14.eglTerminate(display)
        }
        display = EGL14.EGL_NO_DISPLAY
        eglSurface = EGL14.EGL_NO_SURFACE
        eglContext = EGL14.EGL_NO_CONTEXT
    }

    private companion object {
        const val TAG = "GlassOrbRenderer"
    }
}
