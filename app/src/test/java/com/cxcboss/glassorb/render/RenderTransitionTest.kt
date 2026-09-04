package com.cxcboss.glassorb.render

import com.cxcboss.glassorb.model.OrbConfig
import com.cxcboss.glassorb.motion.DragDeformation
import com.cxcboss.glassorb.overlay.OverlayState
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

    @Test
    fun `new render snapshot defaults preserve existing transition inputs`() {
        val snapshot = RenderSnapshot(
            config = OrbConfig(),
            state = OverlayState.Wave,
            springProgress = 0.42f,
        )

        val weights = RenderTransition.weights(snapshot.thinkingProgress)

        assertEquals(0f, snapshot.collapsePull, 0f)
        assertEquals(DragDeformation(0f, 0f, 1f, 1f, 0f), snapshot.deformation)
        assertEquals(RenderWeights(wave = 1f, dots = 0f, container = 1f), weights)
    }
}
