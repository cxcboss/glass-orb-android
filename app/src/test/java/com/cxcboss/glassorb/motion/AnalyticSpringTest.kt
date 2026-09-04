package com.cxcboss.glassorb.motion

import org.junit.Assert.assertEquals
import org.junit.Test

class AnalyticSpringTest {
    @Test
    fun `opening spring matches the independently captured reference trace`() {
        val samples = listOf(
            0.10f to 0.522656592f,
            0.20f to 0.950888505f,
            0.42f to 1.014303303f,
            0.80f to 0.999909991f,
        )

        samples.forEach { (time, expected) ->
            val spring = AnalyticSpring(value = 0f, response = 0.42f, dampingRatio = 0.72f)
            spring.target = 1f
            spring.step(time)
            assertEquals("sample at $time seconds", expected, spring.value, 0.0001f)
        }
    }

    @Test
    fun `closing spring crosses zero briefly instead of pausing at the capsule`() {
        val spring = AnalyticSpring(value = 1f, response = 0.30f, dampingRatio = 0.78f)
        spring.target = 0f

        spring.step(0.30f)

        assertEquals(-0.011824474f, spring.value, 0.0001f)
    }
}
