package com.cxcboss.glassorb.motion

import org.junit.Assert.assertEquals
import org.junit.Test

class AmbientBandsTest {
    @Test
    fun `wave phase follows reference audio speed and wraps continuously`() {
        val bands = FrequencyBands(0.35f, 0.4f, 0.3f)
        val phase = AmbientBands.advanceWavePhase(0f, bands, 0.1f)
        assertEquals(62.831848f - 0.442f, phase, 0.0001f)
        assertEquals(phase, AmbientBands.advanceWavePhase(phase, bands, 0f), 0f)
    }

    @Test
    fun `ambient targets match the reference fallback at time zero`() {
        val bands = AmbientBands.targetsAt(0f)

        assertEquals(0.35f, bands.low, 0.0001f)
        assertEquals(0.6524413f, bands.mid, 0.0001f)
        assertEquals(0.5273244f, bands.high, 0.0001f)
    }

    @Test
    fun `smoothing advances twenty two percent toward each target`() {
        val current = FrequencyBands(0f, 0f, 0f)
        val target = FrequencyBands(1f, 0.5f, 0.25f)

        val smoothed = AmbientBands.smooth(current, target)

        assertEquals(FrequencyBands(0.22f, 0.11f, 0.055f), smoothed)
    }
}
