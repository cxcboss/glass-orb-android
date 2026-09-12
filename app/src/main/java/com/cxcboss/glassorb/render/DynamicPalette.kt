package com.cxcboss.glassorb.render

import kotlin.math.floor

internal data class PaletteColor(
    val red: Float,
    val green: Float,
    val blue: Float,
) {
    fun clamped(): PaletteColor = PaletteColor(
        red.coerceIn(0f, 1f),
        green.coerceIn(0f, 1f),
        blue.coerceIn(0f, 1f),
    )
}

internal data class PaletteSample(
    val color: PaletteColor,
    val alpha: Float,
)

internal data class PaletteResult(
    val color: PaletteColor,
    val mixedColor: PaletteColor,
    val brightness: Float,
    val coverage: Float,
    val confidence: Float,
    val usedFallback: Boolean,
)

/** CPU mirror of the values packed by palette.frag, kept small and pure for tests. */
internal object DynamicPaletteReduction {
    const val FALLBACK_COVERAGE = 0.01f
    const val FULL_CONFIDENCE_COVERAGE = 0.06f
    const val BRIGHTNESS_GAIN = 3f
    const val MIN_BRIGHTNESS = 0.12f
    private const val EPSILON = 0.0001f
    private const val RED_LUMA = 0.2126f
    private const val GREEN_LUMA = 0.7152f
    private const val BLUE_LUMA = 0.0722f

    fun reduce(samples: List<PaletteSample>, fallbackHueDegrees: Float): PaletteResult {
        var weightedRed = 0f
        var weightedGreen = 0f
        var weightedBlue = 0f
        var totalCoverage = 0f
        samples.forEach { sample ->
            val weight = sample.alpha.coerceIn(0f, 1f)
            val color = sample.color.clamped()
            weightedRed += color.red * weight
            weightedGreen += color.green * weight
            weightedBlue += color.blue * weight
            totalCoverage += weight
        }

        val coverage = totalCoverage / maxOf(samples.size, 1)
        val safeWeight = maxOf(totalCoverage, EPSILON)
        val reduced = PaletteColor(
            weightedRed / safeWeight,
            weightedGreen / safeWeight,
            weightedBlue / safeWeight,
        ).clamped()
        return recover(reduced, coverage, fallbackHueDegrees)
    }

    fun recover(reducedColor: PaletteColor, coverage: Float, fallbackHueDegrees: Float): PaletteResult {
        val safeCoverage = coverage.coerceIn(0f, 1f)
        val fallback = fallbackColor(fallbackHueDegrees)
        val confidence = smoothstep(FALLBACK_COVERAGE, FULL_CONFIDENCE_COVERAGE, safeCoverage)
        val mixed = mix(fallback, reducedColor.clamped(), confidence)
        val mixedLuminance = luminance(mixed)
        val fallbackLuminance = luminance(fallback)
        val brightness = lerp(
            fallbackLuminance,
            (mixedLuminance * BRIGHTNESS_GAIN).coerceIn(MIN_BRIGHTNESS, 1f),
            confidence,
        )
        val maxChannel = maxOf(maxOf(mixed.red, mixed.green), maxOf(mixed.blue, EPSILON))
        val chromaScale = 1f / maxChannel
        val recovered = PaletteColor(
            mixed.red * chromaScale * brightness,
            mixed.green * chromaScale * brightness,
            mixed.blue * chromaScale * brightness,
        ).clamped()
        return PaletteResult(
            color = recovered,
            mixedColor = mixed,
            brightness = brightness,
            coverage = safeCoverage,
            confidence = confidence,
            usedFallback = confidence <= EPSILON,
        )
    }

    fun fallbackColor(hueDegrees: Float): PaletteColor {
        val hue = ((hueDegrees / 360f) % 1f + 1f) % 1f
        val h = hue * 6f
        val sector = floor(h).toInt()
        val fraction = h - floor(h)
        val saturation = 0.62f
        val value = 0.9f
        val p = value * (1f - saturation)
        val q = value * (1f - saturation * fraction)
        val t = value * (1f - saturation * (1f - fraction))
        return when (sector) {
            0 -> PaletteColor(value, t, p)
            1 -> PaletteColor(q, value, p)
            2 -> PaletteColor(p, value, t)
            3 -> PaletteColor(p, q, value)
            4 -> PaletteColor(t, p, value)
            else -> PaletteColor(value, p, q)
        }
    }

    private fun luminance(color: PaletteColor): Float =
        color.red * RED_LUMA + color.green * GREEN_LUMA + color.blue * BLUE_LUMA

    private fun mix(start: PaletteColor, end: PaletteColor, fraction: Float): PaletteColor = PaletteColor(
        lerp(start.red, end.red, fraction),
        lerp(start.green, end.green, fraction),
        lerp(start.blue, end.blue, fraction),
    )

    private fun lerp(start: Float, end: Float, fraction: Float): Float =
        start + (end - start) * fraction

    private fun smoothstep(edge0: Float, edge1: Float, value: Float): Float {
        val t = ((value - edge0) / maxOf(edge1 - edge0, EPSILON)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
}
