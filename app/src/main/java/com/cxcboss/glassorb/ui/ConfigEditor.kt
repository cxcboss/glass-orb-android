package com.cxcboss.glassorb.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cxcboss.glassorb.data.ConfigGroup
import com.cxcboss.glassorb.model.HorizontalAnchor
import com.cxcboss.glassorb.model.OrbConfig
import java.util.Locale

@Composable
fun ConfigEditor(
    config: OrbConfig,
    onConfigChange: (OrbConfig) -> Unit,
    onResetGroup: (ConfigGroup) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "精细调参",
            modifier = Modifier.padding(horizontal = 2.dp, vertical = 4.dp),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )

        ConfigSection(
            title = "胶囊与位置",
            summary = "118×34 dp 胶囊、球体大小、安全区偏移",
            defaultExpanded = true,
            onReset = { onResetGroup(ConfigGroup.Geometry) },
        ) {
            val value = config.geometry
            Text("水平预设", style = MaterialTheme.typography.labelLarge)
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
            ParameterSlider("胶囊宽度", value.capsuleWidthDp, 72f..220f, "dp") {
                onConfigChange(config.copy(geometry = value.copy(capsuleWidthDp = it)))
            }
            ParameterSlider("胶囊高度", value.capsuleHeightDp, 24f..64f, "dp") {
                onConfigChange(config.copy(geometry = value.copy(capsuleHeightDp = it)))
            }
            ParameterSlider("球体直径", value.orbDiameterDp, 88f..220f, "dp") {
                onConfigChange(config.copy(geometry = value.copy(orbDiameterDp = it)))
            }
            ParameterSlider("外部效果余量", value.outerMarginDp, 8f..48f, "dp") {
                onConfigChange(config.copy(geometry = value.copy(outerMarginDp = it)))
            }
            ParameterSlider("效果画布比例", value.effectScale, 0.9f..1.5f, decimals = 2) {
                onConfigChange(config.copy(geometry = value.copy(effectScale = it)))
            }
            ParameterSlider("顶部偏移", value.verticalOffsetDp, 0f..240f, "dp") {
                onConfigChange(config.copy(geometry = value.copy(verticalOffsetDp = it)))
            }
            ParameterSlider("水平微调", value.horizontalOffsetDp, -200f..200f, "dp") {
                onConfigChange(config.copy(geometry = value.copy(horizontalOffsetDp = it)))
            }
        }

        ConfigSection(
            title = "玻璃",
            summary = "内部扭曲、边缘高光、阴影与焦散",
            defaultExpanded = true,
            onReset = { onResetGroup(ConfigGroup.Glass) },
        ) {
            val value = config.glass
            ParameterSlider("内部深度", value.internalDepth, 0f..40f) {
                onConfigChange(config.copy(glass = value.copy(internalDepth = it)))
            }
            ParameterSlider("曲率", value.curvature, 0f..1f, decimals = 2) {
                onConfigChange(config.copy(glass = value.copy(curvature = it)))
            }
            ParameterSlider("高光亮度", value.highlightAmount, 0f..2f, decimals = 2) {
                onConfigChange(config.copy(glass = value.copy(highlightAmount = it)))
            }
            ParameterSlider("高光宽度", value.highlightWidth, 0.2f..8f, decimals = 2) {
                onConfigChange(config.copy(glass = value.copy(highlightWidth = it)))
            }
            ParameterSlider("高光收束", value.highlightCut, 0f..1f, decimals = 2) {
                onConfigChange(config.copy(glass = value.copy(highlightCut = it)))
            }
            ParameterSlider("阴影", value.shadowAmount, 0f..1.5f, decimals = 2) {
                onConfigChange(config.copy(glass = value.copy(shadowAmount = it)))
            }
            ParameterSlider("焦散", value.causticAmount, 0f..3f, decimals = 2) {
                onConfigChange(config.copy(glass = value.copy(causticAmount = it)))
            }
            ParameterSlider("阴影偏移", value.shadowOffset, -3f..3f, decimals = 2) {
                onConfigChange(config.copy(glass = value.copy(shadowOffset = it)))
            }
            ParameterSlider("焦散偏移", value.causticOffset, -4f..4f, decimals = 2) {
                onConfigChange(config.copy(glass = value.copy(causticOffset = it)))
            }
            ParameterSlider("光影柔度", value.lightSoftness, 0.5f..10f, decimals = 2) {
                onConfigChange(config.copy(glass = value.copy(lightSoftness = it)))
            }
        }

        ConfigSection(
            title = "暗场",
            summary = "顶部纯黑，向下高斯渐隐",
            onReset = { onResetGroup(ConfigGroup.Container) },
        ) {
            val value = config.container
            ParameterSlider("强度", value.strength, 0f..1.5f, decimals = 2) {
                onConfigChange(config.copy(container = value.copy(strength = it)))
            }
            ParameterSlider("纯黑区域", value.blackLevel, 0f..1f, decimals = 2) {
                onConfigChange(config.copy(container = value.copy(blackLevel = it)))
            }
            ParameterSlider("渐隐跨度", value.fade, 0f..2f, decimals = 2) {
                onConfigChange(config.copy(container = value.copy(fade = it)))
            }
            ParameterSlider("高斯斜率", value.gaussian, 0.5f..16f, decimals = 1) {
                onConfigChange(config.copy(container = value.copy(gaussian = it)))
            }
        }

        ConfigSection(
            title = "波形",
            summary = "四层光谱线、色散、填光与白色 Bloom",
            defaultExpanded = true,
            onReset = { onResetGroup(ConfigGroup.Wave) },
        ) {
            val value = config.wave
            ParameterSlider("振幅", value.amplitude, 0f..0.6f, decimals = 3) {
                onConfigChange(config.copy(wave = value.copy(amplitude = it)))
            }
            ParameterSlider("尺度", value.scale, 0.4f..1.6f, decimals = 2) {
                onConfigChange(config.copy(wave = value.copy(scale = it)))
            }
            ParameterSlider("色散", value.chromaticAberration, 0f..8f, decimals = 2) {
                onConfigChange(config.copy(wave = value.copy(chromaticAberration = it)))
            }
            ParameterSlider("线宽", value.lineWidth, 0.5f..8f, decimals = 2) {
                onConfigChange(config.copy(wave = value.copy(lineWidth = it)))
            }
            ParameterSlider("亮度", value.intensity, 0f..5f, decimals = 2) {
                onConfigChange(config.copy(wave = value.copy(intensity = it)))
            }
            ParameterSlider("填光", value.bandFill, 0f..60_000f, decimals = 0) {
                onConfigChange(config.copy(wave = value.copy(bandFill = it)))
            }
            ParameterSlider("填光厚度", value.bandFillThickness, 0f..0.3f, decimals = 3) {
                onConfigChange(config.copy(wave = value.copy(bandFillThickness = it)))
            }
            ParameterSlider("柔化", value.softness, 0.2f..8f, decimals = 2) {
                onConfigChange(config.copy(wave = value.copy(softness = it)))
            }
            ParameterSlider("白色 Bloom", value.whiteBloom, 0f..3f, decimals = 2) {
                onConfigChange(config.copy(wave = value.copy(whiteBloom = it)))
            }
            ParameterSlider("色相偏移", value.hueShiftDegrees, -180f..180f, "°", decimals = 0) {
                onConfigChange(config.copy(wave = value.copy(hueShiftDegrees = it)))
            }
        }

        ConfigSection(
            title = "思考圆点",
            summary = "六组双点环与多色辉光",
            onReset = { onResetGroup(ConfigGroup.Dots) },
        ) {
            val value = config.dots
            ParameterSlider("环半径", value.ringRadius, 0.15f..0.75f, decimals = 3) {
                onConfigChange(config.copy(dots = value.copy(ringRadius = it)))
            }
            ParameterSlider("点半径", value.dotRadius, 0.025f..0.22f, decimals = 3) {
                onConfigChange(config.copy(dots = value.copy(dotRadius = it)))
            }
            ParameterSlider("辉光", value.glow, 0f..0.2f, decimals = 3) {
                onConfigChange(config.copy(dots = value.copy(glow = it)))
            }
            ParameterSlider("转速", value.rotationSpeed, -3f..3f, decimals = 2) {
                onConfigChange(config.copy(dots = value.copy(rotationSpeed = it)))
            }
        }

        ConfigSection(
            title = "动画",
            summary = "展开/收起弹簧、呼吸、按压与思考时长",
            onReset = { onResetGroup(ConfigGroup.Motion) },
        ) {
            val value = config.motion
            ParameterSlider("展开响应", value.openResponse, 0.12f..1.2f, "s", decimals = 2) {
                onConfigChange(config.copy(motion = value.copy(openResponse = it)))
            }
            ParameterSlider("展开阻尼", value.openDamping, 0.2f..1.5f, decimals = 2) {
                onConfigChange(config.copy(motion = value.copy(openDamping = it)))
            }
            ParameterSlider("收起响应", value.closeResponse, 0.12f..1.2f, "s", decimals = 2) {
                onConfigChange(config.copy(motion = value.copy(closeResponse = it)))
            }
            ParameterSlider("收起阻尼", value.closeDamping, 0.2f..1.5f, decimals = 2) {
                onConfigChange(config.copy(motion = value.copy(closeDamping = it)))
            }
            ParameterSlider("负向软回弹", value.closeBounce, 0f..0.12f, decimals = 3) {
                onConfigChange(config.copy(motion = value.copy(closeBounce = it)))
            }
            ParameterSlider("波形渐入延迟", value.waveFadeDelayMs.toFloat(), 0f..500f, "ms", decimals = 0) {
                onConfigChange(config.copy(motion = value.copy(waveFadeDelayMs = it.toInt())))
            }
            ParameterSlider("呼吸幅度", value.breathingAmplitude, 0f..0.08f, decimals = 3) {
                onConfigChange(config.copy(motion = value.copy(breathingAmplitude = it)))
            }
            ParameterSlider("呼吸速度", value.breathingSpeed, 0f..4f, decimals = 2) {
                onConfigChange(config.copy(motion = value.copy(breathingSpeed = it)))
            }
            ParameterSlider("点击放大", value.pressScale, 1f..1.08f, decimals = 3) {
                onConfigChange(config.copy(motion = value.copy(pressScale = it)))
            }
            ParameterSlider("思考停留", value.thinkingDurationMs.toFloat(), 300f..5_000f, "ms", decimals = 0) {
                onConfigChange(config.copy(motion = value.copy(thinkingDurationMs = it.toInt())))
            }
        }

        ConfigSection(
            title = "性能",
            summary = "胶囊/展开帧率与内部渲染比例",
            onReset = { onResetGroup(ConfigGroup.Performance) },
        ) {
            val value = config.performance
            ParameterSlider("胶囊帧率", value.collapsedFps.toFloat(), 10f..60f, "fps", decimals = 0) {
                onConfigChange(config.copy(performance = value.copy(collapsedFps = it.toInt())))
            }
            ParameterSlider("展开帧率", value.expandedFps.toFloat(), 24f..120f, "fps", decimals = 0) {
                onConfigChange(config.copy(performance = value.copy(expandedFps = it.toInt())))
            }
            ParameterSlider("渲染比例", value.renderScale, 0.5f..1.25f, decimals = 2) {
                onConfigChange(config.copy(performance = value.copy(renderScale = it)))
            }
        }
    }
}

@Composable
private fun ConfigSection(
    title: String,
    summary: String,
    defaultExpanded: Boolean = false,
    onReset: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by remember { mutableStateOf(defaultExpanded) }
    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                    Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = onReset) { Text("重置") }
                Text(if (expanded) "−" else "+", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            }
            AnimatedVisibility(expanded) {
                Column(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    HorizontalDivider(Modifier.padding(bottom = 9.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.28f))
                    content()
                }
            }
        }
    }
}

@Composable
private fun AnchorChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

@Composable
private fun ParameterSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    suffix: String = "",
    decimals: Int = 1,
    onValueChange: (Float) -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().semantics { contentDescription = "$label 调节" },
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text(
                buildString {
                    append(String.format(Locale.US, ".${decimals}f", value))
                    if (suffix.isNotEmpty()) append(' ').append(suffix)
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range,
        )
    }
}
