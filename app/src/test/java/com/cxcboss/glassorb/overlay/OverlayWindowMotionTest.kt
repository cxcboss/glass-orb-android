package com.cxcboss.glassorb.overlay

import com.cxcboss.glassorb.model.MotionConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayWindowMotionTest {
    private val motionConfig = MotionConfig()

    @Test
    fun `collapse settle keeps the stable expanded canvas and clears deformation`() {
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
        assertEquals(10f, motion.anchorTopDp, 0f)
        assertEquals(64f, motion.anchorCenterXDp, 0f)
        assertEquals(0f, motion.deformation.offsetXDp, 0f)
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
        assertTrue(release.velocity < 0f)
        assertEquals(0f, motion.collapsePull, 0f)
    }

    @Test
    fun `horizontal drag stays tiny and does not collapse`() {
        val motion = OverlayWindowMotion(motionConfig)
        motion.onSwipeMove(300f, -10f)
        val before = motion.deformation
        motion.step(1f / 60f)
        assertEquals(before, motion.deformation)
        assertEquals(0f, motion.collapsePull, 0f)
        assertTrue(before.offsetXDp <= 8f)
        assertTrue(before.scaleX > 1f && before.scaleX <= 1.02f)
        assertTrue(before.scaleY < 1f)
        motion.release(SwipeDecision.Restore, 0f, 0f)
        repeat(180) { motion.step(1f / 60f) }
        assertEquals(0f, motion.deformation.offsetXDp, 0.001f)
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
