package com.cxcboss.glassorb.overlay

import com.cxcboss.glassorb.model.MotionConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayWindowMotionTest {
    private val motionConfig = MotionConfig()

    @Test
    fun `collapse settle keeps the expanded window alive for one more frame`() {
        val motion = OverlayWindowMotion(
            motion = motionConfig,
            deformationResponse = motionConfig.deformResponse,
            deformationDamping = motionConfig.deformDamping,
        )

        motion.setExpandedWindow(
            OverlayAnchor(topDp = 10f, centerXDp = 64f),
        )
        motion.onCollapseSettled()

        assertTrue(motion.windowExpanded)
        assertEquals(1, motion.pendingCollapsedResizeFrames)

        val shouldResizeOnNextFrame = motion.advanceFrame()

        assertTrue(shouldResizeOnNextFrame)
        assertFalse(motion.windowExpanded)
        assertEquals(0, motion.pendingCollapsedResizeFrames)
        assertEquals(0f, motion.anchorTopDp, 0f)
        assertEquals(0f, motion.anchorCenterXDp, 0f)
    }

    @Test
    fun `restore release seeds the morph spring back toward expanded state`() {
        val motion = OverlayWindowMotion(
            motion = motionConfig,
            deformationResponse = motionConfig.deformResponse,
            deformationDamping = motionConfig.deformDamping,
        )

        motion.setExpandedWindow(OverlayAnchor(topDp = 12f, centerXDp = 60f))
        motion.onSwipeMove(deltaXDp = 0f, deltaYDp = -30f)

        val release = motion.release(
            decision = SwipeDecision.Restore,
            velocityXDpPerSecond = 0f,
            velocityYDpPerSecond = -120f,
        )

        assertEquals(1f, release.target, 0f)
        assertTrue(release.value < 1f)
        assertEquals(0f, motion.collapsePull, 0f)
        assertEquals(0, motion.pendingCollapsedResizeFrames)
    }

    @Test
    fun `cancel cleanup clears collapse pull and deformation targets`() {
        val motion = OverlayWindowMotion(
            motion = motionConfig,
            deformationResponse = motionConfig.deformResponse,
            deformationDamping = motionConfig.deformDamping,
        )

        motion.onSwipeMove(deltaXDp = 18f, deltaYDp = 26f)
        motion.cancelGesture()
        motion.step(1f / 60f)

        assertEquals(0f, motion.collapsePull, 0f)
        assertEquals(0f, motion.deformation.offsetXDp, 0.0001f)
        assertEquals(0f, motion.deformation.offsetYDp, 0.0001f)
        assertEquals(1f, motion.deformation.scaleX, 0.0001f)
        assertEquals(1f, motion.deformation.scaleY, 0.0001f)
    }
}
