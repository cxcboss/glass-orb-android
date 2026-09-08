package com.cxcboss.glassorb.motion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ElasticDragTest {
    @Test
    fun `rubber band stays negative for negative input and stays inside the range`() {
        val result = ElasticDrag.rubberBand(-64f, 64f, 0.62f)

        assertTrue(result < 0f)
        assertTrue(kotlin.math.abs(result) < 64f)
    }

    @Test
    fun `rubber band output is monotonic and asymptotically approaches the range`() {
        val outputs = listOf(0f, 16f, 32f, 64f, 128f, 1_000f).map {
            ElasticDrag.rubberBand(it, 64f, 0.62f)
        }

        outputs.zipWithNext().forEach { (previous, next) ->
            assertTrue(previous <= next)
        }
        assertTrue(outputs.last() < 64f)
        assertTrue(64f - outputs.last() < 0.5f)
    }

    @Test
    fun `collapse progress reaches the full range smoothly`() {
        assertEquals(0f, ElasticDrag.collapseProgress(0f, 48f), 0f)

        val mid = ElasticDrag.collapseProgress(48f, 48f)
        assertTrue(mid > 0f)
        assertTrue(mid < 1f)

        assertEquals(1f, ElasticDrag.collapseProgress(1_000_000f, 48f), 0f)
    }

    @Test
    fun `approach is continuous and never overshoots`() {
        var value = 0f
        repeat(12) {
            val next = ElasticDrag.approach(value, 1f, 1f / 120f, 48f)
            assertTrue(next >= value)
            assertTrue(next <= 1f)
            value = next
        }
        assertTrue(value > 0.9f)
    }

    @Test
    fun `deformation stays within the requested drag and scale bounds`() {
        val deformation = ElasticDrag.deformation(
            offsetXDp = 120f,
            offsetYDp = -90f,
            maxDragDp = 8f,
            maxScaleDelta = 0.016f,
        )

        assertTrue(deformation.scaleX in 0.984f..1.016f)
        assertTrue(deformation.scaleY in 0.984f..1.016f)
        assertTrue(kotlin.math.abs(deformation.topOffsetDp) <= 2f)
        assertTrue(kotlin.math.abs(deformation.offsetXDp) <= 8f)
        assertTrue(kotlin.math.abs(deformation.offsetYDp) <= 8f)
    }
}
