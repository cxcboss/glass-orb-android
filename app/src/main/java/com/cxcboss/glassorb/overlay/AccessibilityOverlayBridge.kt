package com.cxcboss.glassorb.overlay

import android.view.MotionEvent
import android.view.View

/**
 * Small in-process bridge between the foreground overlay service and the
 * optional AccessibilityService. The latter owns the higher-priority touch
 * proxy; the renderer and gesture state remain owned by OverlayWindowController.
 */
internal object AccessibilityOverlayBridge {
    private var service: GlassOrbAccessibilityService? = null
    private var touchHandler: ((View, MotionEvent) -> Boolean)? = null
    private var boundsProvider: (() -> IntRect?)? = null
    private var connectionChanged: (() -> Unit)? = null

    @Synchronized
    fun attachController(
        handler: (View, MotionEvent) -> Boolean,
        provider: () -> IntRect?,
        onConnectionChanged: () -> Unit,
    ) {
        touchHandler = handler
        boundsProvider = provider
        connectionChanged = onConnectionChanged
        service?.updateTouchBounds(provider())
        onConnectionChanged()
    }

    @Synchronized
    fun detachController() {
        service?.updateTouchBounds(null)
        touchHandler = null
        boundsProvider = null
        connectionChanged = null
    }

    @Synchronized
    fun connect(accessibilityService: GlassOrbAccessibilityService) {
        service = accessibilityService
        accessibilityService.updateTouchBounds(boundsProvider?.invoke())
        connectionChanged?.invoke()
    }

    @Synchronized
    fun disconnect(accessibilityService: GlassOrbAccessibilityService) {
        if (service === accessibilityService) {
            service = null
            connectionChanged?.invoke()
        }
    }

    @Synchronized
    fun isConnected(): Boolean = service != null

    @Synchronized
    fun updateTouchBounds(bounds: IntRect?) {
        service?.updateTouchBounds(bounds)
    }

    @Synchronized
    fun dispatchTouch(view: View, event: MotionEvent): Boolean =
        touchHandler?.invoke(view, event) ?: false
}
