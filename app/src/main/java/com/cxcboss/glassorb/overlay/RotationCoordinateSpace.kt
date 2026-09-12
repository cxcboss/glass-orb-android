package com.cxcboss.glassorb.overlay

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class FloatPoint(val x: Float, val y: Float)

/** Physical display information shared by all overlay geometry calculations. */
data class RotationDisplayGeometry(
    val physicalBounds: IntRect,
    val safeBounds: IntRect,
    val systemInsets: IntRect,
    val rotation: Int,
)

/** One paired read of display metrics and the display rotation. */
data class RotationDisplaySample(
    val geometry: RotationDisplayGeometry,
    val rotationBefore: Int,
    val rotationAfter: Int,
) {
    val isStable: Boolean
        get() = rotationBefore == rotationAfter && geometry.rotation == rotationBefore
}

/**
 * Maps points from the display's portrait reference space to its physical
 * current-space coordinates. Rectangles are deliberately not rotated: the
 * overlay content remains an axis-aligned portrait shape.
 */
object RotationCoordinateSpace {
    /**
     * Chooses the newest sample whose metrics and rotation agree. If the
     * display is mid-transition, retain the last known coherent snapshot so
     * layout and input do not consume a mixed orientation.
     */
    fun selectStableGeometry(
        samples: List<RotationDisplaySample>,
        previous: RotationDisplayGeometry?,
    ): RotationDisplayGeometry? = samples.lastOrNull(RotationDisplaySample::isStable)?.geometry ?: previous

    fun mapPortraitPoint(point: FloatPoint, currentDisplay: IntRect, rotation: Int): FloatPoint {
        val size = portraitReferenceSize(currentDisplay)
        val mapped = when (rotation) {
            ROTATION_90 -> FloatPoint(size.height - point.y, point.x)
            ROTATION_180 -> FloatPoint(size.width - point.x, size.height - point.y)
            ROTATION_270 -> FloatPoint(point.y, size.width - point.x)
            else -> point
        }
        return FloatPoint(
            currentDisplay.left + mapped.x,
            currentDisplay.top + mapped.y,
        )
    }

    fun mapCurrentPoint(point: FloatPoint, currentDisplay: IntRect, rotation: Int): FloatPoint {
        val size = portraitReferenceSize(currentDisplay)
        val x = point.x - currentDisplay.left
        val y = point.y - currentDisplay.top
        return when (rotation) {
            ROTATION_90 -> FloatPoint(y, size.height - x)
            ROTATION_180 -> FloatPoint(size.width - x, size.height - y)
            ROTATION_270 -> FloatPoint(size.width - y, x)
            else -> FloatPoint(x, y)
        }
    }

    fun portraitReferenceSize(currentDisplay: IntRect): DisplaySize = DisplaySize(
        width = min(currentDisplay.width, currentDisplay.height),
        height = max(currentDisplay.width, currentDisplay.height),
    )

    /**
     * Converts current physical edge insets into the portrait reference.
     *
     * The top edge is intentionally not treated as a visual safe-area edge.
     * The capsule is a system-bar overlay and its position slider must be able
     * to place it inside the status-bar region. Insets still constrain the
     * other edges so the fixed portrait coordinate space does not drift into
     * the navigation area after rotation.
     */
    fun portraitSafeBounds(display: RotationDisplayGeometry): IntRect {
        val size = portraitReferenceSize(display.physicalBounds)
        val insets = display.systemInsets
        val portraitInsets = when (display.rotation) {
            ROTATION_90 -> EdgeInsets(insets.top, insets.right, insets.bottom, insets.left)
            ROTATION_180 -> EdgeInsets(insets.right, insets.bottom, insets.left, insets.top)
            ROTATION_270 -> EdgeInsets(insets.bottom, insets.left, insets.top, insets.right)
            else -> EdgeInsets(insets.left, insets.top, insets.right, insets.bottom)
        }
        return IntRect(
            left = portraitInsets.left,
            top = 0,
            right = (size.width - portraitInsets.right).coerceAtLeast(portraitInsets.left),
            bottom = (size.height - portraitInsets.bottom).coerceAtLeast(1),
        )
    }

    fun placeAxisAligned(center: FloatPoint, width: Int, height: Int, safeBounds: IntRect): IntRect {
        val placedWidth = width.coerceIn(1, safeBounds.width.coerceAtLeast(1))
        val placedHeight = height.coerceIn(1, safeBounds.height.coerceAtLeast(1))
        val left = (center.x - placedWidth * 0.5f).roundToInt()
            .coerceIn(safeBounds.left, max(safeBounds.left, safeBounds.right - placedWidth))
        val top = (center.y - placedHeight * 0.5f).roundToInt()
            .coerceIn(safeBounds.top, max(safeBounds.top, safeBounds.bottom - placedHeight))
        return IntRect(left, top, left + placedWidth, top + placedHeight)
    }

    private data class EdgeInsets(val left: Int, val top: Int, val right: Int, val bottom: Int)

    data class DisplaySize(val width: Int, val height: Int)

    const val ROTATION_0 = 0
    const val ROTATION_90 = 1
    const val ROTATION_180 = 2
    const val ROTATION_270 = 3
}
