package com.cxcboss.glassorb.model

import kotlin.math.max

enum class HorizontalAnchor {
    Center,
    Left,
    Right,
}

data class GeometryConfig(
    val capsuleWidthDp: Float = 118f,
    val capsuleHeightDp: Float = 34f,
    val orbDiameterDp: Float = 128f,
    val outerMarginDp: Float = 20f,
    val effectScale: Float = 1.18f,
    val verticalOffsetDp: Float = 8f,
    val horizontalOffsetDp: Float = 0f,
    val horizontalAnchor: HorizontalAnchor = HorizontalAnchor.Center,
)

data class GlassConfig(
    val internalDepth: Float = 18f,
    val curvature: Float = 1f,
    val highlightAmount: Float = 0.72f,
    val highlightWidth: Float = 2.2f,
    val highlightCut: Float = 0.52f,
    val shadowAmount: Float = 0.4f,
    val causticAmount: Float = 1.6f,
    val shadowOffset: Float = 0.3f,
    val causticOffset: Float = -1f,
    val lightSoftness: Float = 4.2f,
)

data class ContainerConfig(
    val strength: Float = 0.9f,
    val blackLevel: Float = 0.25f,
    val fade: Float = 1f,
    val gaussian: Float = 8f,
)

data class WaveConfig(
    val amplitude: Float = 0.22f,
    val scale: Float = 0.9f,
    val chromaticAberration: Float = 2.6f,
    val lineWidth: Float = 3f,
    val intensity: Float = 2f,
    val bandFill: Float = 30_000f,
    val bandFillThickness: Float = 0.08f,
    val softness: Float = 2.5f,
    val whiteBloom: Float = 1f,
    val hueShiftDegrees: Float = 0f,
)

data class DotsConfig(
    val ringRadius: Float = 0.45f,
    val dotRadius: Float = 0.1f,
    val glow: Float = 0.055f,
    val rotationSpeed: Float = 0.7f,
)

data class MotionConfig(
    val openResponse: Float = 0.42f,
    val openDamping: Float = 0.72f,
    val closeResponse: Float = 0.30f,
    val closeDamping: Float = 0.78f,
    val closeBounce: Float = 0.032f,
    val waveFadeDelayMs: Int = 80,
    val breathingAmplitude: Float = 0.028f,
    val breathingSpeed: Float = 1.65f,
    val pressScale: Float = 1.018f,
    val thinkingDurationMs: Int = 1_200,
)

data class PerformanceConfig(
    val collapsedFps: Int = 24,
    val expandedFps: Int = 60,
    val renderScale: Float = 1f,
)

