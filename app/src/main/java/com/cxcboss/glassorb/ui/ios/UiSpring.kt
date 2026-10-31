package com.cxcboss.glassorb.ui.ios

import android.animation.ValueAnimator
import android.view.Choreographer
import com.cxcboss.glassorb.motion.AnalyticSpring
import kotlin.math.abs

/**
 * Frame-driven spring for small UI values (press scale, thumb travel, tab
 * bubble). It reuses the overlay's closed-form [AnalyticSpring], so settings
 * motion shares the same feel as the glass orb and stays stable at any
 * refresh rate. Work stops as soon as the value settles.
 */
internal class UiSpring(
    response: Float,
    dampingRatio: Float,
    initial: Float,
    private val onUpdate: (value: Float, velocity: Float) -> Unit,
) {
    private val spring = AnalyticSpring(initial, response, dampingRatio)
    private val choreographer = Choreographer.getInstance()
    private var lastFrameNanos = 0L
    private var running = false
    private val callback = Choreographer.FrameCallback { now -> step(now) }

    val value: Float get() = spring.value
    val velocity: Float get() = spring.velocity
    val target: Float get() = spring.target

    fun configure(response: Float, dampingRatio: Float) = spring.configure(response, dampingRatio)

    fun animateTo(target: Float) {
        if (!ValueAnimator.areAnimatorsEnabled()) {
            snapTo(target)
            return
        }
        spring.target = target
        if (!running) {
            running = true
            lastFrameNanos = 0L
            choreographer.postFrameCallback(callback)
        }
    }

    fun snapTo(value: Float) {
        spring.snapTo(value)
        if (running) {
            choreographer.removeFrameCallback(callback)
            running = false
        }
        onUpdate(value, 0f)
    }

    fun cancel() {
        if (running) choreographer.removeFrameCallback(callback)
        running = false
    }

    private fun step(now: Long) {
        val delta = if (lastFrameNanos == 0L) 0f else ((now - lastFrameNanos) / 1_000_000_000.0).toFloat()
        lastFrameNanos = now
        spring.step(delta.coerceIn(0f, 0.05f))
        if (abs(spring.value - spring.target) < SETTLE_DISTANCE && abs(spring.velocity) < SETTLE_VELOCITY) {
            spring.snapTo(spring.target)
            running = false
            onUpdate(spring.value, 0f)
            return
        }
        onUpdate(spring.value, spring.velocity)
        choreographer.postFrameCallback(callback)
    }

    private companion object {
        const val SETTLE_DISTANCE = 0.0006f
        const val SETTLE_VELOCITY = 0.01f
    }
}
