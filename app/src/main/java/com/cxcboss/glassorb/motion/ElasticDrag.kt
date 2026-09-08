package com.cxcboss.glassorb.motion

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sign

data class DragDeformation(
    val offsetXDp: Float,
    val offsetYDp: Float,
    val scaleX: Float,
    val scaleY: Float,
    val topOffsetDp: Float,
)

object ElasticDrag {
    private const val DEFAULT_RESISTANCE = 0.62f
    private const val TOP_OFFSET_FACTOR = 0.25f
    private const val TOP_OFFSET_LIMIT_DP = 2f

    fun rubberBand(valueDp: Float, rangeDp: Float, resistance: Float): Float {
        if (!valueDp.isFinite() || !rangeDp.isFinite() || !resistance.isFinite()) return 0f
        if (rangeDp <= 0f || resistance <= 0f) return 0f

        val magnitude = abs(valueDp)
        if (magnitude == 0f) return 0f

        val compressed = rangeDp * (1f - exp(-magnitude * resistance / rangeDp))
        return sign(valueDp) * compressed
    }

    fun collapseProgress(rawUpwardDp: Float, collapseRangeDp: Float): Float {
        if (!rawUpwardDp.isFinite() || !collapseRangeDp.isFinite()) return 0f
        val distance = rawUpwardDp.coerceAtLeast(0f)
        val range = max(collapseRangeDp, 0.0001f)
        return (1f - exp(-distance / range)).coerceIn(0f, 1f)
    }

    /** Frame-rate independent easing used while a finger is still down. */
    fun approach(current: Float, target: Float, deltaSeconds: Float, responsePerSecond: Float): Float {
        if (!current.isFinite() || !target.isFinite() || !deltaSeconds.isFinite() || !responsePerSecond.isFinite()) {
            return target.coerceIn(0f, 1f)
        }
        if (deltaSeconds <= 0f || responsePerSecond <= 0f) return current
        val amount = (1f - exp(-responsePerSecond * deltaSeconds)).coerceIn(0f, 1f)
        return current + (target - current) * amount
    }

    fun deformation(
        offsetXDp: Float,
        offsetYDp: Float,
        maxDragDp: Float,
        maxScaleDelta: Float,
    ): DragDeformation {
        if (!offsetXDp.isFinite() || !offsetYDp.isFinite() || !maxDragDp.isFinite() || !maxScaleDelta.isFinite()) {
            return DragDeformation(0f, 0f, 1f, 1f, 0f)
        }

        if (maxDragDp <= 0f) return DragDeformation(0f, 0f, 1f, 1f, 0f)
        val dragLimit = maxDragDp
        val scaleDelta = maxScaleDelta.coerceIn(0f, 0.02f)
        val x = rubberBand(offsetXDp, dragLimit, DEFAULT_RESISTANCE)
        val y = rubberBand(offsetYDp, dragLimit, DEFAULT_RESISTANCE)
        val xRatio = (abs(x) / dragLimit).coerceIn(0f, 1f)
        val yRatio = (abs(y) / dragLimit).coerceIn(0f, 1f)
        val scaleX = 1f + scaleDelta * (xRatio - yRatio * 0.55f)
        val scaleY = 1f + scaleDelta * (yRatio - xRatio * 0.55f)
        val topOffsetDp = (-y * TOP_OFFSET_FACTOR).coerceIn(-TOP_OFFSET_LIMIT_DP, TOP_OFFSET_LIMIT_DP)

        return DragDeformation(
            offsetXDp = x,
            offsetYDp = y,
            scaleX = scaleX,
            scaleY = scaleY,
            topOffsetDp = topOffsetDp,
        )
    }
}
