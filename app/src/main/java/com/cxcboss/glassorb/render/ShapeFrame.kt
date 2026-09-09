package com.cxcboss.glassorb.render

import com.cxcboss.glassorb.overlay.OverlayLayout
import com.cxcboss.glassorb.overlay.OverlayState
import kotlin.math.sin
import kotlin.math.tanh

/** Shared physical geometry for rendering and the window input region. */
internal data class ShapeFrame(
    val morph: Float, val density: Float, val visualScale: Float,
    val shapeWidth: Float, val shapeHeight: Float, val top: Float, val centerX: Float,
) {
    val centerY get() = top + shapeHeight * 0.5f

    companion object {
        fun from(snapshot: RenderSnapshot, width: Int, height: Int, displayDensity: Float): ShapeFrame {
            val config = snapshot.config
            val raw = snapshot.springProgress - snapshot.collapsePull.coerceIn(0f, 1f)
            val bounce = config.motion.closeBounce
            val morph = if (raw >= 0f) raw else if (bounce <= 0f) 0f else -bounce * tanh(-raw / bounce)
            val overshootRatio = if (snapshot.state == OverlayState.Collapsing && raw < 0f && bounce > 0f) {
                val normalized = (-raw / bounce).coerceIn(0f, 1f)
                normalized * normalized * (3f - 2f * normalized)
            } else {
                0f
            }
            val reboundSignal = if (snapshot.state == OverlayState.Collapsing) {
                (overshootRatio + snapshot.collapseRebound * 2.2f +
                    snapshot.collapseSecondaryRebound * 1.6f).coerceIn(-0.85f, 1.2f)
            } else {
                0f
            }
            val speedFactor = (snapshot.collapseVelocityDpPerSecond / 1_200f).coerceIn(0f, 1f)
            val reboundAmplitudePx = if (config.geometry.expandBelowCapsule) 8f else 4f
            val reboundSpeedBoost = if (config.geometry.expandBelowCapsule) 0.9f else 0.45f
            val reboundOffsetPx = -reboundAmplitudePx * reboundSignal *
                (1f + speedFactor * reboundSpeedBoost)
            val directionalReboundPx = snapshot.collapseDirection.coerceIn(-1, 1) *
                (if (config.geometry.expandBelowCapsule) 6f else 3f) * reboundSignal *
                (1f + speedFactor * 0.7f)
            val progress = morph.coerceIn(0f, 1f)
            val staged = stagedMotion(snapshot, progress, displayDensity)
            val visualMorph = staged?.morph ?: morph
            val stageScale = staged?.scale ?: 1f
            val density = displayDensity * OverlayLayout.renderScale(
                config.geometry, width, height, displayDensity, visualMorph,
            )
            // Once the orb has mostly become a capsule, use the same spring
            // signal for a soft squeeze: shorter horizontally, taller
            // vertically, then naturally back to 1x as the spring settles.
            // Squaring keeps the pulse non-negative while preserving a smooth
            // zero crossing for the layered rebound oscillation.
            val capsulePresence = if (snapshot.state == OverlayState.Collapsing) {
                smoothstep(0.25f, 0.92f, 1f - progress)
            } else {
                0f
            }
            val springPulse = (reboundSignal * reboundSignal).coerceIn(0f, 1f)
            val capsuleDeformation = capsulePresence * springPulse
            val reboundWidthScale = 1f - capsuleDeformation *
                if (config.geometry.expandBelowCapsule) 0.12f else 0.085f
            val reboundHeightScale = 1f + capsuleDeformation *
                if (config.geometry.expandBelowCapsule) 0.20f else 0.14f
            val expanded = snapshot.state != OverlayState.Collapsed &&
                snapshot.state != OverlayState.Hidden && snapshot.state != OverlayState.Collapsing
            val breathing = if (expanded) 1f + config.motion.breathingAmplitude *
                sin(snapshot.timeSeconds * config.motion.breathingSpeed) * visualMorph.coerceIn(0f, 1f) else 1f
            val scale = breathing * (1f + (config.motion.pressScale - 1f) * snapshot.pressProgress.coerceIn(0f, 1f))
            val center = width / (2f * density) +
                snapshot.capsuleCenterOffsetDp * displayDensity / density * (1f - visualMorph) +
                directionalReboundPx / density
            val preview = ShapeMetrics.interpolate(config.geometry, 0f, center, visualMorph, snapshot.deformation)
            val belowCapsuleTargetPx = config.geometry.capsuleHeightDp * displayDensity +
                OverlayLayout.EXPAND_BELOW_GAP_PX
            val dropPx = if (staged == null && config.geometry.expandBelowCapsule) {
                belowCapsuleTargetPx * progress * progress * (3f - 2f * progress)
            } else {
                0f
            }
            val top = if (snapshot.preview) (height / density - preview.heightDp * scale) * 0.5f
                else (snapshot.capsuleTopOffsetDp * displayDensity + dropPx + reboundOffsetPx) / density
            val shape = ShapeMetrics.interpolate(config.geometry, top, center, visualMorph, snapshot.deformation)
            if (staged == null) {
                return ShapeFrame(visualMorph, density, scale, shape.widthDp * density * scale,
                    shape.heightDp * density * scale * reboundHeightScale,
                    shape.topDp * density, shape.centerXDp * density)
                    .let { frame ->
                        frame.copy(shapeWidth = frame.shapeWidth * reboundWidthScale)
                    }
            }

            val stagedHeightDp = shape.heightDp * stageScale * scale * reboundHeightScale
            val stagedWidthDp = shape.widthDp * stageScale * scale * reboundWidthScale
            val stagedCenterYDp = staged.centerYDp +
                if (snapshot.state == OverlayState.Collapsing) reboundOffsetPx / density else 0f
            return ShapeFrame(
                morph = visualMorph,
                density = density,
                visualScale = scale,
                shapeWidth = stagedWidthDp * density,
                shapeHeight = stagedHeightDp * density,
                top = (stagedCenterYDp - stagedHeightDp * 0.5f) * density,
                centerX = shape.centerXDp * density,
            )
        }

        private data class StagedMotion(
            val morph: Float,
            val scale: Float,
            val centerYDp: Float,
        )

        private fun stagedMotion(
            snapshot: RenderSnapshot,
            progress: Float,
            displayDensity: Float,
        ): StagedMotion? {
            if (snapshot.preview || !snapshot.config.geometry.expandBelowCapsule) return null
            val capsuleTopDp = snapshot.capsuleTopOffsetDp
            val capsuleCenterYDp = capsuleTopDp + snapshot.config.geometry.capsuleHeightDp * 0.5f
            val orbCenterYDp = capsuleTopDp + snapshot.config.geometry.capsuleHeightDp +
                OverlayLayout.EXPAND_BELOW_GAP_PX / displayDensity +
                snapshot.config.geometry.orbDiameterDp * 0.5f

            return when (snapshot.state) {
                OverlayState.Expanding -> when {
                    progress < EXPAND_POINT_END -> {
                        val squeeze = smoothstep(0f, EXPAND_POINT_END, progress)
                        StagedMotion(0f, 1f - squeeze, capsuleCenterYDp)
                    }
                    progress < EXPAND_MOVE_END -> {
                        val move = smoothstep(EXPAND_POINT_END, EXPAND_MOVE_END, progress)
                        StagedMotion(0f, 0f, lerp(capsuleCenterYDp, orbCenterYDp, move))
                    }
                    else -> {
                        val reveal = smoothstep(EXPAND_MOVE_END, 1f, progress)
                        StagedMotion(reveal, reveal, orbCenterYDp)
                    }
                }

                OverlayState.Collapsing -> {
                    val closeProgress = 1f - progress
                    when {
                        closeProgress < COLLAPSE_POINT_END -> {
                            val squeeze = smoothstep(0f, COLLAPSE_POINT_END, closeProgress)
                            StagedMotion(1f, 1f - squeeze, orbCenterYDp)
                        }
                        closeProgress < COLLAPSE_MOVE_END -> {
                            val move = smoothstep(COLLAPSE_POINT_END, COLLAPSE_MOVE_END, closeProgress)
                            StagedMotion(0f, 0f, lerp(orbCenterYDp, capsuleCenterYDp, move))
                        }
                        else -> {
                            val reveal = smoothstep(COLLAPSE_MOVE_END, 1f, closeProgress)
                            StagedMotion(0f, reveal, capsuleCenterYDp)
                        }
                    }
                }

                else -> null
            }
        }

        private fun smoothstep(edge0: Float, edge1: Float, value: Float): Float {
            val span = (edge1 - edge0).coerceAtLeast(0.0001f)
            val t = ((value - edge0) / span).coerceIn(0f, 1f)
            return t * t * (3f - 2f * t)
        }

        private fun lerp(start: Float, end: Float, fraction: Float): Float =
            start + (end - start) * fraction

        private const val EXPAND_POINT_END = 0.30f
        private const val EXPAND_MOVE_END = 0.56f
        private const val COLLAPSE_POINT_END = 0.34f
        private const val COLLAPSE_MOVE_END = 0.62f
    }
}
