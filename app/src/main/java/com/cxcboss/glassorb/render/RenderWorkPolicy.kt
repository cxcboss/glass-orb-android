package com.cxcboss.glassorb.render

import com.cxcboss.glassorb.overlay.OverlayState

/** Pure decisions for work that may be skipped without changing presentation. */
internal object RenderWorkPolicy {
    fun shouldRenderAmbientScene(
        state: OverlayState,
        effectsFrozen: Boolean,
        scenePopulated: Boolean,
    ): Boolean = state != OverlayState.Collapsed && (!effectsFrozen || !scenePopulated)

    fun shouldAdvanceAmbientScene(
        state: OverlayState,
        effectsFrozen: Boolean,
    ): Boolean = state != OverlayState.Collapsed && !effectsFrozen
}

/** Pure transition guard used to avoid repeatedly posting the same pause state. */
internal object RenderPauseTransition {
    fun shouldApply(currentPaused: Boolean, requestedPaused: Boolean): Boolean =
        currentPaused != requestedPaused
}
