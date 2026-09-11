package com.cxcboss.glassorb.overlay

import kotlin.math.max

/** Independent visibility transition used by immersive fullscreen and landscape hiding. */
internal enum class FullscreenCapsulePhase {
    None,
    Hiding,
    Showing,
}

internal data class FullscreenCapsuleFrame(
    val scale: Float,
    /** Progress from the original aspect ratio to a square. */
    val squareProgress: Float,
    val opacity: Float,
)

/**
 * Motion is intentionally separate from the orb morph. This lets the capsule
 * finish its own visual handoff without making the GL orb jump or changing its
 * existing gesture curve.
 */
internal object FullscreenCapsuleMotion {
    fun frame(phase: FullscreenCapsulePhase, springValue: Float): FullscreenCapsuleFrame = when (phase) {
        FullscreenCapsulePhase.None -> FullscreenCapsuleFrame(
            scale = 1f,
            squareProgress = 0f,
            opacity = 1f,
        )
        FullscreenCapsulePhase.Hiding -> hideFrame(springValue)
        FullscreenCapsulePhase.Showing -> showFrame(springValue)
    }

    private fun hideFrame(visibleSpringValue: Float): FullscreenCapsuleFrame {
        val progress = (1f - visibleSpringValue).coerceIn(0f, 1f)
        // A short swell leads the handoff; the longer second phase pulls the
        // capsule inward, so it never reads as a hard alpha cut.
        val swell = smoothstep(0f, 0.20f, progress) *
            (1f - smoothstep(0.20f, 0.42f, progress))
        // The dominant axis reaches the short axis first. The uniform shrink
        // starts just after that handoff and overlaps slightly so the motion
        // reads as one continuous transformation rather than a keyframe cut.
        val squareProgress = smoothstep(0f, 0.46f, progress)
        val shrink = smoothstep(0.34f, 1f, progress)
        val scale = max(0.08f, 1f + 0.10f * swell - 1.04f * shrink)
        // Keep the alpha handoff alive for the full hide spring. The old
        // endpoint made the capsule visually disappear well before the host
        // finished its transition, which read as an abrupt cut on fullscreen.
        val opacity = 1f - smoothstep(0.26f, 1f, progress)
        return FullscreenCapsuleFrame(
            scale = scale,
            squareProgress = squareProgress,
            opacity = opacity,
        )
    }

    private fun showFrame(visibleSpringValue: Float): FullscreenCapsuleFrame {
        // Keep a tiny seed instead of starting from a zero-area primitive. The
        // spring then supplies the overshoot and settles at the exact size.
        val progress = visibleSpringValue.coerceIn(0f, 1.16f)
        val grow = smoothstep(0f, 0.64f, progress.coerceIn(0f, 1f))
        val overshoot = smoothstep(1f, 1.16f, progress) * 0.06f
        val scale = max(0.08f, 0.12f + 0.88f * grow + overshoot)
        val squareProgress = 1f - smoothstep(0.56f, 1f, progress)
        val opacity = smoothstep(0f, 0.18f, visibleSpringValue.coerceIn(0f, 1f))
        return FullscreenCapsuleFrame(
            scale = scale,
            squareProgress = squareProgress,
            opacity = opacity,
        )
    }

    private fun smoothstep(edge0: Float, edge1: Float, value: Float): Float {
        val span = (edge1 - edge0).coerceAtLeast(0.0001f)
        val t = ((value - edge0) / span).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
}
