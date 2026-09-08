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

/**
 * Minimal trusted-window host for the optional status-bar touch target.
 * No accessibility content, screenshots, gestures, key events, or touch
 * exploration are requested. The XML event filter is limited to this app.
 */
class GlassOrbAccessibilityService : AccessibilityService() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var windowManager: WindowManager? = null
    private var touchView: View? = null
    private var touchParams: WindowManager.LayoutParams? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        val manager = getSystemService(WindowManager::class.java)
        if (manager == null) {
            Log.e(TAG, "WindowManager unavailable; trusted touch proxy is inactive")
            return
        }
        windowManager = manager
        // Do not call setServiceInfo here. OEM accessibility managers can
        // treat a runtime event mask of zero as an invalid service and disable
        // it on the next interaction; the minimal XML filter is authoritative.
        runCatching { AccessibilityOverlayBridge.connect(this) }
            .onFailure { Log.e(TAG, "Unable to connect trusted touch proxy", it) }
    }

    override fun onAccessibilityEvent(event: android.view.accessibility.AccessibilityEvent?) = Unit

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
        super.onDestroy()
    }

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
