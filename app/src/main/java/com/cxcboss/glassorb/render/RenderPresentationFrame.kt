package com.cxcboss.glassorb.render

import com.cxcboss.glassorb.overlay.IntRect
import com.cxcboss.glassorb.overlay.PresentationGeometry
import kotlin.math.roundToInt

/**
 * Immutable geometry for one submitted frame. Every surface that presents or
 * intercepts the orb uses this object so a display read or shape calculation
 * cannot drift between render, input, dim, and blur updates.
 */
internal data class RenderPresentationFrame(
    val presentation: PresentationGeometry,
    val shape: ShapeFrame,
    val shapeScreenBounds: IntRect,
) {
    companion object {
        fun from(
            snapshot: RenderSnapshot,
            presentation: PresentationGeometry,
            canvasX: Int,
            canvasY: Int,
            density: Float,
        ): RenderPresentationFrame {
            val shape = ShapeFrame.from(
                snapshot = snapshot,
                width = snapshot.viewportWidth.coerceAtLeast(1),
                height = snapshot.viewportHeight.coerceAtLeast(1),
                displayDensity = density,
            )
            return RenderPresentationFrame(
                presentation = presentation,
                shape = shape,
                shapeScreenBounds = IntRect(
                    left = (canvasX + shape.centerX - shape.shapeWidth * 0.5f).roundToInt(),
                    top = (canvasY + shape.top).roundToInt(),
                    right = (canvasX + shape.centerX + shape.shapeWidth * 0.5f).roundToInt(),
                    bottom = (canvasY + shape.top + shape.shapeHeight).roundToInt(),
                ),
            )
        }
    }
}
