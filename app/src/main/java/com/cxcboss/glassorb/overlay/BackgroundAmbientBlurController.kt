package com.cxcboss.glassorb.overlay

import android.content.Context
import kotlin.math.roundToInt

/**
 * Adds an optional top-to-bottom lower-layer blur beneath the glass ball.
 * Window blur has one radius per window, so a handful of stable horizontal or
 * physical-top strips approximate a smooth 10% -> 0% falloff without changing
 * the pixels' original color.
 */
internal class BackgroundAmbientBlurController(
    context: Context,
    accessibilityWindow: Boolean,
) {
    private val layers = List(STRIP_COUNT) {
        AndroidBackgroundBlurWindow(
            context = context,
            accessibilityWindow = accessibilityWindow,
            rectangular = true,
        )
    }
    private var attached = false
    private var destroyed = false
    private var lastStrips: List<IntRect>? = null
    private var lastMaximumRadius = Int.MIN_VALUE

    fun update(
        enabled: Boolean,
        crossWindowBlurEnabled: Boolean,
        display: RotationDisplayGeometry,
        gradientHeightPx: Int,
        blurPercent: Float,
    ) {
        if (destroyed) return
        val active = enabled && crossWindowBlurEnabled && blurPercent > 0f
        val strips = strips(display, gradientHeightPx)
        if (!ensureAttached(strips)) return
        val maximumRadius = if (active) {
            BackgroundBlurPolicy.radiusPxFromPercent(blurPercent)
        } else {
            0
        }
        // updateBackgroundDim participates in the render path, but these five
        // static top-gradient windows only need work when state, capability or
        // physical display geometry changes. Avoid even a no-op Window call on
        // every 90/120/144 Hz frame.
        if (lastStrips == strips && lastMaximumRadius == maximumRadius) return
        layers.forEachIndexed { index, layer ->
            val radius = (maximumRadius * (1f - index / (STRIP_COUNT - 1f))).roundToInt()
            runCatching { layer.update(strips[index], strips[index], radius) }
                .onFailure { layer.hide() }
        }
        lastStrips = strips
        lastMaximumRadius = maximumRadius
    }

    fun destroy() {
        if (destroyed) return
        layers.forEach { runCatching { it.destroy() } }
        attached = false
        lastStrips = null
        lastMaximumRadius = Int.MIN_VALUE
        destroyed = true
    }

    private fun ensureAttached(strips: List<IntRect>): Boolean {
        if (attached && lastStrips == strips) return true
        if (!attached) {
            val shown = layers.zip(strips).all { (layer, bounds) ->
                runCatching { layer.show(bounds, bounds, 0) }.getOrDefault(false)
            }
            if (!shown) {
                layers.forEach { runCatching { it.destroy() } }
                return false
            }
            attached = true
        }
        return true
    }

    private fun strips(display: RotationDisplayGeometry, gradientHeightPx: Int): List<IntRect> {
        val bounds = display.physicalBounds
        val distance = gradientHeightPx.coerceAtLeast(1).coerceIn(
            1,
            if (display.rotation == RotationCoordinateSpace.ROTATION_90 ||
                display.rotation == RotationCoordinateSpace.ROTATION_270
            ) bounds.width else bounds.height,
        )
        val axisStart: Int
        val axisEnd: Int
        when (display.rotation) {
            RotationCoordinateSpace.ROTATION_180 -> {
                axisStart = bounds.bottom - distance
                axisEnd = bounds.bottom
            }
            RotationCoordinateSpace.ROTATION_90 -> {
                axisStart = bounds.left
                axisEnd = bounds.left + distance
            }
            RotationCoordinateSpace.ROTATION_270 -> {
                axisStart = bounds.right - distance
                axisEnd = bounds.right
            }
            else -> {
                axisStart = bounds.top
                axisEnd = bounds.top + distance
            }
        }
        return (0 until STRIP_COUNT).map { index ->
            val start = interpolate(axisStart, axisEnd, index / STRIP_COUNT.toFloat())
            val end = interpolate(axisStart, axisEnd, (index + 1) / STRIP_COUNT.toFloat())
                .coerceAtLeast(start + 1)
            when (display.rotation) {
                RotationCoordinateSpace.ROTATION_90,
                RotationCoordinateSpace.ROTATION_270,
                -> IntRect(start, bounds.top, end, bounds.bottom)
                else -> IntRect(bounds.left, start, bounds.right, end)
            }
        }
    }

    private fun interpolate(start: Int, end: Int, fraction: Float): Int =
        (start + (end - start) * fraction).roundToInt()

    private companion object {
        const val STRIP_COUNT = 5
    }
}
