package com.cxcboss.glassorb.render

import com.cxcboss.glassorb.model.GeometryConfig
import com.cxcboss.glassorb.motion.DragDeformation
import org.junit.Assert.assertEquals
import org.junit.Test

class ShapeMetricsTest {
    private val geometry = GeometryConfig(
        capsuleWidthDp = 118f,
        capsuleHeightDp = 34f,
        orbDiameterDp = 128f,
    )

    @Test
    fun `morph endpoints keep the same anchored top position`() {
        val deformation = DragDeformation(
            offsetXDp = 0f,
            offsetYDp = 0f,
            scaleX = 1f,
            scaleY = 1f,
            topOffsetDp = 1.5f,
        )

        val collapsed = ShapeMetrics.interpolate(
            geometry = geometry,
            anchorTopDp = 20f,
            anchorCenterXDp = 64f,
            morph = 0f,
            deformation = deformation,
        )
        val expanded = ShapeMetrics.interpolate(
            geometry = geometry,
            anchorTopDp = 20f,
            anchorCenterXDp = 64f,
            morph = 1f,
            deformation = deformation,
        )

        assertEquals(collapsed.topDp, expanded.topDp, 0f)
        assertEquals(21.5f, collapsed.topDp, 0f)
    }

    @Test
    fun `top anchor remains stable while deformation rescales the shape`() {
        val deformation = DragDeformation(
            offsetXDp = 6f,
            offsetYDp = -5f,
            scaleX = 0.99f,
            scaleY = 0.985f,
            topOffsetDp = 1.25f,
        )

        listOf(0f, 0.25f, 0.5f, 0.75f, 1f).forEach { morph ->
            val metrics = ShapeMetrics.interpolate(
                geometry = geometry,
                anchorTopDp = 14f,
                anchorCenterXDp = 60f,
                morph = morph,
                deformation = deformation,
            )

            assertEquals(metrics.topDp, metrics.centerYDp - metrics.heightDp / 2f, 0.0001f)
            assertEquals(15.25f, metrics.topDp, 0.0001f)
        }
    }

    @Test
    fun `illegal morph values are clamped for width height and center x`() {
        val deformation = DragDeformation(
            offsetXDp = 5f,
            offsetYDp = 0f,
            scaleX = 0.98f,
            scaleY = 0.97f,
            topOffsetDp = 0f,
        )

        val belowRange = ShapeMetrics.interpolate(
            geometry = geometry,
            anchorTopDp = 18f,
            anchorCenterXDp = 72f,
            morph = -2f,
            deformation = deformation,
        )
        val collapsed = ShapeMetrics.interpolate(
            geometry = geometry,
            anchorTopDp = 18f,
            anchorCenterXDp = 72f,
            morph = 0f,
            deformation = deformation,
        )
        val aboveRange = ShapeMetrics.interpolate(
            geometry = geometry,
            anchorTopDp = 18f,
            anchorCenterXDp = 72f,
            morph = 4f,
            deformation = deformation,
        )
        val extended = ShapeMetrics.interpolate(
            geometry = geometry,
            anchorTopDp = 18f,
            anchorCenterXDp = 72f,
            morph = 1.08f,
            deformation = deformation,
        )

        assertEquals(collapsed.widthDp, belowRange.widthDp, 0.0001f)
        assertEquals(collapsed.heightDp, belowRange.heightDp, 0.0001f)
        assertEquals(collapsed.centerXDp, belowRange.centerXDp, 0.0001f)
        assertEquals(extended.widthDp, aboveRange.widthDp, 0.0001f)
        assertEquals(extended.heightDp, aboveRange.heightDp, 0.0001f)
        assertEquals(extended.centerXDp, aboveRange.centerXDp, 0.0001f)
    }
}
