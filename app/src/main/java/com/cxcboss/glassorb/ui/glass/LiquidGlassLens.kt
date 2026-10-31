package com.cxcboss.glassorb.ui.glass

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import androidx.annotation.RequiresApi

/** AGSL lens (API 33+). Kept in its own object so older devices never load RuntimeShader. */
@RequiresApi(33)
internal object LiquidGlassLens {
    fun apply(base: RenderEffect, lens: LensParams, cached: Any?): Pair<RenderEffect, Any> {
        val shader = (cached as? RuntimeShader) ?: RuntimeShader(LENS_AGSL)
        shader.setFloatUniform("origin", lens.originX, lens.originY)
        shader.setFloatUniform("size", lens.width, lens.height)
        shader.setFloatUniform("radius", lens.cornerRadius)
        shader.setFloatUniform("bezel", lens.bezel)
        shader.setFloatUniform("strength", lens.strength)
        shader.setFloatUniform("dispersion", lens.dispersion)
        val effect = RenderEffect.createChainEffect(
            RenderEffect.createRuntimeShaderEffect(shader, "content"),
            base,
        )
        return effect to shader
    }

    data class LensParams(
        val originX: Float,
        val originY: Float,
        val width: Float,
        val height: Float,
        val cornerRadius: Float,
        val bezel: Float,
        val strength: Float,
        val dispersion: Float,
    )

    private const val LENS_AGSL = """
uniform shader content;
uniform float2 origin;
uniform float2 size;
uniform float radius;
uniform float bezel;
uniform float strength;
uniform float dispersion;

float sdRoundRect(float2 p, float2 extent, float r) {
    float2 q = abs(p) - extent + float2(r);
    return min(max(q.x, q.y), 0.0) + length(max(q, float2(0.0))) - r;
}

half4 main(float2 coord) {
    float2 extent = size * 0.5;
    float2 p = coord - origin - extent;
    float d = sdRoundRect(p, extent, radius);
    float depth = -d;
    if (depth <= 0.0) {
        return content.eval(coord);
    }
    float t = clamp(1.0 - depth / max(bezel, 1.0), 0.0, 1.0);
    float profile = t * t * (3.0 - 2.0 * t);
    float2 gx = float2(1.0, 0.0);
    float2 gy = float2(0.0, 1.0);
    float2 grad = float2(
        sdRoundRect(p + gx, extent, radius) - sdRoundRect(p - gx, extent, radius),
        sdRoundRect(p + gy, extent, radius) - sdRoundRect(p - gy, extent, radius));
    float2 n = grad / max(length(grad), 0.0001);
    float2 shift = -n * strength * profile;
    float2 lo = float2(0.5);
    float2 hi = origin * 2.0 + size - float2(0.5);
    half4 cr = content.eval(clamp(coord + shift * (1.0 + dispersion), lo, hi));
    half4 cg = content.eval(clamp(coord + shift, lo, hi));
    half4 cb = content.eval(clamp(coord + shift * (1.0 - dispersion), lo, hi));
    return half4(cr.r, cg.g, cb.b, cg.a);
}
"""
}
