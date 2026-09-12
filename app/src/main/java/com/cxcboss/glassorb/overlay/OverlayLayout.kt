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

    /**
     * Selects the display-safe rectangle while keeping the island in its
     * portrait/top-anchor coordinate convention. Android reports window
     * bounds in the current display rotation; in landscape the navigation
     * inset is on a physical side, but it must not become the island's new
     * horizontal origin. The island therefore uses the full rotated display
     * width and only reserves the bottom system inset.
     */
    fun safeBoundsForDisplay(
        displayBounds: IntRect,
        systemInsets: IntRect,
        landscape: Boolean,
    ): IntRect {
        return IntRect(
            left = if (landscape) displayBounds.left else displayBounds.left + systemInsets.left,
            top = displayBounds.top,
            right = if (landscape) displayBounds.right else displayBounds.right - systemInsets.right,
            bottom = displayBounds.bottom - systemInsets.bottom,
        )
    }

    fun collapsedBounds(geometry: GeometryConfig, safeBoundsPx: IntRect, density: Float): IntRect {
        val width = max(geometry.capsuleWidthDp, MIN_TOUCH_DP).dpToPx(density)
        val height = max(geometry.capsuleHeightDp, MIN_TOUCH_DP).dpToPx(density)
        return placedBounds(width, height, geometry, safeBoundsPx, density)
    }

    fun collapsedBounds(
        geometry: GeometryConfig,
        display: RotationDisplayGeometry,
        density: Float,
    ): IntRect = collapsedBounds(geometry, display.safeBounds, density, display)

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

    fun capsuleTouchBounds(
        geometry: GeometryConfig,
        display: RotationDisplayGeometry,
        density: Float,
    ): IntRect {
        val scale = if (geometry.enlargedTouchArea) geometry.touchAreaScale else 1f
        val visual = collapsedBounds(geometry, display, density)
        if (scale <= 1f) return visual
        val width = (visual.width * scale).roundToInt()
            .coerceAtLeast(visual.width)
            .coerceAtMost(display.safeBounds.width.coerceAtLeast(1))
        val height = (visual.height * scale).roundToInt()
            .coerceAtLeast(visual.height)
            .coerceAtMost(display.safeBounds.height.coerceAtLeast(1))
        return RotationCoordinateSpace.placeAxisAligned(
            center = FloatPoint(
                (visual.left + visual.right) * 0.5f,
                (visual.top + visual.bottom) * 0.5f,
            ),
            width = width,
            height = height,
            safeBounds = display.safeBounds,
        )
    }

    fun orbTouchBounds(geometry: GeometryConfig, safeBoundsPx: IntRect, density: Float): IntRect =
        placedBounds(
            geometry.orbWidthDp.dpToPx(density),
            geometry.orbHeightDp.dpToPx(density),
            geometry, safeBoundsPx, density,
        )

    fun orbTouchBounds(
        geometry: GeometryConfig,
        display: RotationDisplayGeometry,
        density: Float,
    ): IntRect {
        val capsule = collapsedBounds(geometry, display, density)
        val top = capsule.top + if (geometry.expandBelowCapsule) {
            geometry.capsuleHeightDp.dpToPx(density) + EXPAND_BELOW_GAP_PX.roundToInt()
        } else {
            0
        }
        return RotationCoordinateSpace.placeAxisAligned(
            center = FloatPoint(
                (capsule.left + capsule.right) * 0.5f,
                top + geometry.orbHeightDp.dpToPx(density) * 0.5f,
            ),
            width = geometry.orbWidthDp.dpToPx(density),
            height = geometry.orbHeightDp.dpToPx(density),
            safeBounds = display.safeBounds,
        )
    }

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

    fun expandedBounds(
        geometry: GeometryConfig,
        display: RotationDisplayGeometry,
        density: Float,
    ): IntRect {
        val visualSizeDp = expandedCanvasDp(geometry)
        val size = max(visualSizeDp, MIN_TOUCH_DP).dpToPx(density)
        val dropDp = if (geometry.expandBelowCapsule) {
            geometry.capsuleHeightDp + EXPAND_BELOW_GAP_PX / density
        } else {
            0f
        }
        val height = (visualSizeDp + dropDp).dpToPx(density)
        val collapsed = collapsedBounds(geometry, display, density)
        val placed = RotationCoordinateSpace.placeAxisAligned(
            center = FloatPoint(
                (collapsed.left + collapsed.right) * 0.5f,
                collapsed.top + height * 0.5f,
            ),
            width = size,
            height = height,
            safeBounds = display.safeBounds,
        )
        val topRoom = EXPAND_REBOUND_TOP_MARGIN_PX.roundToInt()
            .coerceAtMost((placed.top - display.safeBounds.top).coerceAtLeast(0))
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
            geometry.orbWidthDp,
            geometry.orbHeightDp,
            geometry.orbWidthDp * geometry.effectScale,
            geometry.orbHeightDp * geometry.effectScale,
        ) + geometry.outerMarginDp
        return orbTopPx + orbExtentDp.dpToPx(density)
    }

    fun expandedVisualBottomPx(
        geometry: GeometryConfig,
        display: RotationDisplayGeometry,
        density: Float,
    ): Int {
        val capsule = collapsedBounds(geometry, display, density)
        val orbTopPx = capsule.top + geometry.capsuleHeightDp.dpToPx(density) +
            if (geometry.expandBelowCapsule) EXPAND_BELOW_GAP_PX.roundToInt() else 0
        val orbExtentDp = maxOf(
            geometry.orbWidthDp,
            geometry.orbHeightDp,
            geometry.orbWidthDp * geometry.effectScale,
            geometry.orbHeightDp * geometry.effectScale,
        ) + geometry.outerMarginDp
        return orbTopPx + orbExtentDp.dpToPx(density)
    }

    fun expandedCanvasDp(geometry: GeometryConfig): Float = maxOf(
        maxOf(geometry.orbWidthDp, geometry.orbHeightDp) * geometry.effectScale,
        maxOf(geometry.orbWidthDp, geometry.orbHeightDp, geometry.capsuleWidthDp, geometry.capsuleHeightDp) * 1.28f,
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

    private fun collapsedBounds(
        geometry: GeometryConfig,
        safeBoundsPx: IntRect,
        density: Float,
        display: RotationDisplayGeometry,
    ): IntRect {
        val width = max(geometry.capsuleWidthDp, MIN_TOUCH_DP).dpToPx(density)
        val height = max(geometry.capsuleHeightDp, MIN_TOUCH_DP).dpToPx(density)
        val portraitSafe = RotationCoordinateSpace.portraitSafeBounds(display)
        val anchorX = when (geometry.horizontalAnchor) {
            HorizontalAnchor.Center -> (portraitSafe.left + portraitSafe.right) * 0.5f
            HorizontalAnchor.Left -> portraitSafe.left + width * 0.5f
            HorizontalAnchor.Right -> portraitSafe.right - width * 0.5f
        } + geometry.horizontalOffsetDp.dpToPx(density)
        val anchorY = portraitSafe.top + geometry.verticalOffsetDp.dpToPx(density) + height * 0.5f
        val mappedCenter = RotationCoordinateSpace.mapPortraitPoint(
            FloatPoint(anchorX, anchorY),
            display.physicalBounds,
            display.rotation,
        )
        return RotationCoordinateSpace.placeAxisAligned(mappedCenter, width, height, safeBoundsPx)
    }

    private fun Float.dpToPx(density: Float): Int = (this * density).roundToInt()

    private const val MIN_TOUCH_DP = 38f
}
