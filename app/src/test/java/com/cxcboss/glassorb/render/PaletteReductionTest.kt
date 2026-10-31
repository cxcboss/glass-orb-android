package com.cxcboss.glassorb.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PaletteReductionTest {
    @Test
    fun redCoverageProducesRedPaletteAndMeasuredBrightness() {
        val result = DynamicPaletteReduction.reduce(
            samples = listOf(PaletteSample(PaletteColor(1f, 0f, 0f), 1f)),
            fallbackHueDegrees = 210f,
        )

        assertEquals(PaletteColor(0.6378f, 0f, 0f), result.color)
        assertEquals(0.6378f, result.brightness, 0.0001f)
        assertEquals(1f, result.confidence, 0f)
        assertEquals(1f, result.coverage, 0f)
        assertFalse(result.usedFallback)
    }

    @Test
    fun blueCoverageProducesBluePalette() {
        val result = DynamicPaletteReduction.reduce(
            samples = listOf(PaletteSample(PaletteColor(0f, 0f, 1f), 1f)),
            fallbackHueDegrees = 0f,
        )

        assertEquals(PaletteColor(0f, 0f, 0.2166f), result.color)
        assertEquals(0.2166f, result.brightness, 0.0001f)
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

        assertEquals(PaletteColor(0.75f, 0f, 0.25f), result.mixedColor)
        assertEquals(0.5325f, result.color.red, 0.0001f)
        assertEquals(0f, result.color.green, 0f)
        assertEquals(0.1775f, result.color.blue, 0.0001f)
        assertEquals((0.2126f * 0.75f + 0.0722f * 0.25f) * 3f, result.brightness, 0.0001f)
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
        assertEquals(0.6f, dim.brightness, 0.0001f)
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
        assertEquals(0f, result.confidence, 0f)
        assertEquals(fallback, result.mixedColor)
        assertTrue(result.brightness.isFinite())
        assertNotEquals(PaletteColor(0f, 0f, 0f), result.mixedColor)
    }

    @Test
    fun weakCoverageFallsBackInsteadOfAmplifyingNoise() {
        val result = DynamicPaletteReduction.reduce(
            samples = listOf(PaletteSample(PaletteColor(1f, 0f, 0f), 0.001f)),
            fallbackHueDegrees = 210f,
        )

        assertTrue(result.usedFallback)
        assertEquals(0f, result.confidence, 0f)
        assertEquals(DynamicPaletteReduction.fallbackColor(210f), result.mixedColor)
    }

    @Test
    fun partialCoverageUsesTheSameConfidenceAndBrightnessRecoveryFormula() {
        val result = DynamicPaletteReduction.recover(
            reducedColor = PaletteColor(1f, 0f, 0f),
            coverage = 0.035f,
            fallbackHueDegrees = 0f,
        )
        val confidence = ((0.035f - 0.01f) / (0.06f - 0.01f)).let { it * it * (3f - 2f * it) }
        val fallback = DynamicPaletteReduction.fallbackColor(0f)
        val mixedRed = fallback.red + (1f - fallback.red) * confidence
        val mixedGreen = fallback.green * (1f - confidence)
        val mixedBlue = fallback.blue * (1f - confidence)
        val mixedLuminance = mixedRed * 0.2126f + mixedGreen * 0.7152f + mixedBlue * 0.0722f
        val fallbackLuminance = fallback.red * 0.2126f + fallback.green * 0.7152f + fallback.blue * 0.0722f
        val brightness = fallbackLuminance +
            ((mixedLuminance * 3f).coerceIn(0.12f, 1f) - fallbackLuminance) * confidence

        assertEquals(confidence, result.confidence, 0.0001f)
        assertEquals(PaletteColor(mixedRed, mixedGreen, mixedBlue), result.mixedColor)
        assertEquals(brightness, result.brightness, 0.0001f)
    }
}
