package com.cxcboss.glassorb.overlay

/**
 * The settled capsule is a static, opaque frame. Keeping a Choreographer loop
 * alive there wastes compositor and GPU time without changing a pixel.
 */
internal fun shouldContinueOverlayFrames(state: OverlayState, pressIsSettled: Boolean): Boolean =
    state != OverlayState.Collapsed || !pressIsSettled

/** State changes must be visible immediately, even when cadence throttling is active. */
internal fun shouldForceRenderAfterStateChange(previous: OverlayState, current: OverlayState): Boolean =
    previous != current
