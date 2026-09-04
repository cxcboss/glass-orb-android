package com.cxcboss.glassorb.overlay

sealed interface OverlayState {
    data object Collapsed : OverlayState
    data object Expanding : OverlayState
    data object Wave : OverlayState
    data object Thinking : OverlayState
    data class SwipeTracking(val offsetYDp: Float) : OverlayState
    data object Collapsing : OverlayState
    data object Hidden : OverlayState
}

class OverlayStateMachine(initialState: OverlayState = OverlayState.Collapsed) {
    var state: OverlayState = initialState
        private set

    private var stateBeforeSwipe: OverlayState = OverlayState.Wave

    fun onTap() {
        state = when (state) {
            OverlayState.Collapsed -> OverlayState.Expanding
            OverlayState.Wave -> OverlayState.Thinking
            OverlayState.Thinking -> OverlayState.Thinking
            else -> state
        }
    }

    fun onThinkingTimeout() {
        if (state == OverlayState.Thinking) state = OverlayState.Wave
    }

    fun onAnimationSettled() {
        state = when (state) {
            OverlayState.Expanding -> OverlayState.Wave
            OverlayState.Collapsing -> OverlayState.Collapsed
            else -> state
        }
    }

    fun onSwipeStart() {
        if (state == OverlayState.Wave || state == OverlayState.Thinking) {
            stateBeforeSwipe = state
            state = OverlayState.SwipeTracking(0f)
        }
    }

    fun onSwipe(offsetYDp: Float) {
        if (state is OverlayState.SwipeTracking) {
            state = OverlayState.SwipeTracking(offsetYDp)
        }
    }

    fun onSwipeEnd(decision: SwipeDecision) {
        if (state !is OverlayState.SwipeTracking) return
        state = if (decision == SwipeDecision.Collapse) OverlayState.Collapsing else stateBeforeSwipe
    }

    fun hide() {
        state = OverlayState.Hidden
    }

    fun showCollapsed() {
        state = OverlayState.Collapsed
    }

    fun beginAutomaticCollapse() {
        if (state == OverlayState.Wave || state == OverlayState.Thinking) state = OverlayState.Collapsing
    }
}
