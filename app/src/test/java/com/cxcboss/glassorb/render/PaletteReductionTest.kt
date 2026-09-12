package com.cxcboss.glassorb.render

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PaletteReductionTest {
    @Test
    fun paletteReductionShaderAssetIsPresent() {
        val shader = File("src/main/assets/shaders/palette.frag")

        assertTrue("palette reduction shader must be part of the app assets", shader.isFile)
    }

    @Test
    fun redCoverageProducesRedPaletteAndMeasuredBrightness() {
        val result = DynamicPaletteReduction.reduce(
            samples = listOf(PaletteSample(PaletteColor(1f, 0f, 0f), 1f)),
            fallbackHueDegrees = 210f,
        )

        assertEquals(PaletteColor(1f, 0f, 0f), result.color)
        assertEquals(0.2126f, result.brightness, 0.0001f)
        assertEquals(1f, result.coverage, 0f)
        assertFalse(result.usedFallback)
    }

    @Test
    fun blueCoverageProducesBluePalette() {
        val result = DynamicPaletteReduction.reduce(
            samples = listOf(PaletteSample(PaletteColor(0f, 0f, 1f), 1f)),
            fallbackHueDegrees = 0f,
        )

        assertEquals(PaletteColor(0f, 0f, 1f), result.color)
        assertEquals(0.0722f, result.brightness, 0.0001f)
        assertFalse(result.usedFallback)
    }

    @Test
    fun mixedCoverageUsesAlphaWeightedRgbAndLuminance() {
        val result = DynamicPaletteReduction.reduce(
            samples = listOf(
                PaletteSample(PaletteColor(1f, 0f, 0f), 0.75f),
                PaletteSample(PaletteColor(0f, 0f, 1f), 0.25f),
            ),
            fallbackHueDegrees = 120f,
        )

        assertEquals(PaletteColor(0.75f, 0f, 0.25f), result.color)
        assertEquals(0.2126f * 0.75f + 0.0722f * 0.25f, result.brightness, 0.0001f)
        assertEquals(0.5f, result.coverage, 0f)
    }

    @Test
    fun lowerLuminanceInputProducesLowerBrightness() {
        val bright = DynamicPaletteReduction.reduce(
            listOf(PaletteSample(PaletteColor(1f, 1f, 1f), 1f)), 0f,
        )
        val dim = DynamicPaletteReduction.reduce(
            listOf(PaletteSample(PaletteColor(0.2f, 0.2f, 0.2f), 1f)), 0f,
        )

        assertTrue(bright.brightness > dim.brightness)
        assertEquals(1f, bright.brightness, 0f)
        assertEquals(0.2f, dim.brightness, 0.0001f)
    }

    @Test
    fun emptyCoverageUsesFiniteHueDerivedFallback() {
        val result = DynamicPaletteReduction.reduce(
            samples = listOf(PaletteSample(PaletteColor(0f, 0f, 0f), 0f)),
            fallbackHueDegrees = 210f,
        )
        val fallback = DynamicPaletteReduction.fallbackColor(210f)

        assertTrue(result.usedFallback)
        assertEquals(0f, result.coverage, 0f)
        assertEquals(fallback, result.color)
        assertTrue(result.brightness.isFinite())
        assertNotEquals(PaletteColor(0f, 0f, 0f), result.color)
    }

    @Test
    fun weakCoverageFallsBackInsteadOfAmplifyingNoise() {
        val result = DynamicPaletteReduction.reduce(
            samples = listOf(PaletteSample(PaletteColor(1f, 0f, 0f), 0.001f)),
            fallbackHueDegrees = 210f,
        )

        assertTrue(result.usedFallback)
        assertEquals(DynamicPaletteReduction.fallbackColor(210f), result.color)
    }
}
