package com.cxcboss.glassorb.overlay

/** Narrow seam around the platform Window/Dialog implementation for JVM policy tests. */
internal interface BackgroundBlurWindow {
    fun show(windowBounds: IntRect, apertureBounds: IntRect, blurRadiusPx: Int): Boolean
    fun update(windowBounds: IntRect, apertureBounds: IntRect, blurRadiusPx: Int): Boolean
    fun hide()
    fun destroy()
}

internal class BackgroundBlurController(
    private val window: BackgroundBlurWindow,
) {
    private var attached = false
    private var destroyed = false
    private var windowBounds: IntRect? = null
    private var apertureBounds: IntRect? = null
    private var appliedRadius = 0

    /** Attach only for an already eligible request. */
    fun show(request: BackgroundBlurRequest): Boolean {
        if (destroyed || !isEligible(request)) return false
        val initialWindowBounds = request.windowBounds ?: return false
        val initialApertureBounds = request.apertureBounds ?: return false
        if (attached) {
            if (windowBounds == initialWindowBounds && apertureBounds == initialApertureBounds &&
                appliedRadius == request.blurRadiusPx
            ) return true
            updateWindow(initialWindowBounds, initialApertureBounds, request.blurRadiusPx)
            return attached
        }
        val shown = runCatching {
            window.show(initialWindowBounds, initialApertureBounds, request.blurRadiusPx)
        }.getOrDefault(false)
        if (!shown) return false
        attached = true
        windowBounds = initialWindowBounds
        apertureBounds = initialApertureBounds
        appliedRadius = request.blurRadiusPx
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
        val nextWindowBounds = request.windowBounds ?: return
        val nextApertureBounds = request.apertureBounds ?: return
        val nextRadius = request.blurRadiusPx
        if (nextWindowBounds == windowBounds && nextApertureBounds == apertureBounds &&
            nextRadius == appliedRadius
        ) return
        updateWindow(nextWindowBounds, nextApertureBounds, nextRadius)
    }

    fun hide() {
        if (destroyed) return
        detach()
    }

    private fun detach() {
        if (!attached) return
        runCatching { window.hide() }
        attached = false
        windowBounds = null
        apertureBounds = null
        appliedRadius = 0
    }

    fun destroy() {
        if (destroyed) return
        detach()
        runCatching { window.destroy() }
        destroyed = true
    }

    private fun updateWindow(nextWindowBounds: IntRect, nextApertureBounds: IntRect, nextRadius: Int) {
        val updated = runCatching {
            window.update(nextWindowBounds, nextApertureBounds, nextRadius)
        }.getOrDefault(false)
        if (updated) {
            windowBounds = nextWindowBounds
            apertureBounds = nextApertureBounds
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
            windowBounds = request.windowBounds,
            apertureBounds = request.apertureBounds,
            blurRadiusPx = request.blurRadiusPx,
            shape = request.shape,
        )
}
