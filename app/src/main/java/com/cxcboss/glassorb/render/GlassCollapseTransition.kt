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

    fun contentOpacity(
        state: OverlayState,
        rawMorph: Float,
        visualMorph: Float,
        stateElapsedSeconds: Float = Float.POSITIVE_INFINITY,
    ): Float = when {
        state == OverlayState.Collapsed -> 0f
        // The scene is primed once while expanding and then reused. Keep the
        // optical contents visible; setting this to zero makes the fragment
        // shader replace the contents with its black transition shell.
        state == OverlayState.Expanding -> 1f
        state == OverlayState.Wave && rawMorph >= 0.999f -> 1f
        state == OverlayState.Collapsing -> collapseOpacity(rawMorph)
        // A cancelled/partial drag is still an interactive glass orb. Only
        // the committed Collapsing state should fade into the black capsule.
        state is OverlayState.SwipeTracking -> 1f
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
