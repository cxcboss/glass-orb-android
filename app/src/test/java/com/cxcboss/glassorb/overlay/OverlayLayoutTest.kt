package com.cxcboss.glassorb.overlay

import com.cxcboss.glassorb.model.GeometryConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayLayoutTest {
    @Test
    fun `constrained canvas does not change capsule scale at morph zero`() {
        val geometry = GeometryConfig(capsuleWidthDp = 220f, orbDiameterDp = 220f,
            effectScale = 1.5f, outerMarginDp = 48f)
        val collapsed = OverlayLayout.renderScale(geometry, 220, 48, 1f, 0f)
        val expanding = OverlayLayout.renderScale(geometry, 360, 360, 1f, 0f)
        assertEquals(collapsed, expanding, 0f)
        val target = OverlayLayout.renderScale(geometry, 360, 360, 1f, 1f)
        val halfway = OverlayLayout.renderScale(geometry, 360, 360, 1f, 0.5f)
        assertEquals((collapsed + target) / 2f, halfway, 0.0001f)
    }

    @Test
    fun `large capsule and small orb retain enough room during morph`() {
        val safe = IntRect(0, 24, 400, 800)
        val geometry = GeometryConfig(capsuleWidthDp = 220f, orbDiameterDp = 88f,
            outerMarginDp = 8f, effectScale = 0.9f)
        val bounds = OverlayLayout.expandedBounds(geometry, safe, 1f)
        assertTrue("expanded canvas must contain the starting capsule", bounds.width >= 220)
    }

    @Test
    fun `tiny safe regions bound oversized configurations`() {
        val safe = IntRect(15, 20, 195, 180)
        val bounds = OverlayLayout.expandedBounds(GeometryConfig(orbDiameterDp = 220f), safe, 1f)
        assertTrue(bounds.right <= safe.right)
        assertTrue(bounds.bottom <= safe.bottom)
    }

    @Test
    fun `center preset clamps the expanded window inside the safe screen`() {
        val safe = IntRect(left = 0, top = 72, right = 1080, bottom = 2280)
        val geometry = GeometryConfig(horizontalOffsetDp = 900f, verticalOffsetDp = 8f)

        val bounds = OverlayLayout.expandedBounds(
            geometry = geometry,
            safeBoundsPx = safe,
            density = 3f,
        )

        assertTrue(bounds.left >= safe.left)
        assertTrue(bounds.right <= safe.right)
        assertTrue(bounds.top >= safe.top)
        assertTrue(bounds.bottom <= safe.bottom)
    }

    @Test
    fun `collapsed touch window includes a minimum forty eight dp hit target`() {
        val safe = IntRect(left = 0, top = 72, right = 1080, bottom = 2280)

        val bounds = OverlayLayout.collapsedBounds(
            geometry = GeometryConfig(capsuleWidthDp = 90f, capsuleHeightDp = 24f),
            safeBoundsPx = safe,
            density = 3f,
        )

        assertEquals(144, bounds.height)
        assertEquals(270, bounds.width)
    }
}
