package com.cxcboss.glassorb.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FullscreenCapsuleMotionTest {
    @Test
    fun `hide first reaches square stage before final shrink`() {
        val early = FullscreenCapsuleMotion.frame(FullscreenCapsulePhase.Hiding, 0.9f)
        val square = FullscreenCapsuleMotion.frame(FullscreenCapsulePhase.Hiding, 0.5f)
        val end = FullscreenCapsuleMotion.frame(FullscreenCapsulePhase.Hiding, 0f)

        assertTrue(early.squareProgress > 0f)
        assertTrue(square.squareProgress > 0.9f)
        assertEquals(1f, end.squareProgress, 0f)
        assertTrue(end.scale < square.scale)
    }

    @Test
    fun `show starts as a square and restores the capsule aspect ratio last`() {
        val start = FullscreenCapsuleMotion.frame(FullscreenCapsulePhase.Showing, 0f)
        val square = FullscreenCapsuleMotion.frame(FullscreenCapsulePhase.Showing, 0.56f)
        val end = FullscreenCapsuleMotion.frame(FullscreenCapsulePhase.Showing, 1f)

        assertEquals(1f, start.squareProgress, 0f)
        assertTrue(square.squareProgress > 0.99f)
        assertEquals(0f, end.squareProgress, 0f)
        assertTrue(end.scale > square.scale)
    }
}
