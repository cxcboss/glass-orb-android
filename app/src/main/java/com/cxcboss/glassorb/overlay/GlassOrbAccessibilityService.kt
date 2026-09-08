package com.cxcboss.glassorb.overlay

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager

/**
 * Optional accessibility-only touch proxy. It deliberately does not inspect
 * window content or receive accessibility events; it exists solely so the
 * capsule can receive input in the status-bar strip when the user explicitly
 * enables the service in system settings.
 */
class GlassOrbAccessibilityService : AccessibilityService() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var windowManager: WindowManager
    private var touchView: View? = null
    private var touchParams: WindowManager.LayoutParams? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WindowManager::class.java)
        AccessibilityOverlayBridge.connect(this)
    }

    override fun onAccessibilityEvent(event: android.view.accessibility.AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent): Boolean {
        AccessibilityOverlayBridge.disconnect(this)
        if (::windowManager.isInitialized) removeTouchView()
        return super.onUnbind(intent)
    }

    internal fun updateTouchBounds(bounds: IntRect?) {
        mainHandler.post {
            if (!::windowManager.isInitialized) return@post
            if (bounds == null || bounds.width <= 0 || bounds.height <= 0) {
                removeTouchView()
                return@post
            }
            val existing = touchView
            val params = touchParams
            if (existing == null || params == null) {
                val target = View(this).apply {
                    isClickable = true
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    setOnTouchListener { view, event ->
                        AccessibilityOverlayBridge.dispatchTouch(view, event)
                    }
                }
                val nextParams = createLayoutParams(bounds)
                try {
                    windowManager.addView(target, nextParams)
                    touchView = target
                    touchParams = nextParams
                } catch (error: Throwable) {
                    Log.w(TAG, "Unable to add accessibility touch proxy", error)
                }
                return@post
            }
            params.width = bounds.width
            params.height = bounds.height
            params.x = bounds.left
            params.y = bounds.top
            runCatching { windowManager.updateViewLayout(existing, params) }
                .onFailure { Log.w(TAG, "Unable to update accessibility touch proxy", it) }
        }
    }

    override fun onDestroy() {
        AccessibilityOverlayBridge.disconnect(this)
        if (::windowManager.isInitialized) removeTouchView()
        super.onDestroy()
    }

    private fun removeTouchView() {
        val target = touchView ?: return
        runCatching { windowManager.removeViewImmediate(target) }
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
        title = "Glass orb accessibility touch proxy"
    }

    private companion object {
        const val TAG = "GlassOrbAccessibility"
    }
}
