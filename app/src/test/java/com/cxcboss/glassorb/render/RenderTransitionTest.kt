package com.cxcboss.glassorb.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RenderTransitionTest {
    @Test
    fun `wave and dots crossfade without dimming the shared dark container`() {
        (0..100).forEach { step ->
            val weights = RenderTransition.weights(step / 100f)

            assertEquals(1f, weights.wave + weights.dots, 0.0001f)
            assertEquals(1f, weights.container, 0f)
            assertTrue(weights.wave >= 0f)
            assertTrue(weights.dots >= 0f)
        }
    }

    @Test
    fun `transition input is clamped`() {
        assertEquals(RenderWeights(wave = 1f, dots = 0f, container = 1f), RenderTransition.weights(-2f))
        assertEquals(RenderWeights(wave = 0f, dots = 1f, container = 1f), RenderTransition.weights(4f))
    }
}
