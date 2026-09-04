package com.cxcboss.glassorb.overlay

import com.cxcboss.glassorb.model.MotionConfig
import com.cxcboss.glassorb.motion.AnalyticSpring
import com.cxcboss.glassorb.motion.DragDeformation
import com.cxcboss.glassorb.motion.ElasticDrag
import kotlin.math.abs

data class OverlayAnchor(
    val topDp: Float,
    val centerXDp: Float,
)

data class OverlaySpringSeed(
    val value: Float,
    val velocity: Float,
    val target: Float,
)

class OverlayWindowMotion(
    motion: MotionConfig,
    deformationResponse: Float = motion.deformResponse,
    deformationDamping: Float = motion.deformDamping,
) {
    private var motion: MotionConfig = motion
    private var dragTargetXDp = 0f
    private var dragTargetYDp = 0f

    val dragXSpring = AnalyticSpring(0f, deformationResponse, deformationDamping)
    val dragYSpring = AnalyticSpring(0f, deformationResponse, deformationDamping)

    var windowExpanded = false
        private set
    var pendingCollapsedResizeFrames = 0
        private set
    var anchorTopDp = 0f
        private set
    var anchorCenterXDp = 0f
        private set
    var collapsePull = 0f
        private set
    var deformation = DragDeformation(0f, 0f, 1f, 1f, 0f)
        private set

    fun updateMotion(motion: MotionConfig) {
        this.motion = motion
        dragXSpring.configure(motion.deformResponse, motion.deformDamping)
        dragYSpring.configure(motion.deformResponse, motion.deformDamping)
        deformation = currentDeformation(dragXSpring.value, dragYSpring.value)
    }

    fun setExpandedWindow(anchor: OverlayAnchor) {
        windowExpanded = true
        pendingCollapsedResizeFrames = 0
        anchorTopDp = anchor.topDp
        anchorCenterXDp = anchor.centerXDp
    }

    fun setCollapsedWindow() {
        windowExpanded = false
        pendingCollapsedResizeFrames = 0
        anchorTopDp = 0f
        anchorCenterXDp = 0f
        clearGesture()
    }

    fun onSwipeMove(deltaXDp: Float, deltaYDp: Float) {
        val collapseDeltaDp = (-deltaYDp).coerceAtLeast(0f)
        collapsePull = ElasticDrag.collapseProgress(collapseDeltaDp, motion.collapseRangeDp)

        val useDeformation = deltaYDp >= 0f || abs(deltaXDp) > abs(deltaYDp)
        dragTargetXDp = if (useDeformation) deltaXDp else 0f
        dragTargetYDp = if (useDeformation) deltaYDp.coerceAtLeast(0f) else 0f
        dragXSpring.target = dragTargetXDp
        dragYSpring.target = dragTargetYDp
        deformation = currentDeformation(dragTargetXDp, dragTargetYDp)
    }

    fun release(
        decision: SwipeDecision,
        velocityXDpPerSecond: Float,
        velocityYDpPerSecond: Float,
    ): OverlaySpringSeed {
        val visualMorph = (1f - collapsePull).coerceIn(0f, 1f)
        val morphVelocity = (-velocityYDpPerSecond / motion.collapseRangeDp).coerceIn(-12f, 12f)

        dragXSpring.seed(dragTargetXDp, velocityXDpPerSecond, 0f)
        dragYSpring.seed(dragTargetYDp, velocityYDpPerSecond.coerceAtLeast(0f), 0f)
        dragTargetXDp = 0f
        dragTargetYDp = 0f
        collapsePull = 0f

        return OverlaySpringSeed(
            value = visualMorph,
            velocity = morphVelocity,
            target = if (decision == SwipeDecision.Collapse) 0f else 1f,
        )
    }

    fun onCollapseSettled() {
        collapsePull = 0f
        dragTargetXDp = 0f
        dragTargetYDp = 0f
        dragXSpring.snapTo(0f)
        dragYSpring.snapTo(0f)
        deformation = DragDeformation(0f, 0f, 1f, 1f, 0f)
        pendingCollapsedResizeFrames = 1
        windowExpanded = true
    }

    fun cancelGesture() {
        clearGesture()
    }

    fun advanceFrame(): Boolean {
        if (pendingCollapsedResizeFrames <= 0) return false
        pendingCollapsedResizeFrames -= 1
        if (pendingCollapsedResizeFrames > 0) return false

        windowExpanded = false
        anchorTopDp = 0f
        anchorCenterXDp = 0f
        return true
    }

    fun step(deltaSeconds: Float) {
        dragXSpring.step(deltaSeconds)
        dragYSpring.step(deltaSeconds)
        deformation = currentDeformation(dragXSpring.value, dragYSpring.value)
    }

    private fun clearGesture() {
        dragTargetXDp = 0f
        dragTargetYDp = 0f
        collapsePull = 0f
        dragXSpring.snapTo(0f)
        dragYSpring.snapTo(0f)
        deformation = DragDeformation(0f, 0f, 1f, 1f, 0f)
    }

    private fun currentDeformation(offsetXDp: Float, offsetYDp: Float): DragDeformation = ElasticDrag.deformation(
        offsetXDp = offsetXDp,
        offsetYDp = offsetYDp,
        maxDragDp = motion.deformLimitDp,
        maxScaleDelta = motion.deformScaleDelta,
    )
}
