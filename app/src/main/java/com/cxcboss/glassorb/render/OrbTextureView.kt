package com.cxcboss.glassorb.render

import android.content.Context
import android.graphics.SurfaceTexture
import android.util.AttributeSet
import android.util.Log
import android.view.TextureView

class OrbTextureView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : TextureView(context, attrs), TextureView.SurfaceTextureListener, OrbRenderer {
    private var renderLoop: GlRenderLoop? = null
    private var pendingSnapshot: RenderSnapshot? = null
    private var paused = false
    private var loggedFirstSubmit = false
    @Volatile private var swappedToken = 0L
    var presentedToken: Long = 0L
        private set

    internal val submittedState get() = pendingSnapshot?.state

    var onRenderFailure: ((Throwable) -> Unit)? = null

    init {
        isOpaque = false
        surfaceTextureListener = this
    }

    override fun submit(snapshot: RenderSnapshot) {
        if (!loggedFirstSubmit) {
            loggedFirstSubmit = true
            Log.d(TAG, "First immutable snapshot submitted: ${width}x$height")
        }
        pendingSnapshot = snapshot
        renderLoop?.submit(snapshot)
    }

    override fun setPaused(paused: Boolean) {
        this.paused = paused
        renderLoop?.setPaused(paused)
    }

    override fun release() {
        renderLoop?.releaseBlocking()
        renderLoop = null
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        Log.d(TAG, "Texture surface available: ${width}x$height")
        release()
        renderLoop = GlRenderLoop(
            context = context.applicationContext,
            surfaceTexture = surface,
            initialWidth = width,
            initialHeight = height,
            density = resources.displayMetrics.density,
            onFailure = { error ->
                Log.e(TAG, "GLES render loop failed", error)
                post { onRenderFailure?.invoke(error) }
            },
            onPresented = { token -> swappedToken = token },
        ).also { loop ->
            loop.setPaused(paused)
            pendingSnapshot?.let(loop::submit)
        }
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        renderLoop?.resize(width, height)
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        release()
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
        presentedToken = swappedToken
    }

    override fun onDetachedFromWindow() {
        release()
        super.onDetachedFromWindow()
    }

    private companion object {
        const val TAG = "GlassOrbRenderer"
    }
}
