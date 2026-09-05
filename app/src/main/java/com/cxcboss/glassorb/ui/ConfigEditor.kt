package com.cxcboss.glassorb.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.LocalContext
import com.cxcboss.glassorb.overlay.OrbOverlayService
import com.cxcboss.glassorb.overlay.OverlayRuntime
import com.cxcboss.glassorb.overlay.OverlayRuntimeStatus
import com.cxcboss.glassorb.data.ConfigGroup
import com.cxcboss.glassorb.model.HorizontalAnchor
import com.cxcboss.glassorb.model.OrbConfig
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.kyant.backdrop.catalog.components.LiquidSlider
import com.kyant.backdrop.catalog.components.LiquidToggle
import kotlin.math.pow

@Composable
fun ConfigEditor(
    config: OrbConfig,
    onConfigChange: (OrbConfig) -> Unit,
    onResetGroup: (ConfigGroup) -> Unit,
    section: SettingsSection = SettingsSection.Appearance,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            if (section == SettingsSection.Motion) "手感与性能" else "细节与外观",
            modifier = Modifier.padding(horizontal = 2.dp, vertical = 4.dp),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )

        ConfigSection(
            title = "胶囊与位置",
            visible = section == SettingsSection.Appearance,
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
            ParameterSlider("胶囊宽度", value.capsuleWidthDp, 24f..220f, "dp") {
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
            Text("0 dp = 真实物理屏幕顶边", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ParameterSlider("水平微调", value.horizontalOffsetDp, -200f..200f, "dp") {
                onConfigChange(config.copy(geometry = value.copy(horizontalOffsetDp = it)))
            }
            ToggleRow("扩大胶囊触摸区域", value.enlargedTouchArea) {
                onConfigChange(config.copy(geometry = value.copy(enlargedTouchArea = it)))
            }
            if (value.enlargedTouchArea) {
                ParameterSlider("触摸区域倍率", value.touchAreaScale, 1f..3f, "×", decimals = 2) {
                    onConfigChange(config.copy(geometry = value.copy(touchAreaScale = it)))
                }
            }
        }

        ConfigSection(
            title = "玻璃",
            visible = section == SettingsSection.Appearance,
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
            visible = section == SettingsSection.Appearance,
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
            visible = section == SettingsSection.Appearance,
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
            visible = section == SettingsSection.Appearance,
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
            visible = section == SettingsSection.Motion,
            defaultExpanded = true,
            summary = "展开/收起弹簧、呼吸、按压与思考时长",
            onReset = { onResetGroup(ConfigGroup.Motion) },
        ) {
            val value = config.motion
            ParameterSlider("收起跟手距离", value.collapseRangeDp, 24f..120f, "dp") {
                onConfigChange(config.copy(motion = value.copy(collapseRangeDp = it)))
            }
            ParameterSlider("拖拽弹性范围", value.dragRangeDp, 16f..160f, "dp") {
                onConfigChange(config.copy(motion = value.copy(dragRangeDp = it)))
            }
            ParameterSlider("拖拽响应系数", value.dragResistance, 0.05f..2f, decimals = 2) {
                onConfigChange(config.copy(motion = value.copy(dragResistance = it)))
            }
            ParameterSlider("最大轻微位移", value.deformLimitDp, 0f..8f, "dp") {
                onConfigChange(config.copy(motion = value.copy(deformLimitDp = it)))
            }
            ParameterSlider("形变幅度", value.deformScaleDelta, 0f..0.02f, decimals = 3) {
                onConfigChange(config.copy(motion = value.copy(deformScaleDelta = it)))
            }
            ParameterSlider("形变回弹响应", value.deformResponse, 0.08f..1.5f, "s", decimals = 2) {
                onConfigChange(config.copy(motion = value.copy(deformResponse = it)))
            }
            ParameterSlider("形变回弹阻尼", value.deformDamping, 0.1f..1.5f, decimals = 2) {
                onConfigChange(config.copy(motion = value.copy(deformDamping = it)))
            }
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
            ToggleRow("自动收起玻璃球", value.autoCollapseEnabled) {
                onConfigChange(config.copy(motion = value.copy(autoCollapseEnabled = it)))
            }
            if (value.autoCollapseEnabled) {
                ParameterSlider("自动收起倒计时", value.autoCollapseSeconds, 1f..60f, "s", decimals = 1) {
                    onConfigChange(config.copy(motion = value.copy(autoCollapseSeconds = it)))
                }
            }
        }

        ConfigSection(
            title = "性能",
            visible = section == SettingsSection.Motion,
            summary = "胶囊/展开帧率与内部渲染比例",
            onReset = { onResetGroup(ConfigGroup.Performance) },
        ) {
            val value = config.performance
            ParameterSlider("胶囊帧率", value.collapsedFps.toFloat(), 24f..120f, "fps", decimals = 0) {
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
    visible: Boolean = true,
    @Suppress("UNUSED_PARAMETER") defaultExpanded: Boolean = false,
    onReset: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!visible) return
    var expanded by remember { mutableStateOf(false) }
    IosGlassCard(Modifier.fillMaxWidth().clickable { expanded = true }) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("›", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (expanded) {
        val scope = rememberCoroutineScope()
        var closing by remember { mutableStateOf(false) }
        var entered by remember { mutableStateOf(false) }
        val dragOffset = remember { Animatable(0f) }
        fun closeSheet() {
            if (closing) return
            closing = true
            entered = false
            scope.launch {
                delay(260)
                expanded = false
            }
        }
        Dialog(onDismissRequest = ::closeSheet, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            LaunchedEffect(Unit) { entered = true }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                AnimatedVisibility(
                    entered,
                    enter = slideInVertically(tween(320), initialOffsetY = { it }),
                    exit = slideOutVertically(tween(240), targetOffsetY = { it }),
                ) {
                    androidx.compose.material3.Surface(
                        Modifier.fillMaxWidth().fillMaxHeight(0.94f).graphicsLayer { translationY = dragOffset.value },
                        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
                        color = MaterialTheme.colorScheme.background,
                        tonalElevation = 8.dp,
                    ) {
                        Column {
                            Box(
                                Modifier.fillMaxWidth().height(30.dp)
                                    .pointerInput(Unit) {
                                        detectVerticalDragGestures(
                                            onVerticalDrag = { _, amount ->
                                                scope.launch { dragOffset.snapTo((dragOffset.value + amount).coerceAtLeast(0f)) }
                                            },
                                            onDragEnd = {
                                                if (dragOffset.value > 72.dp.toPx()) closeSheet()
                                                else scope.launch { dragOffset.animateTo(0f, spring(.78f, 520f)) }
                                            },
                                            onDragCancel = { scope.launch { dragOffset.animateTo(0f, spring(.78f, 520f)) } },
                                        )
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(Modifier.size(42.dp, 5.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = .5f), CircleShape))
                            }
                            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 10.dp, top = 10.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                LiquidGlassTextButton(onClick = ::closeSheet) { Text("完成") }
                                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                                LiquidGlassTextButton(onClick = onReset) { Text("全部复位") }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .2f))
                            Column(
                                Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) { content() }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnchorChip(label: String, selected: Boolean, onClick: () -> Unit) {
    LiquidGlassButton(
        onClick = onClick,
        tint = if (selected) MaterialTheme.colorScheme.primary else Color.Unspecified,
    ) {
        Text(label, color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface)
    }
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
                    append(String.format(Locale.US, "%.${decimals}f", value))
                    if (suffix.isNotEmpty()) append(' ').append(suffix)
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            LiquidGlassTextButton(onClick = { onValueChange(defaultValueFor(label)) }) { Text("复位") }
        }
        val context = LocalContext.current
        var active by remember(label) { mutableStateOf(false) }
        LaunchedEffect(label, active) {
            if (label == "触摸区域倍率" && OverlayRuntime.status.value == OverlayRuntimeStatus.Visible) {
                OrbOverlayService.setTouchPreview(context, active)
            }
        }
        DisposableEffect(label) {
            onDispose {
                if (label == "触摸区域倍率" && OverlayRuntime.status.value == OverlayRuntimeStatus.Visible) {
                    OrbOverlayService.setTouchPreview(context, false)
                }
            }
        }
        val backdrop = LocalGlassBackdrop.current
        val initial = ((defaultValueFor(label) - range.start) /
            (range.endInclusive - range.start)).coerceIn(0f, 1f)
        val defaultMarkerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = .48f)
        Box(
            Modifier
                .fillMaxWidth()
                .height(30.dp)
                .observePressState(label == "触摸区域倍率") { active = it },
            contentAlignment = Alignment.Center,
        ) {
            LiquidSlider(
                value = { value.coerceIn(range.start, range.endInclusive) },
                onValueChange = onValueChange,
                valueRange = range,
                visibilityThreshold = (10.0.pow(-(decimals + 2)).toFloat()).coerceAtLeast(0.000001f),
                backdrop = backdrop,
                modifier = Modifier.fillMaxWidth(),
            )
            Canvas(Modifier.fillMaxWidth().height(24.dp)) {
                val x = size.width * initial
                val y = size.height / 2f
                drawLine(
                    color = defaultMarkerColor,
                    start = Offset(x, y - 4.dp.toPx()),
                    end = Offset(x, y + 4.dp.toPx()),
                    strokeWidth = 1.dp.toPx(),
                )
            }
        }
    }
}

private fun Modifier.observePressState(enabled: Boolean, onChanged: (Boolean) -> Unit): Modifier {
    if (!enabled) return this
    return pointerInput(onChanged) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            onChanged(true)
            try {
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                } while (event.changes.any { it.pressed })
            } finally {
                onChanged(false)
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        LiquidToggle(
            selected = { checked },
            onSelect = onCheckedChange,
            backdrop = LocalGlassBackdrop.current,
        )
    }
}

private fun defaultValueFor(label: String): Float {
    val c = OrbConfig.reference()
    return when (label) {
        "胶囊宽度" -> c.geometry.capsuleWidthDp; "胶囊高度" -> c.geometry.capsuleHeightDp
        "球体直径" -> c.geometry.orbDiameterDp; "外部效果余量" -> c.geometry.outerMarginDp
        "效果画布比例" -> c.geometry.effectScale; "顶部偏移" -> c.geometry.verticalOffsetDp
        "水平微调" -> c.geometry.horizontalOffsetDp; "触摸区域倍率" -> c.geometry.touchAreaScale
        "内部深度" -> c.glass.internalDepth; "曲率" -> c.glass.curvature
        "高光亮度" -> c.glass.highlightAmount; "高光宽度" -> c.glass.highlightWidth
        "高光收束" -> c.glass.highlightCut; "阴影" -> c.glass.shadowAmount
        "焦散" -> c.glass.causticAmount; "阴影偏移" -> c.glass.shadowOffset
        "焦散偏移" -> c.glass.causticOffset; "光影柔度" -> c.glass.lightSoftness
        "强度" -> c.container.strength; "纯黑区域" -> c.container.blackLevel
        "渐隐跨度" -> c.container.fade; "高斯斜率" -> c.container.gaussian
        "振幅" -> c.wave.amplitude; "尺度" -> c.wave.scale; "色散" -> c.wave.chromaticAberration
        "线宽" -> c.wave.lineWidth; "亮度" -> c.wave.intensity; "填光" -> c.wave.bandFill
        "填光厚度" -> c.wave.bandFillThickness; "柔化" -> c.wave.softness
        "白色 Bloom" -> c.wave.whiteBloom; "色相偏移" -> c.wave.hueShiftDegrees
        "环半径" -> c.dots.ringRadius; "点半径" -> c.dots.dotRadius
        "辉光" -> c.dots.glow; "转速" -> c.dots.rotationSpeed
        "收起跟手距离" -> c.motion.collapseRangeDp; "拖拽弹性范围" -> c.motion.dragRangeDp
        "拖拽响应系数" -> c.motion.dragResistance; "最大轻微位移" -> c.motion.deformLimitDp
        "形变幅度" -> c.motion.deformScaleDelta; "形变回弹响应" -> c.motion.deformResponse
        "形变回弹阻尼" -> c.motion.deformDamping; "展开响应" -> c.motion.openResponse
        "展开阻尼" -> c.motion.openDamping; "收起响应" -> c.motion.closeResponse
        "收起阻尼" -> c.motion.closeDamping; "负向软回弹" -> c.motion.closeBounce
        "波形渐入延迟" -> c.motion.waveFadeDelayMs.toFloat(); "呼吸幅度" -> c.motion.breathingAmplitude
        "呼吸速度" -> c.motion.breathingSpeed; "点击放大" -> c.motion.pressScale
        "思考停留" -> c.motion.thinkingDurationMs.toFloat(); "自动收起倒计时" -> c.motion.autoCollapseSeconds
        "胶囊帧率" -> c.performance.collapsedFps.toFloat(); "展开帧率" -> c.performance.expandedFps.toFloat()
        "渲染比例" -> c.performance.renderScale
        else -> 0f
    }
}
