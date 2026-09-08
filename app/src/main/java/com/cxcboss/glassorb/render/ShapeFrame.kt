package com.cxcboss.glassorb.render

import com.cxcboss.glassorb.overlay.OverlayLayout
import com.cxcboss.glassorb.overlay.OverlayState
import kotlin.math.abs
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
            val reboundWidthScale = 1f + abs(reboundSignal) *
                if (config.geometry.expandBelowCapsule) 0.045f else 0.022f
            val reboundHeightScale = 1f - abs(reboundSignal) *
                if (config.geometry.expandBelowCapsule) 0.09f else 0.045f
            val density = displayDensity * OverlayLayout.renderScale(config.geometry, width, height, displayDensity, morph)
            val progress = morph.coerceIn(0f, 1f)
            val expanded = snapshot.state != OverlayState.Collapsed && snapshot.state != OverlayState.Hidden
            val breathing = if (expanded) 1f + config.motion.breathingAmplitude *
                sin(snapshot.timeSeconds * config.motion.breathingSpeed) * progress else 1f
            val scale = breathing * (1f + (config.motion.pressScale - 1f) * snapshot.pressProgress.coerceIn(0f, 1f))
            val center = width / (2f * density) +
                snapshot.capsuleCenterOffsetDp * displayDensity / density * (1f - progress) +
                directionalReboundPx / density
            val preview = ShapeMetrics.interpolate(config.geometry, 0f, center, morph, snapshot.deformation)
            val belowCapsuleTargetPx = config.geometry.capsuleHeightDp * displayDensity +
                OverlayLayout.EXPAND_BELOW_GAP_PX
            val dropPx = if (config.geometry.expandBelowCapsule) {
                belowCapsuleTargetPx * progress * progress * (3f - 2f * progress)
            } else {
                0f
            }
            val top = if (snapshot.preview) (height / density - preview.heightDp * scale) * 0.5f
                else (snapshot.capsuleTopOffsetDp * displayDensity + dropPx + reboundOffsetPx) / density
            val shape = ShapeMetrics.interpolate(config.geometry, top, center, morph, snapshot.deformation)
            return ShapeFrame(morph, density, scale, shape.widthDp * density * scale,
                shape.heightDp * density * scale * reboundHeightScale,
                shape.topDp * density, shape.centerXDp * density)
                .let { frame ->
                    frame.copy(shapeWidth = frame.shapeWidth * reboundWidthScale)
                }
        }
    }
}
