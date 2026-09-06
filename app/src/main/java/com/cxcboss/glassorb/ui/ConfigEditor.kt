package com.cxcboss.glassorb.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cxcboss.glassorb.data.ConfigGroup
import com.cxcboss.glassorb.model.HorizontalAnchor
import com.cxcboss.glassorb.model.OrbConfig
import com.cxcboss.glassorb.overlay.OrbOverlayService
import com.cxcboss.glassorb.overlay.OverlayRuntime
import com.cxcboss.glassorb.overlay.OverlayRuntimeStatus
import java.util.Locale

private val ReferenceConfig = OrbConfig.reference()
@Composable
fun ConfigEditor(config: OrbConfig, onConfigChange: (OrbConfig) -> Unit, group: ConfigGroup, onReset: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
        when (group) {
        ConfigGroup.Geometry -> {
            val value = config.geometry
            val defaults = ReferenceConfig.geometry
            item {
            Text("水平预设", style = MaterialTheme.typography.labelLarge)
            }
            item {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AnchorChip("左侧", value.horizontalAnchor == HorizontalAnchor.Left) {
                    onConfigChange(config.copy(geometry = value.copy(horizontalAnchor = HorizontalAnchor.Left)))
                }
                AnchorChip("居中", value.horizontalAnchor == HorizontalAnchor.Center) {
                    onConfigChange(config.copy(geometry = value.copy(horizontalAnchor = HorizontalAnchor.Center)))
                }
                AnchorChip("右侧", value.horizontalAnchor == HorizontalAnchor.Right) {
                    onConfigChange(config.copy(geometry = value.copy(horizontalAnchor = HorizontalAnchor.Right)))
                }
            }
            }
            item {
            ParameterSlider("胶囊宽度", value.capsuleWidthDp, defaults.capsuleWidthDp, 24f..220f, "dp") {
                onConfigChange(config.copy(geometry = value.copy(capsuleWidthDp = it)))
            }
            }
            item {
            ParameterSlider("胶囊高度", value.capsuleHeightDp, defaults.capsuleHeightDp, 24f..64f, "dp") {
                onConfigChange(config.copy(geometry = value.copy(capsuleHeightDp = it)))
            }
            }
            item {
            ParameterSlider("球体直径", value.orbDiameterDp, defaults.orbDiameterDp, 88f..220f, "dp") {
                onConfigChange(config.copy(geometry = value.copy(orbDiameterDp = it)))
            }
            }
            item {
            ParameterSlider("外部效果余量", value.outerMarginDp, defaults.outerMarginDp, 8f..48f, "dp") {
                onConfigChange(config.copy(geometry = value.copy(outerMarginDp = it)))
            }
            }
            item {
            ParameterSlider("效果画布比例", value.effectScale, defaults.effectScale, 0.9f..1.5f, decimals = 2) {
                onConfigChange(config.copy(geometry = value.copy(effectScale = it)))
            }
            }
            item {
            ParameterSlider("顶部偏移", value.verticalOffsetDp, defaults.verticalOffsetDp, 0f..240f, "dp") {
                onConfigChange(config.copy(geometry = value.copy(verticalOffsetDp = it)))
            }
            }
            item {
            Text("0 dp = 真实物理屏幕顶边", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
            ParameterSlider("水平微调", value.horizontalOffsetDp, defaults.horizontalOffsetDp, -200f..200f, "dp") {
                onConfigChange(config.copy(geometry = value.copy(horizontalOffsetDp = it)))
            }
            }
            item {
            ToggleRow("扩大胶囊触摸区域", value.enlargedTouchArea) {
                onConfigChange(config.copy(geometry = value.copy(enlargedTouchArea = it)))
            }
            }
            if (value.enlargedTouchArea) {
            item {
                ParameterSlider("触摸区域倍率", value.touchAreaScale, defaults.touchAreaScale, 1f..3f, "×", decimals = 2, touchPreview = true) {
                    onConfigChange(config.copy(geometry = value.copy(touchAreaScale = it)))
                }
            }
            }
        }

        ConfigGroup.Glass -> {
            val value = config.glass
            val defaults = ReferenceConfig.glass
            item {
            ParameterSlider("内部深度", value.internalDepth, defaults.internalDepth, 0f..40f) {
                onConfigChange(config.copy(glass = value.copy(internalDepth = it)))
            }
            }
            item {
            ParameterSlider("曲率", value.curvature, defaults.curvature, 0f..1f, decimals = 2) {
                onConfigChange(config.copy(glass = value.copy(curvature = it)))
            }
            }
            item {
            ParameterSlider("高光亮度", value.highlightAmount, defaults.highlightAmount, 0f..2f, decimals = 2) {
                onConfigChange(config.copy(glass = value.copy(highlightAmount = it)))
            }
            }
            item {
            ParameterSlider("高光宽度", value.highlightWidth, defaults.highlightWidth, 0.2f..8f, decimals = 2) {
                onConfigChange(config.copy(glass = value.copy(highlightWidth = it)))
            }
            }
            item {
            ParameterSlider("高光收束", value.highlightCut, defaults.highlightCut, 0f..1f, decimals = 2) {
                onConfigChange(config.copy(glass = value.copy(highlightCut = it)))
            }
            }
            item {
            ParameterSlider("阴影", value.shadowAmount, defaults.shadowAmount, 0f..1.5f, decimals = 2) {
                onConfigChange(config.copy(glass = value.copy(shadowAmount = it)))
            }
            }
            item {
            ParameterSlider("焦散", value.causticAmount, defaults.causticAmount, 0f..3f, decimals = 2) {
                onConfigChange(config.copy(glass = value.copy(causticAmount = it)))
            }
            }
            item {
            ParameterSlider("阴影偏移", value.shadowOffset, defaults.shadowOffset, -3f..3f, decimals = 2) {
                onConfigChange(config.copy(glass = value.copy(shadowOffset = it)))
            }
            }
            item {
            ParameterSlider("焦散偏移", value.causticOffset, defaults.causticOffset, -4f..4f, decimals = 2) {
                onConfigChange(config.copy(glass = value.copy(causticOffset = it)))
            }
            }
            item {
            ParameterSlider("光影柔度", value.lightSoftness, defaults.lightSoftness, 0.5f..10f, decimals = 2) {
                onConfigChange(config.copy(glass = value.copy(lightSoftness = it)))
            }
            }
        }

        ConfigGroup.Container -> {
            val value = config.container
            val defaults = ReferenceConfig.container
            item {
            ParameterSlider("强度", value.strength, defaults.strength, 0f..1.5f, decimals = 2) {
                onConfigChange(config.copy(container = value.copy(strength = it)))
            }
            }
            item {
            ParameterSlider("纯黑区域", value.blackLevel, defaults.blackLevel, 0f..1f, decimals = 2) {
                onConfigChange(config.copy(container = value.copy(blackLevel = it)))
            }
            }
            item {
            ParameterSlider("渐隐跨度", value.fade, defaults.fade, 0f..2f, decimals = 2) {
                onConfigChange(config.copy(container = value.copy(fade = it)))
            }
            }
            item {
            ParameterSlider("高斯斜率", value.gaussian, defaults.gaussian, 0.5f..16f, decimals = 1) {
                onConfigChange(config.copy(container = value.copy(gaussian = it)))
            }
            }
        }

        ConfigGroup.Wave -> {
            val value = config.wave
            val defaults = ReferenceConfig.wave
            item {
            ParameterSlider("振幅", value.amplitude, defaults.amplitude, 0f..0.6f, decimals = 3) {
                onConfigChange(config.copy(wave = value.copy(amplitude = it)))
            }
            }
            item {
            ParameterSlider("尺度", value.scale, defaults.scale, 0.4f..1.6f, decimals = 2) {
                onConfigChange(config.copy(wave = value.copy(scale = it)))
            }
            }
            item {
            ParameterSlider("色散", value.chromaticAberration, defaults.chromaticAberration, 0f..8f, decimals = 2) {
                onConfigChange(config.copy(wave = value.copy(chromaticAberration = it)))
            }
            }
            item {
            ParameterSlider("线宽", value.lineWidth, defaults.lineWidth, 0.5f..8f, decimals = 2) {
                onConfigChange(config.copy(wave = value.copy(lineWidth = it)))
            }
            }
            item {
            ParameterSlider("亮度", value.intensity, defaults.intensity, 0f..5f, decimals = 2) {
                onConfigChange(config.copy(wave = value.copy(intensity = it)))
            }
            }
            item {
            ParameterSlider("填光", value.bandFill, defaults.bandFill, 0f..60_000f, decimals = 0) {
                onConfigChange(config.copy(wave = value.copy(bandFill = it)))
            }
            }
            item {
            ParameterSlider("填光厚度", value.bandFillThickness, defaults.bandFillThickness, 0f..0.3f, decimals = 3) {
                onConfigChange(config.copy(wave = value.copy(bandFillThickness = it)))
            }
            }
            item {
            ParameterSlider("柔化", value.softness, defaults.softness, 0.2f..8f, decimals = 2) {
                onConfigChange(config.copy(wave = value.copy(softness = it)))
            }
            }
            item {
            ParameterSlider("白色 Bloom", value.whiteBloom, defaults.whiteBloom, 0f..3f, decimals = 2) {
                onConfigChange(config.copy(wave = value.copy(whiteBloom = it)))
            }
            }
            item {
            ParameterSlider("色相偏移", value.hueShiftDegrees, defaults.hueShiftDegrees, -180f..180f, "°", decimals = 0) {
                onConfigChange(config.copy(wave = value.copy(hueShiftDegrees = it)))
            }
            }
        }

        ConfigGroup.Dots -> {
            val value = config.dots
            val defaults = ReferenceConfig.dots
            item {
            ParameterSlider("环半径", value.ringRadius, defaults.ringRadius, 0.15f..0.75f, decimals = 3) {
                onConfigChange(config.copy(dots = value.copy(ringRadius = it)))
            }
            }
            item {
            ParameterSlider("点半径", value.dotRadius, defaults.dotRadius, 0.025f..0.22f, decimals = 3) {
                onConfigChange(config.copy(dots = value.copy(dotRadius = it)))
            }
            }
            item {
            ParameterSlider("辉光", value.glow, defaults.glow, 0f..0.2f, decimals = 3) {
                onConfigChange(config.copy(dots = value.copy(glow = it)))
            }
            }
            item {
            ParameterSlider("转速", value.rotationSpeed, defaults.rotationSpeed, -3f..3f, decimals = 2) {
                onConfigChange(config.copy(dots = value.copy(rotationSpeed = it)))
            }
            }
        }

        ConfigGroup.Motion -> {
            val value = config.motion
            val defaults = ReferenceConfig.motion
            item {
            ParameterSlider("收起跟手距离", value.collapseRangeDp, defaults.collapseRangeDp, 24f..120f, "dp") {
                onConfigChange(config.copy(motion = value.copy(collapseRangeDp = it)))
            }
            }
            item {
            ParameterSlider("拖拽弹性范围", value.dragRangeDp, defaults.dragRangeDp, 16f..160f, "dp") {
                onConfigChange(config.copy(motion = value.copy(dragRangeDp = it)))
            }
            }
            item {
            ParameterSlider("拖拽响应系数", value.dragResistance, defaults.dragResistance, 0.05f..2f, decimals = 2) {
                onConfigChange(config.copy(motion = value.copy(dragResistance = it)))
            }
            }
            item {
            ParameterSlider("最大轻微位移", value.deformLimitDp, defaults.deformLimitDp, 0f..8f, "dp") {
                onConfigChange(config.copy(motion = value.copy(deformLimitDp = it)))
            }
            }
            item {
            ParameterSlider("形变幅度", value.deformScaleDelta, defaults.deformScaleDelta, 0f..0.02f, decimals = 3) {
                onConfigChange(config.copy(motion = value.copy(deformScaleDelta = it)))
            }
            }
            item {
            ParameterSlider("形变回弹响应", value.deformResponse, defaults.deformResponse, 0.08f..1.5f, "s", decimals = 2) {
                onConfigChange(config.copy(motion = value.copy(deformResponse = it)))
            }
            }
            item {
            ParameterSlider("形变回弹阻尼", value.deformDamping, defaults.deformDamping, 0.1f..1.5f, decimals = 2) {
                onConfigChange(config.copy(motion = value.copy(deformDamping = it)))
            }
            }
            item {
            ParameterSlider("展开响应", value.openResponse, defaults.openResponse, 0.12f..1.2f, "s", decimals = 2) {
                onConfigChange(config.copy(motion = value.copy(openResponse = it)))
            }
            }
            item {
            ParameterSlider("展开阻尼", value.openDamping, defaults.openDamping, 0.2f..1.5f, decimals = 2) {
                onConfigChange(config.copy(motion = value.copy(openDamping = it)))
            }
            }
            item {
            ParameterSlider("收起响应", value.closeResponse, defaults.closeResponse, 0.12f..1.2f, "s", decimals = 2) {
                onConfigChange(config.copy(motion = value.copy(closeResponse = it)))
            }
            }
            item {
            ParameterSlider("收起阻尼", value.closeDamping, defaults.closeDamping, 0.2f..1.5f, decimals = 2) {
                onConfigChange(config.copy(motion = value.copy(closeDamping = it)))
            }
            }
            item {
            ParameterSlider("负向软回弹", value.closeBounce, defaults.closeBounce, 0f..0.12f, decimals = 3) {
                onConfigChange(config.copy(motion = value.copy(closeBounce = it)))
            }
            }
            item {
            ParameterSlider("波形渐入延迟", value.waveFadeDelayMs.toFloat(), defaults.waveFadeDelayMs.toFloat(), 0f..500f, "ms", decimals = 0) {
                onConfigChange(config.copy(motion = value.copy(waveFadeDelayMs = it.toInt())))
            }
            }
            item {
            ParameterSlider("呼吸幅度", value.breathingAmplitude, defaults.breathingAmplitude, 0f..0.08f, decimals = 3) {
                onConfigChange(config.copy(motion = value.copy(breathingAmplitude = it)))
            }
            }
            item {
            ParameterSlider("呼吸速度", value.breathingSpeed, defaults.breathingSpeed, 0f..4f, decimals = 2) {
                onConfigChange(config.copy(motion = value.copy(breathingSpeed = it)))
            }
            }
            item {
            ParameterSlider("点击放大", value.pressScale, defaults.pressScale, 1f..1.08f, decimals = 3) {
                onConfigChange(config.copy(motion = value.copy(pressScale = it)))
            }
            }
            item {
            ParameterSlider("思考停留", value.thinkingDurationMs.toFloat(), defaults.thinkingDurationMs.toFloat(), 300f..5_000f, "ms", decimals = 0) {
                onConfigChange(config.copy(motion = value.copy(thinkingDurationMs = it.toInt())))
            }
            }
            item {
            ToggleRow("自动收起玻璃球", value.autoCollapseEnabled) {
                onConfigChange(config.copy(motion = value.copy(autoCollapseEnabled = it)))
            }
            }
            if (value.autoCollapseEnabled) {
            item {
                ParameterSlider("自动收起倒计时", value.autoCollapseSeconds, defaults.autoCollapseSeconds, 1f..60f, "s", decimals = 1) {
                    onConfigChange(config.copy(motion = value.copy(autoCollapseSeconds = it)))
                }
            }
            }
        }

        ConfigGroup.Performance -> {
            val value = config.performance
            val defaults = ReferenceConfig.performance
            item {
            ParameterSlider("胶囊帧率", value.collapsedFps.toFloat(), defaults.collapsedFps.toFloat(), 24f..120f, "fps", decimals = 0) {
                onConfigChange(config.copy(performance = value.copy(collapsedFps = it.toInt())))
            }
            }
            item {
            ParameterSlider("展开帧率", value.expandedFps.toFloat(), defaults.expandedFps.toFloat(), 24f..120f, "fps", decimals = 0) {
                onConfigChange(config.copy(performance = value.copy(expandedFps = it.toInt())))
            }
            }
            item {
            ParameterSlider("渲染比例", value.renderScale, defaults.renderScale, 0.5f..1.25f, decimals = 2) {
                onConfigChange(config.copy(performance = value.copy(renderScale = it)))
            }
            }
        }
        }
        item {
            SettingsRow("恢复本组默认值", destructive = true, onClick = onReset)
            Text("仅恢复本页参数，其他分组保持当前设置。", Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AnchorChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(),
    )
}

@Composable
private fun ParameterSlider(
    label: String,
    value: Float,
    defaultValue: Float,
    range: ClosedFloatingPointRange<Float>,
    suffix: String = "",
    decimals: Int = 1,
    touchPreview: Boolean = false,
    onValueChange: (Float) -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text(
                buildString {
                    append(String.format(Locale.US, "%.${decimals}f", value))
                    if (suffix.isNotEmpty()) append(' ').append(suffix)
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            val isModified = isParameterModified(value, defaultValue, decimals)
            if (isModified) {
                androidx.compose.material3.TextButton(onClick = { onValueChange(restoreParameter(defaultValue)) }) {
                    Text("还原")
                }
            }
        }
        val context = LocalContext.current
        var active by remember(touchPreview) { mutableStateOf(false) }
        val interactionSource = remember { MutableInteractionSource() }
        LaunchedEffect(interactionSource) {
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is DragInteraction.Start -> active = true
                    is DragInteraction.Stop, is DragInteraction.Cancel -> active = false
                }
            }
        }
        LaunchedEffect(touchPreview, active) {
            if (touchPreview && OverlayRuntime.status.value == OverlayRuntimeStatus.Visible) OrbOverlayService.setTouchPreview(context, active)
        }
        DisposableEffect(touchPreview) {
            onDispose {
                if (touchPreview && OverlayRuntime.status.value == OverlayRuntimeStatus.Visible) {
                    OrbOverlayService.setTouchPreview(context, false)
                }
            }
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range,
            steps = 0,
            interactionSource = interactionSource,
            colors = SliderDefaults.colors(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    androidx.compose.material3.ListItem(
        headlineContent = { Text(label) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onCheckedChange) },
        modifier = Modifier.fillMaxWidth(),
    )
}
