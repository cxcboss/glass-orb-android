package com.cxcboss.glassorb.ui.glass

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.graphics.Shader
import androidx.annotation.RequiresApi

/**
 * Effect chain used by [LiquidGlassView]:
 *
 *  - API 31+: backdrop blur + saturation boost (frosted glass).
 *  - API 33+: additionally an AGSL lens that bends the backdrop near the rim of
 *    the rounded-rect signed-distance field, with a little dispersion. This is
 *    the "liquid" part: content at the edge of the glass appears pulled inward.
 *  - Older devices fall back to a flat translucent tint in [LiquidGlassView].
 *
 * The lens is an independent implementation of the common SDF-lens technique;
 * no source from Kyant0/AndroidLiquidGlass (Compose only) is used.
 */
internal object LiquidGlassEffects {
    private val saturation: ColorMatrixColorFilter by lazy {
        ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(1.55f) })
    }

    @RequiresApi(31)
    fun blurSaturation(blurPx: Float): RenderEffect {
        val radius = blurPx.coerceAtLeast(0.1f)
        val blur = RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP)
        return RenderEffect.createColorFilterEffect(saturation, blur)
    }
}
