package com.cxcboss.glassorb.overlay

import android.view.MotionEvent
import android.view.View

internal data class SystemAssistantWindowEvent(
    val packageName: String,
    val visible: Boolean,
    val eventType: Int,
    val windowChanges: Int,
)

/**
 * Process-local bridge between the foreground overlay and the explicitly
 * enabled accessibility service. The service is used only for the trusted
 * status-bar touch window and a small package-filtered assistant lifecycle
 * signal; it does not inspect accessibility content.
 */
internal object AccessibilityOverlayBridge {
    private var service: GlassOrbAccessibilityService? = null
    private var touchHandler: ((View, MotionEvent) -> Boolean)? = null
    private var boundsProvider: (() -> IntRect?)? = null
    private var connectionChanged: (() -> Unit)? = null
    private var assistantWindowChanged: ((SystemAssistantWindowEvent) -> Unit)? = null

    @Synchronized
    fun attachController(
        handler: (View, MotionEvent) -> Boolean,
        provider: () -> IntRect?,
        onConnectionChanged: () -> Unit,
        onAssistantWindowChanged: (SystemAssistantWindowEvent) -> Unit,
    ) {
        touchHandler = handler
        boundsProvider = provider
        connectionChanged = onConnectionChanged
        assistantWindowChanged = onAssistantWindowChanged
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
        assistantWindowChanged = null
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

    @Synchronized
    fun notifyAssistantWindowChanged(event: SystemAssistantWindowEvent) {
        runCatching { assistantWindowChanged?.invoke(event) }
    }
}
