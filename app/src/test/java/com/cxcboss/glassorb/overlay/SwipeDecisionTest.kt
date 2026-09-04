package com.cxcboss.glassorb.overlay

import org.junit.Assert.assertEquals
import org.junit.Test

class SwipeDecisionTest {
    @Test
    fun `upward distance at threshold collapses the orb`() {
        assertEquals(
            SwipeDecision.Collapse,
            SwipeDecision.decide(deltaYDp = -64f, velocityYDpPerSecond = 0f),
        )
    }

    @Test
    fun `fast upward fling collapses even before distance threshold`() {
        assertEquals(
            SwipeDecision.Collapse,
            SwipeDecision.decide(deltaYDp = -20f, velocityYDpPerSecond = -900f),
        )
    }

    @Test
    fun `downward or short slow gesture restores the orb`() {
        assertEquals(
            SwipeDecision.Restore,
            SwipeDecision.decide(deltaYDp = 40f, velocityYDpPerSecond = 1200f),
        )
        assertEquals(
            SwipeDecision.Restore,
            SwipeDecision.decide(deltaYDp = -30f, velocityYDpPerSecond = -300f),
        )
    }
}
