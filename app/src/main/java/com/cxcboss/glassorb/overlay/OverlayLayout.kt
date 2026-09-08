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
        val visual = collapsedBounds(geometry, safeBoundsPx, density)
        if (scale <= 1f) return visual

        // Enlarge around the rendered capsule's actual center. Scaling the
        // requested width/height before placement shifts left/right presets
        // and pins the top edge, which makes the hit area feel detached from
        // the capsule. Keep the center fixed first, then clamp only when the
        // display edge leaves no room.
        val width = (visual.width * scale).roundToInt()
            .coerceAtLeast(visual.width)
            .coerceAtMost(safeBoundsPx.width.coerceAtLeast(1))
        val height = (visual.height * scale).roundToInt()
            .coerceAtLeast(visual.height)
            .coerceAtMost(safeBoundsPx.height.coerceAtLeast(1))
        val centerX = (visual.left + visual.right) * 0.5f
        val centerY = (visual.top + visual.bottom) * 0.5f
        val left = (centerX - width * 0.5f).roundToInt()
            .coerceIn(safeBoundsPx.left, safeBoundsPx.right - width)
        val top = (centerY - height * 0.5f).roundToInt()
            .coerceIn(safeBoundsPx.top, safeBoundsPx.bottom - height)
        return IntRect(left, top, left + width, top + height)
    }

    fun orbTouchBounds(geometry: GeometryConfig, safeBoundsPx: IntRect, density: Float): IntRect =
        placedBounds(
            geometry.orbDiameterDp.dpToPx(density),
            geometry.orbDiameterDp.dpToPx(density),
            geometry, safeBoundsPx, density,
        )

    /**
     * Application overlays are below the status-bar window for input. A window
     * whose top is inside that protected strip cannot receive any touch, even if
     * it uses FLAG_LAYOUT_NO_LIMITS. Move the independent hit-proxy below the
     * strip instead of leaving a fully blocked window at y=0.
     *
     * The visual renderer is intentionally not moved: this is only a fallback
     * hit target for configurations that place the capsule under the status bar.
     */
    fun moveTouchBelowProtectedTop(bounds: IntRect, blockedBottomPx: Int, limitBottomPx: Int): IntRect {
        if (bounds.top >= blockedBottomPx) return bounds
        val newTop = blockedBottomPx.coerceIn(0, limitBottomPx)
        if (newTop >= limitBottomPx) return bounds.copy(top = limitBottomPx, bottom = limitBottomPx)
        val height = bounds.height.coerceAtLeast(1)
        val newBottom = (newTop + height).coerceAtMost(limitBottomPx)
        return bounds.copy(top = newTop, bottom = newBottom.coerceAtLeast(newTop + 1))
    }

    fun expandedBounds(geometry: GeometryConfig, safeBoundsPx: IntRect, density: Float): IntRect {
        val visualSizeDp = expandedCanvasDp(geometry)
        val size = max(visualSizeDp, MIN_TOUCH_DP).dpToPx(density)
        // Keep the capsule anchor unchanged while reserving room below it for
        // the orb. The 20 px gap is physical pixels, so convert it to dp only
        // for the window's measured height.
        val dropDp = if (geometry.expandBelowCapsule) {
            geometry.capsuleHeightDp + EXPAND_BELOW_GAP_PX / density
        } else {
            0f
        }
        val height = (visualSizeDp + dropDp).dpToPx(density)
        val placed = placedBounds(size, height, geometry, safeBoundsPx, density)
        // The capsule can briefly rebound above its settled top. Reserve a
        // physical top margin inside the rendering window so that the Texture
        // surface does not clip that part of the animation.
        val topRoom = EXPAND_REBOUND_TOP_MARGIN_PX.roundToInt()
            .coerceAtMost((placed.top - safeBoundsPx.top).coerceAtLeast(0))
        return if (topRoom == 0) placed else placed.copy(top = placed.top - topRoom)
    }

    const val EXPAND_BELOW_GAP_PX = 20f
    private const val EXPAND_REBOUND_TOP_MARGIN_PX = 24f

    /** Screen-space end of the dim gradient, based on the visible orb extent. */
    fun expandedVisualBottomPx(geometry: GeometryConfig, safeBoundsPx: IntRect, density: Float): Int {
        val capsule = collapsedBounds(geometry, safeBoundsPx, density)
        val capsuleVisualHeightPx = geometry.capsuleHeightDp.dpToPx(density)
        val orbTopPx = capsule.top + capsuleVisualHeightPx +
            if (geometry.expandBelowCapsule) EXPAND_BELOW_GAP_PX.roundToInt() else 0
        val orbExtentDp = maxOf(
            geometry.orbDiameterDp,
            geometry.orbDiameterDp * geometry.effectScale,
        ) + geometry.outerMarginDp
        return orbTopPx + orbExtentDp.dpToPx(density)
    }

    fun expandedCanvasDp(geometry: GeometryConfig): Float = maxOf(
        geometry.orbDiameterDp * geometry.effectScale,
        maxOf(geometry.orbDiameterDp, geometry.capsuleWidthDp, geometry.capsuleHeightDp) * 1.28f,
    ) + geometry.outerMarginDp * 2f

    fun renderScale(geometry: GeometryConfig, width: Int, height: Int, density: Float, morph: Float): Float {
        val capsuleFit = minOf(1f, width / (geometry.capsuleWidthDp * density).coerceAtLeast(1f),
            height / (geometry.capsuleHeightDp * density).coerceAtLeast(1f))
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

    private const val MIN_TOUCH_DP = 38f
}
