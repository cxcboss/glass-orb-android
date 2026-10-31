package com.cxcboss.glassorb.overlay

import android.os.Build
import android.view.WindowManager
import com.cxcboss.glassorb.model.GeometryConfig
import kotlin.math.roundToInt

internal enum class BlurShape {
    Circular,
    Ellipse,
    Uncertain,
}

internal data class BackgroundBlurRequest(
    val userEnabled: Boolean,
    val apiLevel: Int,
    val crossWindowBlurEnabled: Boolean,
    val visible: Boolean,
    /** Stable outer window. This must not move with every animation frame. */
    val windowBounds: IntRect?,
    /** Per-frame oval aperture in physical display coordinates. */
    val apertureBounds: IntRect?,
    val blurRadiusPx: Int,
    val shape: BlurShape,
)

internal object BackgroundBlurPolicy {
    const val MAX_BLUR_RADIUS_PX = 150

    fun radiusPxFromPercent(percent: Float): Int =
        (percent.coerceIn(0f, 100f) * MAX_BLUR_RADIUS_PX / 100f).roundToInt()

    fun isEligible(
        userEnabled: Boolean,
        apiLevel: Int,
        crossWindowBlurEnabled: Boolean,
        visible: Boolean,
        windowBounds: IntRect?,
        apertureBounds: IntRect?,
        blurRadiusPx: Int,
        shape: BlurShape,
    ): Boolean = userEnabled &&
        apiLevel >= Build.VERSION_CODES.S &&
        crossWindowBlurEnabled &&
        visible &&
        blurRadiusPx > 0 &&
        shape != BlurShape.Uncertain &&
        windowBounds != null &&
        windowBounds.width > 0 &&
        windowBounds.height > 0 &&
        apertureBounds != null &&
        apertureBounds.width > 0 &&
        apertureBounds.height > 0

    fun shapeFor(geometry: GeometryConfig): BlurShape =
        if (geometry.orbWidthDp == geometry.orbHeightDp) BlurShape.Circular else BlurShape.Ellipse

    fun windowFlags(): Int = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN

    fun windowType(accessibilityWindow: Boolean, apiLevel: Int): Int = when {
        accessibilityWindow -> WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        apiLevel >= Build.VERSION_CODES.O -> WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else -> {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
    }
}
