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
    private var collapsePullTarget = 0f
    private var tracking = false

    val dragXSpring = AnalyticSpring(0f, deformationResponse, deformationDamping)
    val dragYSpring = AnalyticSpring(0f, deformationResponse, deformationDamping)

    var windowExpanded = false
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
        anchorTopDp = anchor.topDp
        anchorCenterXDp = anchor.centerXDp
    }

    fun setCollapsedWindow() {
        windowExpanded = false
        anchorTopDp = 0f
        anchorCenterXDp = 0f
        clearGesture()
    }

    fun onSwipeMove(deltaXDp: Float, deltaYDp: Float) {
        tracking = true
        val closingDirection = deltaYDp < 0f && abs(deltaYDp) > abs(deltaXDp)
        val collapseDeltaDp = if (closingDirection) -deltaYDp else 0f
        collapsePullTarget = ElasticDrag.collapseProgress(collapseDeltaDp, motion.collapseRangeDp)

        dragTargetXDp = if (!closingDirection) ElasticDrag.rubberBand(deltaXDp, motion.dragRangeDp, motion.dragResistance) else 0f
        dragTargetYDp = if (!closingDirection) ElasticDrag.rubberBand(deltaYDp, motion.dragRangeDp, motion.dragResistance) else 0f
        dragXSpring.target = dragTargetXDp
        dragYSpring.target = dragTargetYDp
    }

    fun release(
        decision: SwipeDecision,
        velocityXDpPerSecond: Float,
        velocityYDpPerSecond: Float,
    ): OverlaySpringSeed {
        val visualMorph = (1f - collapsePull).coerceIn(0f, 1f)
        val morphVelocity = if (collapsePull > 0f) {
            (velocityYDpPerSecond * visualMorph / motion.collapseRangeDp).coerceIn(-8f, 8f)
        } else 0f
        tracking = false

        dragXSpring.seed(dragXSpring.value, if (collapsePull > 0f) 0f else velocityXDpPerSecond.coerceIn(-80f, 80f), 0f)
        dragYSpring.seed(dragYSpring.value, if (collapsePull > 0f) 0f else velocityYDpPerSecond.coerceIn(-80f, 80f), 0f)
        dragTargetXDp = 0f
        dragTargetYDp = 0f
        collapsePullTarget = 0f
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
        windowExpanded = true
    }

    fun cancelGesture() {
        clearGesture()
    }

    fun step(deltaSeconds: Float) {
        if (tracking) {
            // MotionEvent frequency is device/OEM dependent. Ease toward the
            // latest finger target on the render clock so a fast gesture cannot
            // jump several morph states between two rendered frames.
            collapsePull = ElasticDrag.approach(
                current = collapsePull,
                target = collapsePullTarget,
                deltaSeconds = deltaSeconds,
                responsePerSecond = TRACKING_RESPONSE_PER_SECOND,
            ).coerceIn(0f, 1f)
            dragXSpring.target = dragTargetXDp
            dragYSpring.target = dragTargetYDp
        }
        dragXSpring.step(deltaSeconds)
        dragYSpring.step(deltaSeconds)
        deformation = currentDeformation(dragXSpring.value, dragYSpring.value)
    }

    private fun clearGesture() {
        tracking = false
        dragTargetXDp = 0f
        dragTargetYDp = 0f
        collapsePullTarget = 0f
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

    private companion object {
        const val TRACKING_RESPONSE_PER_SECOND = 48f
    }
}
