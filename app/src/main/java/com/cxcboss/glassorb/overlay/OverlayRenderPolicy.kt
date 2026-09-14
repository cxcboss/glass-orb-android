package com.cxcboss.glassorb.overlay

/**
 * The settled capsule is a static, opaque frame. Keeping a Choreographer loop
 * alive there wastes compositor and GPU time without changing a pixel.
 */
internal fun shouldContinueOverlayFrames(state: OverlayState, pressIsSettled: Boolean): Boolean =
    state != OverlayState.Collapsed || !pressIsSettled

/**
 * Fullscreen visibility is animated independently from the orb state. During
 * that handoff the state machine is already collapsed, so the fullscreen
 * phase must keep the producer alive until its spring settles.
 */
internal fun shouldSubmitOverlayFrame(
    state: OverlayState,
    fullscreenPhase: FullscreenCapsulePhase,
    forceRender: Boolean,
): Boolean = state != OverlayState.Collapsed ||
    fullscreenPhase != FullscreenCapsulePhase.None ||
    forceRender

/** State changes must be visible immediately, even when cadence throttling is active. */
internal fun shouldForceRenderAfterStateChange(previous: OverlayState, current: OverlayState): Boolean =
    previous != current

/** Keep transition rendering continuous; the producer cadence is fixed at 120 Hz. */
internal fun shouldFreezeCollapseEffects(state: OverlayState, collapsePull: Float): Boolean = false
