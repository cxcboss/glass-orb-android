package com.cxcboss.glassorb.render

import com.cxcboss.glassorb.overlay.OverlayState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RenderWorkPolicyTest {
    @Test
    fun `frozen collapse keeps populated ambient scene and palette unchanged`() {
        assertFalse(RenderWorkPolicy.shouldRenderAmbientScene(OverlayState.Collapsing, true, true))
        assertTrue(RenderWorkPolicy.shouldRenderAmbientScene(OverlayState.Collapsing, true, false))
        assertFalse(RenderWorkPolicy.shouldAdvanceAmbientScene(OverlayState.Collapsing, true))
    }

    @Test
    fun `collapsed state never advances or repopulates ambient scene`() {
        assertFalse(RenderWorkPolicy.shouldRenderAmbientScene(OverlayState.Collapsed, false, false))
        assertFalse(RenderWorkPolicy.shouldRenderAmbientScene(OverlayState.Collapsed, true, true))
        assertFalse(RenderWorkPolicy.shouldAdvanceAmbientScene(OverlayState.Collapsed, false))
    }

    @Test
    fun `unpaused transition is applied only when state changes`() {
        assertFalse(RenderPauseTransition.shouldApply(false, false))
        assertTrue(RenderPauseTransition.shouldApply(true, false))
        assertTrue(RenderPauseTransition.shouldApply(false, true))
        assertFalse(RenderPauseTransition.shouldApply(true, true))
    }
}