data class OrbConfig(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val geometry: GeometryConfig = GeometryConfig(),
    val glass: GlassConfig = GlassConfig(),
    val container: ContainerConfig = ContainerConfig(),
    val wave: WaveConfig = WaveConfig(),
    val dots: DotsConfig = DotsConfig(),
    val motion: MotionConfig = MotionConfig(),
    val performance: PerformanceConfig = PerformanceConfig(),
) {
    fun normalized(): OrbConfig {
        val defaults = reference()
        return copy(
            schemaVersion = CURRENT_SCHEMA_VERSION,
            geometry = geometry.copy(
                capsuleWidthDp = geometry.capsuleWidthDp.safeRange(72f, 220f, defaults.geometry.capsuleWidthDp),
                capsuleHeightDp = geometry.capsuleHeightDp.safeRange(24f, 64f, defaults.geometry.capsuleHeightDp),
                orbDiameterDp = geometry.orbDiameterDp.safeRange(88f, 220f, defaults.geometry.orbDiameterDp),
                outerMarginDp = geometry.outerMarginDp.safeRange(8f, 48f, defaults.geometry.outerMarginDp),
                effectScale = geometry.effectScale.safeRange(0.9f, 1.5f, defaults.geometry.effectScale),
                verticalOffsetDp = geometry.verticalOffsetDp.safeRange(0f, 240f, defaults.geometry.verticalOffsetDp),
                horizontalOffsetDp = geometry.horizontalOffsetDp.safeRange(-600f, 600f, defaults.geometry.horizontalOffsetDp),
            ),
            glass = glass.copy(
                internalDepth = glass.internalDepth.safeRange(0f, 40f, defaults.glass.internalDepth),
                curvature = glass.curvature.safeRange(0f, 1f, defaults.glass.curvature),
                highlightAmount = glass.highlightAmount.safeRange(0f, 2f, defaults.glass.highlightAmount),
                highlightWidth = glass.highlightWidth.safeRange(0.2f, 8f, defaults.glass.highlightWidth),
                highlightCut = glass.highlightCut.safeRange(0f, 1f, defaults.glass.highlightCut),
                shadowAmount = glass.shadowAmount.safeRange(0f, 1.5f, defaults.glass.shadowAmount),
                causticAmount = glass.causticAmount.safeRange(0f, 3f, defaults.glass.causticAmount),
                shadowOffset = glass.shadowOffset.safeRange(-3f, 3f, defaults.glass.shadowOffset),
                causticOffset = glass.causticOffset.safeRange(-4f, 4f, defaults.glass.causticOffset),
                lightSoftness = glass.lightSoftness.safeRange(0.5f, 10f, defaults.glass.lightSoftness),
            ),
            container = container.copy(
                strength = container.strength.safeRange(0f, 1.5f, defaults.container.strength),
                blackLevel = container.blackLevel.safeRange(0f, 1f, defaults.container.blackLevel),
                fade = container.fade.safeRange(0f, 2f, defaults.container.fade),
                gaussian = container.gaussian.safeRange(0.5f, 16f, defaults.container.gaussian),
            ),
            wave = wave.copy(
                amplitude = wave.amplitude.safeRange(0f, 0.6f, defaults.wave.amplitude),
                scale = wave.scale.safeRange(0.4f, 1.6f, defaults.wave.scale),
                chromaticAberration = wave.chromaticAberration.safeRange(0f, 8f, defaults.wave.chromaticAberration),
                lineWidth = wave.lineWidth.safeRange(0.5f, 8f, defaults.wave.lineWidth),
                intensity = wave.intensity.safeRange(0f, 5f, defaults.wave.intensity),
                bandFill = wave.bandFill.safeRange(0f, 60_000f, defaults.wave.bandFill),
                bandFillThickness = wave.bandFillThickness.safeRange(0f, 0.3f, defaults.wave.bandFillThickness),
                softness = wave.softness.safeRange(0.2f, 8f, defaults.wave.softness),
                whiteBloom = wave.whiteBloom.safeRange(0f, 3f, defaults.wave.whiteBloom),
                hueShiftDegrees = wave.hueShiftDegrees.safeRange(-180f, 180f, defaults.wave.hueShiftDegrees),
            ),
            dots = dots.copy(
                ringRadius = dots.ringRadius.safeRange(0.15f, 0.75f, defaults.dots.ringRadius),
                dotRadius = dots.dotRadius.safeRange(0.025f, 0.22f, defaults.dots.dotRadius),
                glow = dots.glow.safeRange(0f, 0.2f, defaults.dots.glow),
                rotationSpeed = dots.rotationSpeed.safeRange(-3f, 3f, defaults.dots.rotationSpeed),
            ),
            motion = motion.copy(
                openResponse = motion.openResponse.safeRange(0.12f, 1.2f, defaults.motion.openResponse),
                openDamping = motion.openDamping.safeRange(0.2f, 1.5f, defaults.motion.openDamping),
                closeResponse = motion.closeResponse.safeRange(0.12f, 1.2f, defaults.motion.closeResponse),
                closeDamping = motion.closeDamping.safeRange(0.2f, 1.5f, defaults.motion.closeDamping),
                closeBounce = motion.closeBounce.safeRange(0f, 0.12f, defaults.motion.closeBounce),
                waveFadeDelayMs = motion.waveFadeDelayMs.coerceIn(0, 500),
                breathingAmplitude = motion.breathingAmplitude.safeRange(0f, 0.08f, defaults.motion.breathingAmplitude),
                breathingSpeed = motion.breathingSpeed.safeRange(0f, 4f, defaults.motion.breathingSpeed),
                pressScale = motion.pressScale.safeRange(1f, 1.08f, defaults.motion.pressScale),
                thinkingDurationMs = motion.thinkingDurationMs.coerceIn(300, 5_000),
            ),
            performance = performance.copy(
                collapsedFps = performance.collapsedFps.coerceIn(10, 60),
                expandedFps = max(performance.collapsedFps, performance.expandedFps.coerceIn(24, 120)),
                renderScale = performance.renderScale.safeRange(0.5f, 1.25f, defaults.performance.renderScale),
            ),
        )
    }

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1

        fun reference(): OrbConfig = OrbConfig()

        fun soft(): OrbConfig = reference().copy(
            glass = GlassConfig(
                highlightAmount = 0.5f,
                shadowAmount = 0.28f,
                causticAmount = 1.05f,
                lightSoftness = 5.4f,
            ),
            wave = WaveConfig(amplitude = 0.17f, intensity = 1.55f, chromaticAberration = 2.1f),
            motion = MotionConfig(openResponse = 0.50f, openDamping = 0.82f),
        )

        fun bright(): OrbConfig = reference().copy(
            glass = GlassConfig(highlightAmount = 1.05f, shadowAmount = 0.48f, causticAmount = 2.05f),
            container = ContainerConfig(strength = 0.82f, blackLevel = 0.19f),
            wave = WaveConfig(intensity = 2.55f, whiteBloom = 1.35f, chromaticAberration = 3.1f),
        )
    }
}

private fun Float.safeRange(minimum: Float, maximum: Float, fallback: Float): Float =
    if (isFinite()) coerceIn(minimum, maximum) else fallback
