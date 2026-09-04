package com.cxcboss.glassorb.model

import org.json.JSONObject

object OrbConfigJson {
    fun encode(config: OrbConfig): String {
        val value = config.normalized()
        return JSONObject().apply {
            put("schemaVersion", OrbConfig.CURRENT_SCHEMA_VERSION)
            put("geometry", JSONObject().apply {
                put("capsuleWidthDp", value.geometry.capsuleWidthDp)
                put("capsuleHeightDp", value.geometry.capsuleHeightDp)
                put("orbDiameterDp", value.geometry.orbDiameterDp)
                put("outerMarginDp", value.geometry.outerMarginDp)
                put("effectScale", value.geometry.effectScale)
                put("verticalOffsetDp", value.geometry.verticalOffsetDp)
                put("horizontalOffsetDp", value.geometry.horizontalOffsetDp)
                put("horizontalAnchor", value.geometry.horizontalAnchor.name)
            })
            put("glass", JSONObject().apply {
                put("internalDepth", value.glass.internalDepth)
                put("curvature", value.glass.curvature)
                put("highlightAmount", value.glass.highlightAmount)
                put("highlightWidth", value.glass.highlightWidth)
                put("highlightCut", value.glass.highlightCut)
                put("shadowAmount", value.glass.shadowAmount)
                put("causticAmount", value.glass.causticAmount)
                put("shadowOffset", value.glass.shadowOffset)
                put("causticOffset", value.glass.causticOffset)
                put("lightSoftness", value.glass.lightSoftness)
            })
            put("container", JSONObject().apply {
                put("strength", value.container.strength)
                put("blackLevel", value.container.blackLevel)
                put("fade", value.container.fade)
                put("gaussian", value.container.gaussian)
            })
            put("wave", JSONObject().apply {
                put("amplitude", value.wave.amplitude)
                put("scale", value.wave.scale)
                put("chromaticAberration", value.wave.chromaticAberration)
                put("lineWidth", value.wave.lineWidth)
                put("intensity", value.wave.intensity)
                put("bandFill", value.wave.bandFill)
                put("bandFillThickness", value.wave.bandFillThickness)
                put("softness", value.wave.softness)
                put("whiteBloom", value.wave.whiteBloom)
                put("hueShiftDegrees", value.wave.hueShiftDegrees)
            })
            put("dots", JSONObject().apply {
                put("ringRadius", value.dots.ringRadius)
                put("dotRadius", value.dots.dotRadius)
                put("glow", value.dots.glow)
                put("rotationSpeed", value.dots.rotationSpeed)
            })
            put("motion", JSONObject().apply {
                put("openResponse", value.motion.openResponse)
                put("openDamping", value.motion.openDamping)
                put("closeResponse", value.motion.closeResponse)
                put("closeDamping", value.motion.closeDamping)
                put("closeBounce", value.motion.closeBounce)
                put("collapseRangeDp", value.motion.collapseRangeDp)
                put("dragRangeDp", value.motion.dragRangeDp)
                put("dragResistance", value.motion.dragResistance)
                put("deformLimitDp", value.motion.deformLimitDp)
                put("deformScaleDelta", value.motion.deformScaleDelta)
                put("deformResponse", value.motion.deformResponse)
                put("deformDamping", value.motion.deformDamping)
                put("waveFadeDelayMs", value.motion.waveFadeDelayMs)
                put("breathingAmplitude", value.motion.breathingAmplitude)
                put("breathingSpeed", value.motion.breathingSpeed)
                put("pressScale", value.motion.pressScale)
                put("thinkingDurationMs", value.motion.thinkingDurationMs)
            })
            put("performance", JSONObject().apply {
                put("collapsedFps", value.performance.collapsedFps)
                put("expandedFps", value.performance.expandedFps)
                put("renderScale", value.performance.renderScale)
            })
        }.toString(2)
    }

