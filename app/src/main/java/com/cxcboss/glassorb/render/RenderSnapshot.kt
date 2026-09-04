package com.cxcboss.glassorb.render

import com.cxcboss.glassorb.model.OrbConfig
import com.cxcboss.glassorb.motion.FrequencyBands
import com.cxcboss.glassorb.overlay.OverlayState

data class RenderWeights(
    val wave: Float,
    val dots: Float,
    val container: Float,
)

object RenderTransition {
    fun weights(thinkingProgress: Float): RenderWeights {
        val t = thinkingProgress.coerceIn(0f, 1f)
        val dots = t * t * (3f - 2f * t)
        return RenderWeights(wave = 1f - dots, dots = dots, container = 1f)
    }
}

data class RenderSnapshot(
    val config: OrbConfig,
    val state: OverlayState,
    val springProgress: Float,
    val gestureOffsetDp: Float = 0f,
    val capsuleCenterOffsetDp: Float = 0f,
    val capsuleTopOffsetDp: Float = 0f,
    val pressProgress: Float = 0f,
    val thinkingProgress: Float = 0f,
    val timeSeconds: Float = 0f,
    val wavePhase: Float = 0f,
    val stateElapsedSeconds: Float = 0f,
    val bands: FrequencyBands = FrequencyBands(0.35f, 0.4f, 0.3f),
    val preview: Boolean = false,
)
