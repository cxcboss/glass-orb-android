package com.cxcboss.glassorb.render

import com.cxcboss.glassorb.overlay.OverlayState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassCollapseTransitionTest {
    @Test
    fun `collapse keeps the glass content before the midpoint`() {
        assertEquals(
            1f,
            GlassCollapseTransition.contentOpacity(
                state = OverlayState.Collapsing,
                rawMorph = 1f,
                visualMorph = 1f,
            ),
            0f,
        )
        assertEquals(
            1f,
            GlassCollapseTransition.contentOpacity(
                state = OverlayState.Collapsing,
                rawMorph = 0.54f,
                visualMorph = 0f,
            ),
            0f,
        )
    }

    @Test
    fun `collapse fades content continuously after midpoint`() {
        val mid = GlassCollapseTransition.contentOpacity(
            state = OverlayState.Collapsing,
            rawMorph = 0.5f,
            visualMorph = 0f,
        )
        val end = GlassCollapseTransition.contentOpacity(
            state = OverlayState.Collapsing,
            rawMorph = 0f,
            visualMorph = 0f,
        )

        assertTrue(mid in 0f..1f)
        assertTrue(mid < 1f)
        assertEquals(0f, end, 0f)
    }

    @Test
    fun `expansion reveals content only with the orb shape`() {
        assertEquals(
            0f,
            GlassCollapseTransition.contentOpacity(
                state = OverlayState.Expanding,
                rawMorph = 0f,
                visualMorph = 0f,
            ),
            0f,
        )
        assertEquals(
            1f,
            GlassCollapseTransition.contentOpacity(
                state = OverlayState.Expanding,
                rawMorph = 1f,
                visualMorph = 1f,
            ),
            0f,
        )
    }

    @Test
    fun `collapsed state always has no glass content`() {
        assertEquals(
            0f,
            GlassCollapseTransition.contentOpacity(
                state = OverlayState.Collapsed,
                rawMorph = 1f,
                visualMorph = 1f,
            ),
            0f,
        )
    }
}
