package com.cxcboss.glassorb.overlay

import com.cxcboss.glassorb.model.GeometryConfig
import com.cxcboss.glassorb.model.HorizontalAnchor
import kotlin.math.max
import kotlin.math.roundToInt

data class IntRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

object OverlayLayout {
    const val WINDOW_TOP_PADDING_DP = 0f

    fun collapsedBounds(geometry: GeometryConfig, safeBoundsPx: IntRect, density: Float): IntRect {
        val width = max(geometry.capsuleWidthDp, MIN_TOUCH_DP).dpToPx(density)
        val height = max(geometry.capsuleHeightDp, MIN_TOUCH_DP).dpToPx(density)
        return placedBounds(width, height, geometry, safeBoundsPx, density)
    }

    fun capsuleTouchBounds(geometry: GeometryConfig, safeBoundsPx: IntRect, density: Float): IntRect {
        val scale = if (geometry.enlargedTouchArea) geometry.touchAreaScale else 1f
        return placedBounds(
            (geometry.capsuleWidthDp * scale).dpToPx(density),
            (geometry.capsuleHeightDp * scale).dpToPx(density),
            geometry, safeBoundsPx, density,
        )
    }

    fun orbTouchBounds(geometry: GeometryConfig, safeBoundsPx: IntRect, density: Float): IntRect =
        placedBounds(
            geometry.orbDiameterDp.dpToPx(density),
            geometry.orbDiameterDp.dpToPx(density),
            geometry, safeBoundsPx, density,
        )

    /**
     * System status bars are above TYPE_APPLICATION_OVERLAY for input. If a visual
     * target overlaps that protected strip, extend an equivalent hit band below it
     * so the full configured touch height remains reachable without moving the orb.
     */
    fun extendTouchBelowBlockedTop(bounds: IntRect, blockedBottomPx: Int, limitBottomPx: Int): IntRect {
        val blockedHeight = (blockedBottomPx - bounds.top).coerceIn(0, bounds.height)
        if (blockedHeight == 0) return bounds
        return bounds.copy(bottom = (bounds.bottom + blockedHeight).coerceAtMost(limitBottomPx))
    }

    fun expandedBounds(geometry: GeometryConfig, safeBoundsPx: IntRect, density: Float): IntRect {
        val visualSizeDp = expandedCanvasDp(geometry)
        val size = max(visualSizeDp, MIN_TOUCH_DP).dpToPx(density)
        return placedBounds(size, size, geometry, safeBoundsPx, density)
    }

    fun expandedCanvasDp(geometry: GeometryConfig): Float = maxOf(
        geometry.orbDiameterDp * geometry.effectScale,
        maxOf(geometry.orbDiameterDp, geometry.capsuleWidthDp, geometry.capsuleHeightDp) * 1.28f,
    ) + geometry.outerMarginDp * 2f

    fun renderScale(geometry: GeometryConfig, width: Int, height: Int, density: Float, morph: Float): Float {
        val capsuleFit = minOf(1f, width / (geometry.capsuleWidthDp * density),
            height / (geometry.capsuleHeightDp * density))
        val expandedFit = minOf(1f, minOf(width, height) / (expandedCanvasDp(geometry) * density))
        return capsuleFit + (expandedFit - capsuleFit) * morph.coerceIn(0f, 1f)
    }

    fun anchorOffsets(expandedBounds: IntRect, collapsedBounds: IntRect, density: Float): OverlayAnchor {
        val collapsedCenterX = collapsedBounds.left + collapsedBounds.width * 0.5f
        return OverlayAnchor(
            topDp = WINDOW_TOP_PADDING_DP + (collapsedBounds.top - expandedBounds.top) / density,
            centerXDp = (collapsedCenterX - expandedBounds.left) / density,
        )
    }

    private fun placedBounds(
        requestedWidth: Int,
        requestedHeight: Int,
        geometry: GeometryConfig,
        safe: IntRect,
        density: Float,
    ): IntRect {
        val width = requestedWidth.coerceIn(1, safe.width.coerceAtLeast(1))
        val height = requestedHeight.coerceIn(1, safe.height.coerceAtLeast(1))
        val anchorX = when (geometry.horizontalAnchor) {
            HorizontalAnchor.Center -> safe.left + safe.width / 2
            HorizontalAnchor.Left -> safe.left + width / 2
            HorizontalAnchor.Right -> safe.right - width / 2
        }
        val requestedLeft = anchorX - width / 2 + geometry.horizontalOffsetDp.dpToPx(density)
        val maxLeft = max(safe.left, safe.right - width)
        val left = requestedLeft.coerceIn(safe.left, maxLeft)
        val requestedTop = safe.top + geometry.verticalOffsetDp.dpToPx(density)
        val maxTop = max(safe.top, safe.bottom - height)
        val top = requestedTop.coerceIn(safe.top, maxTop)
        return IntRect(left = left, top = top, right = left + width, bottom = top + height)
    }

    private fun Float.dpToPx(density: Float): Int = (this * density).roundToInt()

    private const val MIN_TOUCH_DP = 48f
}
