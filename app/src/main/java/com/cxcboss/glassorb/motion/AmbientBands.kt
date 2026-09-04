package com.cxcboss.glassorb.motion

import kotlin.math.sin

data class FrequencyBands(
    val low: Float,
    val mid: Float,
    val high: Float,
)

object AmbientBands {
    private const val SMOOTHING = 0.22f

    /** Same audio-driven phase integrator as reference state.js; phase remains continuous. */
    fun advanceWavePhase(phase: Float, bands: FrequencyBands, deltaSeconds: Float): Float {
        val drive = (maxOf(bands.low, bands.mid, bands.high) * 0.4f).coerceIn(0f, 1f)
        val wrap = 62.831848f
        val next = (phase + (-2.5f - 12f * drive) * deltaSeconds.coerceAtLeast(0f)) % wrap
        return if (next < 0f) next + wrap else next
    }

    fun targetsAt(timeSeconds: Float): FrequencyBands = FrequencyBands(
        low = 0.35f + 0.25f * sin(2.1f * timeSeconds),
        mid = 0.40f + 0.30f * sin(3.7f * timeSeconds + 1f),
        high = 0.30f + 0.25f * sin(6.3f * timeSeconds + 2f),
    )

    fun smooth(current: FrequencyBands, target: FrequencyBands): FrequencyBands = FrequencyBands(
        low = current.low + (target.low - current.low) * SMOOTHING,
        mid = current.mid + (target.mid - current.mid) * SMOOTHING,
        high = current.high + (target.high - current.high) * SMOOTHING,
    )
}
