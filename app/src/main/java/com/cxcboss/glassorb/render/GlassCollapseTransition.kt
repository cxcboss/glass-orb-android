package com.cxcboss.glassorb.render

import com.cxcboss.glassorb.overlay.OverlayState

/**
 * Keeps the expensive glass scene and the capsule shell as two independent
 * layers during a close. The scene can fade away while the geometry continues
 * moving, so the settled capsule never needs a discontinuous shader switch.
 */
internal object GlassCollapseTransition {
    const val COLLAPSE_FADE_START = 0.46f
    private const val COLLAPSE_FADE_END = 0.88f
    private const val EXPAND_FADE_START = 0.08f
    private const val EXPAND_FADE_END = 0.72f

    fun contentOpacity(state: OverlayState, rawMorph: Float, visualMorph: Float): Float = when {
        state == OverlayState.Collapsed -> 0f
        state == OverlayState.Collapsing -> collapseOpacity(rawMorph)
        state is OverlayState.SwipeTracking && rawMorph < 0.999f -> collapseOpacity(rawMorph)
        state == OverlayState.Expanding -> smoothstep(EXPAND_FADE_START, EXPAND_FADE_END, visualMorph)
        else -> 1f
    }

    private fun collapseOpacity(rawMorph: Float): Float {
        val collapseProgress = 1f - rawMorph.coerceIn(0f, 1f)
        return 1f - smoothstep(COLLAPSE_FADE_START, COLLAPSE_FADE_END, collapseProgress)
    }

    private fun smoothstep(edge0: Float, edge1: Float, value: Float): Float {
        val span = (edge1 - edge0).coerceAtLeast(0.0001f)
        val t = ((value - edge0) / span).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
}
