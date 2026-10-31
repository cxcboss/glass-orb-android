package com.cxcboss.glassorb.overlay

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayRenderPolicyTest {
    @Test fun collapsedIdleDoesNotKeepSubmittingFrames() {
        assertFalse(shouldContinueOverlayFrames(OverlayState.Collapsed, pressIsSettled = true))
        assertTrue(shouldContinueOverlayFrames(OverlayState.Collapsed, pressIsSettled = false))
    }

    @Test fun fullscreenVisibilityTransitionKeepsCollapsedOrbSubmittingFrames() {
        assertFalse(
            shouldSubmitOverlayFrame(
                state = OverlayState.Collapsed,
                fullscreenPhase = FullscreenCapsulePhase.None,
                forceRender = false,
            ),
        )
        assertTrue(
            shouldSubmitOverlayFrame(
                state = OverlayState.Collapsed,
                fullscreenPhase = FullscreenCapsulePhase.Hiding,
                forceRender = false,
            ),
        )
        assertTrue(
            shouldSubmitOverlayFrame(
                state = OverlayState.Collapsed,
                fullscreenPhase = FullscreenCapsulePhase.Showing,
                forceRender = false,
            ),
        )
        assertTrue(
            shouldSubmitOverlayFrame(
                state = OverlayState.Collapsed,
                fullscreenPhase = FullscreenCapsulePhase.None,
                forceRender = true,
            ),
        )
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

    @Test fun collapseEffectsStayLiveDuringTransitions() {
        assertFalse(shouldFreezeCollapseEffects(OverlayState.SwipeTracking(0f), 0.3f))
        assertFalse(shouldFreezeCollapseEffects(OverlayState.SwipeTracking(0f), 0.46f))
        assertFalse(shouldFreezeCollapseEffects(OverlayState.Collapsing, 0f))
    }
}
