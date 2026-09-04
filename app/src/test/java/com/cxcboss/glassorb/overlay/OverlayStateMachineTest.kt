package com.cxcboss.glassorb.overlay

import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayStateMachineTest {
    @Test
    fun `capsule tap expands and settling reaches wave`() {
        val machine = OverlayStateMachine()

        machine.onTap()
        assertEquals(OverlayState.Expanding, machine.state)

        machine.onAnimationSettled()
        assertEquals(OverlayState.Wave, machine.state)
    }

    @Test
    fun `orb tap enters thinking and timeout returns to wave`() {
        val machine = OverlayStateMachine(initialState = OverlayState.Wave)

        machine.onTap()
        assertEquals(OverlayState.Thinking, machine.state)

        machine.onThinkingTimeout()
        assertEquals(OverlayState.Wave, machine.state)
    }

    @Test
    fun `short swipe restores while qualifying swipe collapses`() {
        val machine = OverlayStateMachine(initialState = OverlayState.Wave)
        machine.onSwipeStart()
        machine.onSwipeEnd(SwipeDecision.Restore)
        assertEquals(OverlayState.Wave, machine.state)

        machine.onSwipeStart()
        machine.onSwipeEnd(SwipeDecision.Collapse)
        assertEquals(OverlayState.Collapsing, machine.state)
        machine.onAnimationSettled()
        assertEquals(OverlayState.Collapsed, machine.state)
    }

    @Test
    fun `restore returns swipe tracking to the state that started it`() {
        val machine = OverlayStateMachine(initialState = OverlayState.Thinking)

        machine.onSwipeStart()
        machine.onSwipe(12f)
        machine.onSwipeEnd(SwipeDecision.Restore)

        assertEquals(OverlayState.Thinking, machine.state)
    }

    @Test
    fun `collapse decision transitions swipe tracking into collapsing before settle`() {
        val machine = OverlayStateMachine(initialState = OverlayState.Wave)

        machine.onSwipeStart()
        machine.onSwipe(-72f)
        machine.onSwipeEnd(SwipeDecision.Collapse)

        assertEquals(OverlayState.Collapsing, machine.state)
    }
}
