package com.cxcboss.glassorb.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PaletteRenderContractTest {
    @Test
    fun effectAndPaletteTextureUnitsDoNotConflictWithinEitherConsumerPass() {
        val bindings = DynamicPaletteBindings

        assertNotEquals(bindings.effectTexture.textureUnit, bindings.paletteTexture.textureUnit)
        assertNotEquals(bindings.sceneTexture.textureUnit, bindings.paletteTexture.textureUnit)
        assertEquals(bindings.effectTexture.textureUnit, bindings.sceneTexture.textureUnit)
    }

    @Test
    fun planKeepsEffectPaletteSceneGlassPassOrder() {
        assertEquals(
            listOf(
                DynamicPaletteRenderPass.Effect,
                DynamicPaletteRenderPass.Palette,
                DynamicPaletteRenderPass.Scene,
                DynamicPaletteRenderPass.Glass,
            ),
            DynamicPalettePassPlan.passOrder,
        )
    }

    @Test
    fun paletteAndGlassBindingNamesComeFromTheSharedContract() {
        val bindings = DynamicPaletteBindings

        assertEquals(DynamicPaletteBindings.EFFECT_TEXTURE_UNIT, bindings.effectTexture.textureUnit)
        assertEquals(DynamicPaletteBindings.EFFECT_TEXTURE_UNIFORM, bindings.effectTexture.uniformName)
        assertEquals(DynamicPaletteBindings.PALETTE_TEXTURE_UNIT, bindings.paletteTexture.textureUnit)
        assertEquals(DynamicPaletteBindings.PALETTE_TEXTURE_UNIFORM, bindings.paletteTexture.uniformName)
        assertEquals(DynamicPaletteBindings.SCENE_TEXTURE_UNIFORM, bindings.sceneTexture.uniformName)
        assertEquals(DynamicPaletteBindings.EFFECT_RESOLUTION_UNIFORM, bindings.effectResolutionUniform)
    }
}
