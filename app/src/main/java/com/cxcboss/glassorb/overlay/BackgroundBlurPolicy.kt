package com.cxcboss.glassorb.overlay

import android.os.Build
import android.view.WindowManager
import com.cxcboss.glassorb.model.GeometryConfig

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
    val bounds: IntRect?,
    val shape: BlurShape,
)

internal object BackgroundBlurPolicy {
    fun isEligible(
        userEnabled: Boolean,
        apiLevel: Int,
        crossWindowBlurEnabled: Boolean,
        visible: Boolean,
        bounds: IntRect?,
        shape: BlurShape,
    ): Boolean = userEnabled &&
        apiLevel >= Build.VERSION_CODES.S &&
        crossWindowBlurEnabled &&
        visible &&
        shape == BlurShape.Circular &&
        bounds != null &&
        bounds.width > 0 &&
        bounds.height > 0 &&
        bounds.width == bounds.height

    fun shapeFor(geometry: GeometryConfig): BlurShape =
        if (geometry.orbWidthDp == geometry.orbHeightDp) BlurShape.Circular else BlurShape.Ellipse

    fun windowFlags(): Int = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
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
