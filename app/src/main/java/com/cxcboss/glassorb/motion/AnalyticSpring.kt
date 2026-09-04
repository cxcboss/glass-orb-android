package com.cxcboss.glassorb.motion

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

class AnalyticSpring(
    value: Float,
    response: Float,
    dampingRatio: Float,
) {
    var value: Float = value
        private set
    var velocity: Float = 0f
        private set
    var target: Float = value

    private var parameters = parameters(response, dampingRatio)

    fun configure(response: Float, dampingRatio: Float) {
        parameters = parameters(response, dampingRatio)
    }

    fun snapTo(newValue: Float) {
        value = newValue
        velocity = 0f
        target = newValue
    }

    fun step(deltaSeconds: Float): Float {
        val elapsed = max(deltaSeconds, 0f).toDouble()
        val offset = (value - target).toDouble()
        val currentVelocity = velocity.toDouble()
        if (elapsed <= 0.0 || (offset == 0.0 && currentVelocity == 0.0)) return value

        val omegaSquared = parameters.stiffness
        val naturalAngularFrequency = parameters.naturalAngularFrequency
        val decay = parameters.damping / 2.0
        val displacement: Double
        val nextVelocity: Double

        if (decay < naturalAngularFrequency) {
            val frequency = sqrt(omegaSquared - decay * decay)
            val envelope = exp(-decay * elapsed)
            val cosine = cos(frequency * elapsed)
            val sine = sin(frequency * elapsed)
            val phase = (currentVelocity + decay * offset) / frequency
            val oscillation = offset * cosine + phase * sine
            displacement = envelope * oscillation
            nextVelocity = envelope * (
                -decay * oscillation + (-offset * frequency * sine + phase * frequency * cosine)
                )
        } else if (naturalAngularFrequency < decay) {
            val frequency = sqrt(decay * decay - omegaSquared)
            val upper = -decay + frequency
            val lower = -decay - frequency
            val upperWeight = (currentVelocity - lower * offset) / (upper - lower)
            val lowerWeight = offset - upperWeight
            val upperEnvelope = exp(upper * elapsed)
            val lowerEnvelope = exp(lower * elapsed)
            displacement = upperWeight * upperEnvelope + lowerWeight * lowerEnvelope
            nextVelocity = upperWeight * upper * upperEnvelope + lowerWeight * lower * lowerEnvelope
        } else {
            val envelope = exp(-decay * elapsed)
            val phase = currentVelocity + decay * offset
            val oscillation = offset + phase * elapsed
            displacement = envelope * oscillation
            nextVelocity = envelope * (phase - decay * oscillation)
        }

        value = (target + displacement).toFloat()
        velocity = nextVelocity.toFloat()
        return value
    }

    private data class Parameters(
        val stiffness: Double,
        val damping: Double,
        val naturalAngularFrequency: Double,
    )

    private companion object {
        private fun parameters(response: Float, dampingRatio: Float): Parameters {
            val safeResponse = max(response, 0.0001f).toDouble()
            val safeDamping = max(dampingRatio, 0f).toDouble()
            val omega = 2.0 * PI / safeResponse
            return Parameters(
                stiffness = omega * omega,
                damping = 2.0 * safeDamping * omega,
                naturalAngularFrequency = omega,
            )
        }
    }
}
