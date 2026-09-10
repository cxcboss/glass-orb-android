package com.cxcboss.glassorb.overlay

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import com.cxcboss.glassorb.BuildConfig

/**
 * Minimal trusted-window host for the optional status-bar touch target.
 * The service opts into interactive-window metadata because Android does not
 * deliver TYPE_WINDOWS_CHANGED without it. Only root package names are used
 * to identify an allowlisted system assistant; no text tree, screenshots,
 * gestures, key events, or touch exploration are requested.
 */
class GlassOrbAccessibilityService : AccessibilityService() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var windowManager: WindowManager? = null
    private var touchView: View? = null
    private var touchParams: WindowManager.LayoutParams? = null
    private var reportedAssistantPackage: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        val manager = getSystemService(WindowManager::class.java)
        if (manager == null) {
            Log.e(TAG, "WindowManager unavailable; trusted touch proxy is inactive")
            return
        }
        windowManager = manager
        // Keep the service info in XML. Some OEM accessibility managers treat
        // a partial runtime replacement as an invalid service and disable it
        // on the next interaction.
        runCatching { AccessibilityOverlayBridge.connect(this) }
            .onFailure { Log.e(TAG, "Unable to connect trusted touch proxy", it) }
        // If the service was rebound while the system assistant was already
        // visible, there may be no new window event to trigger the overlay.
        reportCurrentAssistantWindow()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val accessibilityEvent = event ?: return
        when (accessibilityEvent.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                // This event carries the source package for app-controlled
                // panes. It is the low-cost path and does not query windows.
                val packageName = accessibilityEvent.packageName?.toString()
                if (packageName != null && SystemAssistantPackages.isSupported(packageName)) {
                    reportAssistantWindow(
                        packageName = packageName,
                        visible = true,
                        event = accessibilityEvent,
                    )
                }
            }

            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> reportWindowSetChanged(accessibilityEvent)
        }
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent): Boolean {
        AccessibilityOverlayBridge.disconnect(this)
        removeTouchView()
        return super.onUnbind(intent)
    }

    internal fun updateTouchBounds(bounds: IntRect?) {
        mainHandler.post {
            runCatching { updateTouchBoundsNow(bounds) }
                .onFailure { Log.e(TAG, "Unable to update trusted touch proxy", it) }
        }
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        AccessibilityOverlayBridge.disconnect(this)
        removeTouchView()
        windowManager = null
        reportedAssistantPackage = null
        super.onDestroy()
    }

    private fun reportWindowSetChanged(event: AccessibilityEvent) {
        val visiblePackages = visibleAssistantPackages()
        val eventPackage = event.packageName?.toString()
            ?.takeIf(SystemAssistantPackages::isSupported)
        val removed = event.windowChanges and AccessibilityEvent.WINDOWS_CHANGE_REMOVED != 0

        // Some ROMs expose the package on the event but temporarily return an
        // empty window list while adding/removing a full-screen overlay.
        if (eventPackage != null && removed && visiblePackages.isEmpty()) {
            reportAssistantWindow(eventPackage, visible = false, event = event)
            return
        }
        if (visiblePackages.isNotEmpty()) {
            val packageName = eventPackage ?: visiblePackages.first()
            reportAssistantWindow(packageName, visible = true, event = event)
            return
        }

        if (eventPackage != null && !removed) {
            reportAssistantWindow(eventPackage, visible = true, event = event)
            return
        }

        // A removed window is the only reliable disappearance signal. Do not
        // turn unrelated window changes into a false event after the assistant
        // has already been cleared.
        if (reportedAssistantPackage != null && removed) {
            reportAssistantWindow(reportedAssistantPackage!!, visible = false, event = event)
        }
    }

    private fun reportCurrentAssistantWindow() {
        val packageName = visibleAssistantPackages().firstOrNull() ?: return
        AccessibilityOverlayBridge.notifyAssistantWindowChanged(
            SystemAssistantWindowEvent(
                packageName = packageName,
                visible = true,
                eventType = AccessibilityEvent.TYPE_WINDOWS_CHANGED,
                windowChanges = 0,
            ),
        )
        reportedAssistantPackage = packageName
    }

    private fun reportAssistantWindow(
        packageName: String,
        visible: Boolean,
        event: AccessibilityEvent,
    ) {
        AccessibilityOverlayBridge.notifyAssistantWindowChanged(
            SystemAssistantWindowEvent(
                packageName = packageName,
                visible = visible,
                eventType = event.eventType,
                windowChanges = event.windowChanges,
            ),
        )
        reportedAssistantPackage = if (visible) packageName else null
    }

    /**
     * TYPE_WINDOWS_CHANGED may have no useful event package on OEM builds.
     * The official window list lets us resolve the source window by its root
     * package without reading any node text or traversing the hierarchy.
     */
    private fun visibleAssistantPackages(): Set<String> = runCatching {
        getWindows()
            .asSequence()
            .mapNotNull { window ->
                runCatching { window.root?.packageName?.toString() }.getOrNull()
            }
            .filter(SystemAssistantPackages::isSupported)
            .toSet()
    }.onFailure {
        if (BuildConfig.DEBUG) Log.d(TAG, "Unable to query interactive windows", it)
    }.getOrDefault(emptySet())

    private fun updateTouchBoundsNow(bounds: IntRect?) {
        val manager = windowManager ?: return
        if (bounds == null || bounds.width <= 0 || bounds.height <= 0) {
            removeTouchView()
            return
        }
        val existing = touchView
        val params = touchParams
        if (existing == null || params == null) {
            val target = View(this).apply {
                isClickable = true
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                setOnTouchListener { view, event ->
                    // Never let a controller/window exception crash this
                    // service; OEM ROMs disable a service after such crashes.
                    runCatching { AccessibilityOverlayBridge.dispatchTouch(view, event) }
                        .onFailure { Log.e(TAG, "Touch proxy dispatch failed", it) }
                        .getOrDefault(true)
                }
            }
            val nextParams = createLayoutParams(bounds)
            manager.addView(target, nextParams)
            touchView = target
            touchParams = nextParams
            return
        }
        params.width = bounds.width
        params.height = bounds.height
        params.x = bounds.left
        params.y = bounds.top
        manager.updateViewLayout(existing, params)
    }

    private fun removeTouchView() {
        val target = touchView ?: return
        windowManager?.let { manager -> runCatching { manager.removeViewImmediate(target) } }
        touchView = null
        touchParams = null
    }

    private fun createLayoutParams(bounds: IntRect) = WindowManager.LayoutParams(
        bounds.width,
        bounds.height,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = bounds.left
        y = bounds.top
        title = "Glass orb trusted touch proxy"
    }

    private companion object {
        const val TAG = "GlassOrbAccessibility"
    }
}
