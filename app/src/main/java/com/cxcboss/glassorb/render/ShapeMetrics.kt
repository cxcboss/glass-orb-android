package com.cxcboss.glassorb.render

import com.cxcboss.glassorb.model.GeometryConfig
import com.cxcboss.glassorb.motion.DragDeformation

data class ShapeMetrics(
    val topDp: Float,
    val centerXDp: Float,
    val centerYDp: Float,
    val widthDp: Float,
    val heightDp: Float,
) {
    companion object {
        fun interpolate(
            geometry: GeometryConfig,
            anchorTopDp: Float,
            anchorCenterXDp: Float,
            morph: Float,
            deformation: DragDeformation,
        ): ShapeMetrics {
            val clampedMorph = if (morph.isFinite()) morph.coerceIn(-0.12f, 1.08f) else 0f
            val widthDp = (lerp(geometry.capsuleWidthDp, geometry.orbDiameterDp, clampedMorph) * deformation.scaleX).coerceAtLeast(0f)
            val heightDp = (lerp(geometry.capsuleHeightDp, geometry.orbDiameterDp, clampedMorph) * deformation.scaleY).coerceAtLeast(0f)
            val topDp = anchorTopDp + deformation.topOffsetDp
            val centerXDp = anchorCenterXDp + deformation.offsetXDp
            val centerYDp = topDp + heightDp / 2f
            return ShapeMetrics(
                topDp = topDp,
                centerXDp = centerXDp,
                centerYDp = centerYDp,
                widthDp = widthDp,
                heightDp = heightDp,
            )
        }

        private fun lerp(start: Float, end: Float, fraction: Float): Float = start + (end - start) * fraction
    }
}
