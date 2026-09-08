package com.cxcboss.glassorb.overlay

import android.view.MotionEvent
import android.view.View

/**
 * Process-local bridge between the foreground overlay and the explicitly
 * enabled accessibility service. The service is used only for the trusted
 * status-bar touch window; it does not inspect accessibility content.
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
        service?.let { connected ->
            runCatching { connected.updateTouchBounds(provider()) }
                .onFailure { /* The service must remain alive if a window is rebuilding. */ }
        }
        runCatching { onConnectionChanged() }
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
        runCatching { accessibilityService.updateTouchBounds(boundsProvider?.invoke()) }
        runCatching { connectionChanged?.invoke() }
    }

    @Synchronized
    fun disconnect(accessibilityService: GlassOrbAccessibilityService) {
        if (service === accessibilityService) {
            service = null
            runCatching { connectionChanged?.invoke() }
        }
    }

    @Synchronized
    fun isConnected(): Boolean = service != null

    @Synchronized
    fun windowContext(): GlassOrbAccessibilityService? = service

    @Synchronized
    fun updateTouchBounds(bounds: IntRect?) {
        runCatching { service?.updateTouchBounds(bounds) }
    }

    @Synchronized
    fun dispatchTouch(view: View, event: MotionEvent): Boolean =
        touchHandler?.invoke(view, event) ?: false
}
