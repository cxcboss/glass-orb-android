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
    val brightness: Float,
    val coverage: Float,
    val usedFallback: Boolean,
)

/** CPU mirror of the values packed by palette.frag, kept small and pure for tests. */
internal object DynamicPaletteReduction {
    private const val MIN_COVERAGE = 0.02f
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
        val fallback = fallbackColor(fallbackHueDegrees)
        if (coverage < MIN_COVERAGE || totalCoverage < EPSILON) {
            return PaletteResult(fallback, luminance(fallback), coverage, usedFallback = true)
        }

        val safeWeight = maxOf(totalCoverage, EPSILON)
        val mixed = PaletteColor(
            weightedRed / safeWeight,
            weightedGreen / safeWeight,
            weightedBlue / safeWeight,
        ).clamped()
        return PaletteResult(mixed, luminance(mixed), coverage, usedFallback = false)
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
}
