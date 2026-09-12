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

    /** Attach the transparent window before the GL host is added. */
    fun show(initialBounds: IntRect): Boolean {
        if (destroyed) return false
        if (attached) {
            updateWindow(initialBounds, appliedRadius)
            return true
        }
        val shown = runCatching { window.show(initialBounds, 0) }.getOrDefault(false)
        if (!shown) return false
        attached = true
        bounds = initialBounds
        appliedRadius = 0
        return true
    }

    fun update(request: BackgroundBlurRequest) {
        if (destroyed || !attached) return
        val nextBounds = request.bounds ?: bounds ?: return
        val nextRadius = if (BackgroundBlurPolicy.isEligible(
                userEnabled = request.userEnabled,
                apiLevel = request.apiLevel,
                crossWindowBlurEnabled = request.crossWindowBlurEnabled,
                visible = request.visible,
                bounds = nextBounds,
                shape = request.shape,
            )
        ) blurRadiusPx else 0
        if (nextBounds == bounds && nextRadius == appliedRadius) return
        updateWindow(nextBounds, nextRadius)
    }

    fun hide() {
        if (!attached || destroyed) return
        runCatching { window.hide() }
        attached = false
        bounds = null
        appliedRadius = 0
    }

    fun destroy() {
        if (destroyed) return
        hide()
        runCatching { window.destroy() }
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
        runCatching { window.hide() }
        attached = false
        bounds = null
        appliedRadius = 0
    }
}