    fun decode(json: String): Result<OrbConfig> = runCatching {
        val root = JSONObject(json)
        val defaults = OrbConfig.reference()
        val geometry = root.optJSONObject("geometry")
        val glass = root.optJSONObject("glass")
        val container = root.optJSONObject("container")
        val wave = root.optJSONObject("wave")
        val dots = root.optJSONObject("dots")
        val motion = root.optJSONObject("motion")
        val performance = root.optJSONObject("performance")

        OrbConfig(
            schemaVersion = root.int("schemaVersion", defaults.schemaVersion),
            geometry = defaults.geometry.copy(
                capsuleWidthDp = geometry.float("capsuleWidthDp", defaults.geometry.capsuleWidthDp),
                capsuleHeightDp = geometry.float("capsuleHeightDp", defaults.geometry.capsuleHeightDp),
                orbDiameterDp = geometry.float("orbDiameterDp", defaults.geometry.orbDiameterDp),
                outerMarginDp = geometry.float("outerMarginDp", defaults.geometry.outerMarginDp),
                effectScale = geometry.float("effectScale", defaults.geometry.effectScale),
                verticalOffsetDp = geometry.float("verticalOffsetDp", defaults.geometry.verticalOffsetDp),
                horizontalOffsetDp = geometry.float("horizontalOffsetDp", defaults.geometry.horizontalOffsetDp),
                horizontalAnchor = geometry.enum("horizontalAnchor", defaults.geometry.horizontalAnchor),
            ),
            glass = defaults.glass.copy(
                internalDepth = glass.float("internalDepth", defaults.glass.internalDepth),
                curvature = glass.float("curvature", defaults.glass.curvature),
                highlightAmount = glass.float("highlightAmount", defaults.glass.highlightAmount),
                highlightWidth = glass.float("highlightWidth", defaults.glass.highlightWidth),
                highlightCut = glass.float("highlightCut", defaults.glass.highlightCut),
                shadowAmount = glass.float("shadowAmount", defaults.glass.shadowAmount),
                causticAmount = glass.float("causticAmount", defaults.glass.causticAmount),
                shadowOffset = glass.float("shadowOffset", defaults.glass.shadowOffset),
                causticOffset = glass.float("causticOffset", defaults.glass.causticOffset),
                lightSoftness = glass.float("lightSoftness", defaults.glass.lightSoftness),
            ),
            container = defaults.container.copy(
                strength = container.float("strength", defaults.container.strength),
                blackLevel = container.float("blackLevel", defaults.container.blackLevel),
                fade = container.float("fade", defaults.container.fade),
                gaussian = container.float("gaussian", defaults.container.gaussian),
            ),
            wave = defaults.wave.copy(
                amplitude = wave.float("amplitude", defaults.wave.amplitude),
                scale = wave.float("scale", defaults.wave.scale),
                chromaticAberration = wave.float("chromaticAberration", defaults.wave.chromaticAberration),
                lineWidth = wave.float("lineWidth", defaults.wave.lineWidth),
                intensity = wave.float("intensity", defaults.wave.intensity),
                bandFill = wave.float("bandFill", defaults.wave.bandFill),
                bandFillThickness = wave.float("bandFillThickness", defaults.wave.bandFillThickness),
                softness = wave.float("softness", defaults.wave.softness),
                whiteBloom = wave.float("whiteBloom", defaults.wave.whiteBloom),
                hueShiftDegrees = wave.float("hueShiftDegrees", defaults.wave.hueShiftDegrees),
            ),
            dots = defaults.dots.copy(
                ringRadius = dots.float("ringRadius", defaults.dots.ringRadius),
                dotRadius = dots.float("dotRadius", defaults.dots.dotRadius),
                glow = dots.float("glow", defaults.dots.glow),
                rotationSpeed = dots.float("rotationSpeed", defaults.dots.rotationSpeed),
            ),
            motion = defaults.motion.copy(
                openResponse = motion.float("openResponse", defaults.motion.openResponse),
                openDamping = motion.float("openDamping", defaults.motion.openDamping),
                closeResponse = motion.float("closeResponse", defaults.motion.closeResponse),
                closeDamping = motion.float("closeDamping", defaults.motion.closeDamping),
                closeBounce = motion.float("closeBounce", defaults.motion.closeBounce),
                collapseRangeDp = motion.float("collapseRangeDp", defaults.motion.collapseRangeDp),
                dragRangeDp = motion.float("dragRangeDp", defaults.motion.dragRangeDp),
                dragResistance = motion.float("dragResistance", defaults.motion.dragResistance),
                deformLimitDp = motion.float("deformLimitDp", defaults.motion.deformLimitDp),
                deformScaleDelta = motion.float("deformScaleDelta", defaults.motion.deformScaleDelta),
                deformResponse = motion.float("deformResponse", defaults.motion.deformResponse),
                deformDamping = motion.float("deformDamping", defaults.motion.deformDamping),
                waveFadeDelayMs = motion.int("waveFadeDelayMs", defaults.motion.waveFadeDelayMs),
                breathingAmplitude = motion.float("breathingAmplitude", defaults.motion.breathingAmplitude),
                breathingSpeed = motion.float("breathingSpeed", defaults.motion.breathingSpeed),
                pressScale = motion.float("pressScale", defaults.motion.pressScale),
                thinkingDurationMs = motion.int("thinkingDurationMs", defaults.motion.thinkingDurationMs),
            ),
            performance = defaults.performance.copy(
                collapsedFps = performance.int("collapsedFps", defaults.performance.collapsedFps),
                expandedFps = performance.int("expandedFps", defaults.performance.expandedFps),
                renderScale = performance.float("renderScale", defaults.performance.renderScale),
            ),
        ).normalized()
    }
}

private fun JSONObject?.float(name: String, fallback: Float): Float =
    if (this == null || !has(name)) fallback else optDouble(name, fallback.toDouble()).toFloat()

private fun JSONObject?.int(name: String, fallback: Int): Int =
    if (this == null || !has(name)) fallback else optInt(name, fallback)

private inline fun <reified T : Enum<T>> JSONObject?.enum(name: String, fallback: T): T {
    val raw = if (this == null) null else optString(name, "")
    return enumValues<T>().firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: fallback
}
