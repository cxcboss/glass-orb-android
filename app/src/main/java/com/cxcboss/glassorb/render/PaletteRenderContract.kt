package com.cxcboss.glassorb.render

internal enum class DynamicPaletteRenderPass {
    Effect,
    Palette,
    Scene,
    Glass,
}

internal data class DynamicPaletteTextureBinding(
    val textureUnit: Int,
    val uniformName: String,
)

/** Pure sampler/uniform contract shared by the render implementation and JVM wiring tests. */
internal object DynamicPaletteBindings {
    const val EFFECT_TEXTURE_UNIT = 0
    const val PALETTE_TEXTURE_UNIT = 1
    const val EFFECT_TEXTURE_UNIFORM = "uEffectTexture"
    const val PALETTE_TEXTURE_UNIFORM = "uPaletteTexture"
    const val SCENE_TEXTURE_UNIFORM = "uSceneTexture"
    const val EFFECT_RESOLUTION_UNIFORM = "uEffectResolution"

    val effectTexture = DynamicPaletteTextureBinding(EFFECT_TEXTURE_UNIT, EFFECT_TEXTURE_UNIFORM)
    val paletteTexture = DynamicPaletteTextureBinding(PALETTE_TEXTURE_UNIT, PALETTE_TEXTURE_UNIFORM)
    val sceneTexture = DynamicPaletteTextureBinding(EFFECT_TEXTURE_UNIT, SCENE_TEXTURE_UNIFORM)
    val effectResolutionUniform = EFFECT_RESOLUTION_UNIFORM
}

/** Pure pass-order plan shared by the render implementation and JVM wiring tests. */
internal object DynamicPalettePassPlan {
    val bindings = DynamicPaletteBindings
    val passOrder = listOf(
        DynamicPaletteRenderPass.Effect,
        DynamicPaletteRenderPass.Palette,
        DynamicPaletteRenderPass.Scene,
        DynamicPaletteRenderPass.Glass,
    )
}
