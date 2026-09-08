package com.cxcboss.glassorb.overlay

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayRenderPolicyTest {
    @Test fun collapsedIdleDoesNotKeepSubmittingFrames() {
        assertFalse(shouldContinueOverlayFrames(OverlayState.Collapsed, pressIsSettled = true))
        assertTrue(shouldContinueOverlayFrames(OverlayState.Collapsed, pressIsSettled = false))
    }

    @Test fun everyVisibleOrbStateKeepsItsAnimationFrames() {
        listOf(
            OverlayState.Expanding,
            OverlayState.Wave,
            OverlayState.Thinking,
            OverlayState.SwipeTracking(0f),
            OverlayState.Collapsing,
        ).forEach { state ->
            assertTrue(shouldContinueOverlayFrames(state, pressIsSettled = true))
        }
    }

    @Test fun stateTransitionRequestsAnImmediateFrame() {
        assertTrue(shouldForceRenderAfterStateChange(OverlayState.Collapsing, OverlayState.Collapsed))
        assertFalse(shouldForceRenderAfterStateChange(OverlayState.Wave, OverlayState.Wave))
    }
}
