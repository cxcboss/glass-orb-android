package com.cxcboss.glassorb.render

import com.cxcboss.glassorb.overlay.PresentationGeometry

/**
 * Immutable geometry for one submitted frame. Every surface that presents or
 * intercepts the orb uses this object so a display read or shape calculation
 * cannot drift between render, input, dim, and blur updates.
 */
internal data class RenderPresentationFrame(
    val presentation: PresentationGeometry,
    val shape: ShapeFrame,
) {
    companion object {
        fun from(
            snapshot: RenderSnapshot,
            presentation: PresentationGeometry,
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
            )
        }
    }
}
