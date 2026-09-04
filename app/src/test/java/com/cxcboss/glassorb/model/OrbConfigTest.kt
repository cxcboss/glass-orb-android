package com.cxcboss.glassorb.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OrbConfigTest {
    @Test
    fun `defaults preserve the approved reference tuning`() {
        val config = OrbConfig.reference()

        assertEquals(118f, config.geometry.capsuleWidthDp, 0f)
        assertEquals(34f, config.geometry.capsuleHeightDp, 0f)
        assertEquals(128f, config.geometry.orbDiameterDp, 0f)
        assertEquals(0.72f, config.glass.highlightAmount, 0f)
        assertEquals(1.6f, config.glass.causticAmount, 0f)
        assertEquals(30_000f, config.wave.bandFill, 0f)
        assertEquals(0.055f, config.dots.glow, 0f)
        assertEquals(48f, config.motion.collapseRangeDp, 0f)
        assertEquals(64f, config.motion.dragRangeDp, 0f)
        assertEquals(0.62f, config.motion.dragResistance, 0f)
        assertEquals(8f, config.motion.deformLimitDp, 0f)
        assertEquals(0.016f, config.motion.deformScaleDelta, 0f)
        assertEquals(0.24f, config.motion.deformResponse, 0f)
        assertEquals(0.68f, config.motion.deformDamping, 0f)
        assertEquals(120, config.performance.collapsedFps)
        assertEquals(120, config.performance.expandedFps)
    }

    @Test
    fun `normalized clamps malformed imported values without changing valid fields`() {
        val config = OrbConfig.reference().copy(
            geometry = OrbConfig.reference().geometry.copy(
                capsuleWidthDp = -40f,
                capsuleHeightDp = 500f,
                orbDiameterDp = 170f,
            ),
            glass = OrbConfig.reference().glass.copy(curvature = 5f),
            motion = OrbConfig.reference().motion.copy(
                collapseRangeDp = -3f,
                dragRangeDp = 4_000f,
                dragResistance = Float.NaN,
                deformLimitDp = 200f,
                deformScaleDelta = -1f,
                deformResponse = Float.POSITIVE_INFINITY,
                deformDamping = -10f,
            ),
        ).normalized()

        assertEquals(24f, config.geometry.capsuleWidthDp, 0f)
        assertEquals(64f, config.geometry.capsuleHeightDp, 0f)
        assertEquals(170f, config.geometry.orbDiameterDp, 0f)
        assertEquals(1f, config.glass.curvature, 0f)
        assertEquals(24f, config.motion.collapseRangeDp, 0f)
        assertEquals(160f, config.motion.dragRangeDp, 0f)
        assertEquals(0.62f, config.motion.dragResistance, 0f)
        assertEquals(8f, config.motion.deformLimitDp, 0f)
        assertEquals(0f, config.motion.deformScaleDelta, 0f)
        assertEquals(0.24f, config.motion.deformResponse, 0f)
        assertEquals(0.1f, config.motion.deformDamping, 0f)
    }

    @Test
    fun `json round trip keeps a customized snapshot`() {
        val original = OrbConfig.bright().copy(
            geometry = OrbConfig.bright().geometry.copy(horizontalOffsetDp = 42f),
            wave = OrbConfig.bright().wave.copy(hueShiftDegrees = -35f),
            motion = OrbConfig.bright().motion.copy(
                collapseRangeDp = 52f,
                dragRangeDp = 72f,
                dragResistance = 0.74f,
                deformLimitDp = 7f,
                deformScaleDelta = 0.012f,
                deformResponse = 0.31f,
                deformDamping = 0.71f,
            ),
        )

        val restored = OrbConfigJson.decode(OrbConfigJson.encode(original)).getOrThrow()

        assertEquals(original, restored)
    }

    @Test
    fun `invalid json never returns a replacement config`() {
        val result = OrbConfigJson.decode("{ definitely-not-json")

        assertTrue(result.isFailure)
    }

    @Test
    fun `partial future json uses defaults ignores unknown fields and clamps values`() {
        val restored = OrbConfigJson.decode(
            """{"schemaVersion":99,"geometry":{"capsuleWidthDp":999,"futureGeometry":7},"futureRoot":true,"motion":{"dragResistance":1.5,"futureMotion":4}}""",
        ).getOrThrow()

        assertEquals(OrbConfig.CURRENT_SCHEMA_VERSION, restored.schemaVersion)
        assertEquals(220f, restored.geometry.capsuleWidthDp, 0f)
        assertEquals(34f, restored.geometry.capsuleHeightDp, 0f)
        assertEquals(OrbConfig.reference().glass, restored.glass)
        assertEquals(1.5f, restored.motion.dragResistance, 0f)
    }
}
