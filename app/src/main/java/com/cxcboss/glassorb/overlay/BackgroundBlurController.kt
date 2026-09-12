package com.cxcboss.glassorb.overlay

/** Narrow seam around the platform Window/Dialog implementation for JVM policy tests. */
internal interface BackgroundBlurWindow {
    fun show(bounds: IntRect, blurRadiusPx: Int): Boolean
    fun update(bounds: IntRect, blurRadiusPx: Int): Boolean
    fun hide()
    fun destroy()
}

internal class BackgroundBlurController(
    private val window: BackgroundBlurWindow,
    private val blurRadiusPx: Int,
) {
    private var attached = false
    private var destroyed = false
    private var bounds: IntRect? = null
    private var appliedRadius = 0

    /** Attach only for an already eligible request. */
    fun show(request: BackgroundBlurRequest): Boolean {
        if (destroyed || !isEligible(request)) return false
        val initialBounds = request.bounds ?: return false
        if (attached) {
            if (bounds == initialBounds && appliedRadius == blurRadiusPx) return true
            updateWindow(initialBounds, blurRadiusPx)
            return attached
        }
        val shown = runCatching { window.show(initialBounds, blurRadiusPx) }.getOrDefault(false)
        if (!shown) return false
        attached = true
        bounds = initialBounds
        appliedRadius = blurRadiusPx
        return true
    }

    fun update(request: BackgroundBlurRequest) {
        if (destroyed) return
        if (!isEligible(request)) {
            detach()
            return
        }
        if (!attached) {
            show(request)
            return
        }
        val nextBounds = request.bounds ?: return
        val nextRadius = blurRadiusPx
        if (nextBounds == bounds && nextRadius == appliedRadius) return
        updateWindow(nextBounds, nextRadius)
    }

    fun hide() {
        if (destroyed) return
        detach()
    }

    private fun detach() {
        if (!attached) return
        runCatching { window.hide() }
        runCatching { window.destroy() }
        attached = false
        bounds = null
        appliedRadius = 0
    }

    fun destroy() {
        if (destroyed) return
        detach()
        destroyed = true
    }

    private fun updateWindow(nextBounds: IntRect, nextRadius: Int) {
        val updated = runCatching { window.update(nextBounds, nextRadius) }.getOrDefault(false)
        if (updated) {
            bounds = nextBounds
            appliedRadius = nextRadius
            return
        }
        // A detached/stale Dialog must never take down the existing orb. Drop
        // this optional layer and leave the normal GL/dim/touch stack intact.
        detach()
    }

    private fun isEligible(request: BackgroundBlurRequest): Boolean =
        BackgroundBlurPolicy.isEligible(
            userEnabled = request.userEnabled,
            apiLevel = request.apiLevel,
            crossWindowBlurEnabled = request.crossWindowBlurEnabled,
            visible = request.visible,
            bounds = request.bounds,
            shape = request.shape,
        )
}
