package com.cxcboss.glassorb.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.key
import com.cxcboss.glassorb.data.ConfigGroup
import com.cxcboss.glassorb.data.ConfigPreset
import com.cxcboss.glassorb.model.OrbConfig
import com.cxcboss.glassorb.motion.AmbientBands
import com.cxcboss.glassorb.motion.FrequencyBands
import com.cxcboss.glassorb.overlay.OverlayRuntimeStatus
import com.cxcboss.glassorb.overlay.OverlayState
import com.cxcboss.glassorb.render.OrbTextureView
import com.cxcboss.glassorb.render.RenderSnapshot
import kotlinx.coroutines.isActive

@Composable
fun SettingsScreen(
    config: OrbConfig,
    runtimeStatus: OverlayRuntimeStatus,
    overlayPermission: Boolean,
    onRequestOverlayPermission: () -> Unit,
    onStartOverlay: () -> Unit,
    onShowOverlay: () -> Unit,
    onHideOverlay: () -> Unit,
    onStopOverlay: () -> Unit,
    onConfigChange: (OrbConfig) -> Unit,
    onPreset: (ConfigPreset) -> Unit,
    onResetGroup: (ConfigGroup) -> Unit,
    onResetAll: () -> Unit,
    onExportJson: () -> String,
    onImportJson: (String, (Result<OrbConfig>) -> Unit) -> Unit,
) {
    val context = LocalContext.current
    var importDialogVisible by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf("") }
    var section by rememberSaveable { mutableStateOf(SettingsSection.Overview) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Box(Modifier.fillMaxSize()) {
        key(section) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    text = if (section == SettingsSection.Overview) "灵动玻璃球" else section.label,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = when (section) {
                        SettingsSection.Overview -> "让一点灵动，留在屏幕上。"
                        SettingsSection.Appearance -> "调整大小、位置和光影细节"
                        SettingsSection.Motion -> "找到恰到好处的弹性与回弹"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (section == SettingsSection.Overview) {
        item {
            OverlayControlCard(
                runtimeStatus = runtimeStatus,
                overlayPermission = overlayPermission,
                onRequestOverlayPermission = onRequestOverlayPermission,
                onStartOverlay = onStartOverlay,
                onShowOverlay = onShowOverlay,
                onHideOverlay = onHideOverlay,
                onStopOverlay = onStopOverlay,
            )
        }
        item { OrbPreviewCard(config) }
        item {
            PresetCard(
                onPreset = onPreset,
                onResetAll = onResetAll,
                onExport = {
                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                    clipboard.setPrimaryClip(ClipData.newPlainText("灵动玻璃球参数", onExportJson()))
                    Toast.makeText(context, "JSON 已复制", Toast.LENGTH_SHORT).show()
                },
                onImport = { importDialogVisible = true },
            )
        }
        }
        if (section != SettingsSection.Overview) {
        item {
            ConfigEditor(
                config = config,
                onConfigChange = onConfigChange,
                onResetGroup = onResetGroup,
                section = section,
            )
        }
        }
        if (section == SettingsSection.Motion) {
        item {
            IosGlassCard {
                Column(Modifier.padding(16.dp)) {
                    Text("参数管理", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = {
                            context.getSystemService(ClipboardManager::class.java)
                                .setPrimaryClip(ClipData.newPlainText("灵动玻璃球参数", onExportJson()))
                            Toast.makeText(context, "JSON 已复制", Toast.LENGTH_SHORT).show()
                        }) { Text("复制 JSON") }
                        TextButton(onClick = { importDialogVisible = true }) { Text("导入 JSON") }
                        TextButton(onClick = onResetAll) { Text("全部恢复", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
        item { LimitsCard() }
        item {
            AttributionCard(
                onOpenSource = {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/cxcboss/glass-voice-orb-study")),
                    )
                },
            )
        }
        }
            item { Spacer(Modifier.height(12.dp)) }
        }
        }
        IosFloatingTabBar(section, { section = it }, Modifier.align(Alignment.BottomCenter)
            .navigationBarsPadding().padding(horizontal = 24.dp, vertical = 12.dp))
        }
    }

    if (importDialogVisible) {
        AlertDialog(
            onDismissRequest = { importDialogVisible = false },
            title = { Text("导入参数 JSON") },
            text = {
                OutlinedTextField(
                    value = importText,
                    onValueChange = { importText = it },
                    modifier = Modifier.fillMaxWidth().height(220.dp),
                    label = { Text("schemaVersion = 1") },
                    supportingText = { Text("缺失字段使用默认值，越界数值会自动夹紧") },
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onImportJson(importText) { result ->
                            if (result.isSuccess) {
                                importDialogVisible = false
                                Toast.makeText(context, "参数已导入", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "JSON 解析失败，现有参数未变更", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    enabled = importText.isNotBlank(),
                ) { Text("导入") }
            },
            dismissButton = { TextButton(onClick = { importDialogVisible = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun OverlayControlCard(
    runtimeStatus: OverlayRuntimeStatus,
    overlayPermission: Boolean,
    onRequestOverlayPermission: () -> Unit,
    onStartOverlay: () -> Unit,
    onShowOverlay: () -> Unit,
    onHideOverlay: () -> Unit,
    onStopOverlay: () -> Unit,
) {
    val (statusLabel, statusColor) = when (runtimeStatus) {
        OverlayRuntimeStatus.Stopped -> "未运行" to MaterialTheme.colorScheme.onSurfaceVariant
        OverlayRuntimeStatus.Visible -> "正在显示" to MaterialTheme.colorScheme.secondary
        OverlayRuntimeStatus.Hidden -> "已隐藏" to MaterialTheme.colorScheme.primary
        OverlayRuntimeStatus.PermissionRequired -> "需要权限" to MaterialTheme.colorScheme.error
        is OverlayRuntimeStatus.Error -> "渲染异常" to MaterialTheme.colorScheme.error
    }
    IosGlassCard {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("系统悬浮层", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (overlayPermission) "已获得显示在其他应用上层的权限" else "需要一次系统悬浮窗授权",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Surface(
                    color = statusColor.copy(alpha = 0.14f),
                    contentColor = statusColor,
                    shape = RoundedCornerShape(999.dp),
                ) {
                    Text(statusLabel, Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium)
                }
            }
            if (runtimeStatus is OverlayRuntimeStatus.Error) {
                Text(runtimeStatus.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            if (!overlayPermission) {
                Button(onClick = onRequestOverlayPermission, modifier = Modifier.fillMaxWidth()) {
                    Text("授权并返回")
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = if (runtimeStatus == OverlayRuntimeStatus.Hidden) onShowOverlay else onStartOverlay,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(if (runtimeStatus == OverlayRuntimeStatus.Hidden) "显示" else "启动悬浮层")
                    }
                    OutlinedButton(onClick = onHideOverlay, modifier = Modifier.weight(1f)) { Text("隐藏") }
                }
                TextButton(onClick = onStopOverlay, modifier = Modifier.align(Alignment.End)) { Text("停止常驻服务") }
            }
        }
    }
}

@Composable
private fun OrbPreviewCard(config: OrbConfig) {
    var thinking by remember { mutableStateOf(false) }
    var backdrop by remember { mutableStateOf(0) }
    IosGlassCard {
        Column {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("实时预览", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("光影与参数实时呈现", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                FilterChip(
                    selected = thinking,
                    onClick = { thinking = !thinking },
                    label = { Text(if (thinking) "思考圆点" else "光谱波形") },
                )
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(224.dp),
            ) {
                Canvas(Modifier.fillMaxSize().clipToBounds()) { drawPreviewBackdrop(backdrop) }
                OrbPreview(
                    config = config,
                    thinking = thinking,
                    modifier = Modifier.size(198.dp).align(Alignment.Center),
                )
                Text(
                    "程序化音频驱动 · 无麦克风",
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (backdrop == 2) Color.White.copy(alpha = 0.56f) else Color(0xFF5C6070),
                )
            }
            Row(Modifier.padding(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("棋盘", "纯白", "深色").forEachIndexed { index, label ->
                    FilterChip(selected = backdrop == index, onClick = { backdrop = index }, label = { Text(label) })
                }
            }
        }
    }
}

@Composable
private fun OrbPreview(config: OrbConfig, thinking: Boolean, modifier: Modifier = Modifier) {
    var renderer by remember { mutableStateOf<OrbTextureView?>(null) }
    var renderError by remember { mutableStateOf<String?>(null) }
    val thinkingProgress by animateFloatAsState(
        targetValue = if (thinking) 1f else 0f,
        animationSpec = tween(420),
        label = "previewThinking",
    )
    val latestConfig = rememberUpdatedState(config)
    val latestThinking = rememberUpdatedState(thinking)
    val latestThinkingProgress = rememberUpdatedState(thinkingProgress)

    Box(modifier) {
        AndroidView(
            factory = { context ->
                OrbTextureView(context).also {
                    it.onRenderFailure = { error -> renderError = error.message ?: "GLES 渲染失败" }
                    renderer = it
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        renderError?.let { message ->
            Text(
                text = message,
                modifier = Modifier.align(Alignment.Center).padding(12.dp),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
    DisposableEffect(Unit) {
        onDispose { renderer?.setPaused(true) }
    }
    LaunchedEffect(renderer) {
        val target = renderer ?: return@LaunchedEffect
        var origin = 0L
        var bands = FrequencyBands(0.35f, 0.4f, 0.3f)
        var previousTime = 0f
        var previousSubmitNanos = 0L
        var wavePhase = 0f
        while (isActive) {
            withFrameNanos { frameTime ->
                if (previousSubmitNanos != 0L && frameTime - previousSubmitNanos < 16_000_000L) return@withFrameNanos
                previousSubmitNanos = frameTime
                if (origin == 0L) origin = frameTime
                val time = ((frameTime - origin) / 1_000_000_000.0).toFloat()
                bands = AmbientBands.smooth(bands, AmbientBands.targetsAt(time))
                wavePhase = AmbientBands.advanceWavePhase(wavePhase, bands, (time - previousTime).coerceAtMost(0.05f))
                previousTime = time
                target.submit(
                    RenderSnapshot(
                        config = latestConfig.value,
                        state = if (latestThinking.value) OverlayState.Thinking else OverlayState.Wave,
                        springProgress = 1f,
                        thinkingProgress = latestThinkingProgress.value,
                        timeSeconds = time,
                        wavePhase = wavePhase,
                        stateElapsedSeconds = time,
                        bands = bands,
                        preview = true,
                    ),
                )
            }
        }
    }
}

private fun DrawScope.drawPreviewBackdrop(backdrop: Int) {
    if (backdrop != 2) {
        drawRect(if (backdrop == 1) Color.White else Color(0xFFE7E9ED))
        if (backdrop == 1) return
    } else {
    drawRect(
        brush = Brush.linearGradient(
            colors = listOf(Color(0xFF293352), Color(0xFF14182A), Color(0xFF463148)),
            start = Offset.Zero,
            end = Offset(size.width, size.height),
        ),
    )
    }
    val step = 24.dp.toPx()
    var row = 0
    var y = 0f
    while (y < size.height) {
        var column = 0
        var x = 0f
        while (x < size.width) {
            if ((row + column) % 2 == 0) {
                drawRect(if (backdrop == 2) Color.White.copy(alpha = 0.035f) else Color(0xFFCBD0D8),
                    Offset(x, y), androidx.compose.ui.geometry.Size(step, step))
            }
            column += 1
            x += step
        }
        row += 1
        y += step
    }
}

@Composable
private fun PresetCard(
    onPreset: (ConfigPreset) -> Unit,
    onResetAll: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    IosGlassCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("预设与参数", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = { onPreset(ConfigPreset.Reference) }) { Text("参考原版") }
                OutlinedButton(onClick = { onPreset(ConfigPreset.Soft) }) { Text("柔和") }
                OutlinedButton(onClick = { onPreset(ConfigPreset.Bright) }) { Text("明亮") }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onExport, modifier = Modifier.weight(1f)) { Text("复制 JSON") }
                TextButton(onClick = onImport, modifier = Modifier.weight(1f)) { Text("导入 JSON") }
                TextButton(onClick = onResetAll, modifier = Modifier.weight(1f)) { Text("全部恢复") }
            }
        }
    }
}

@Composable
private fun LimitsCard() {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.34f))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("透明不等于折射", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                "本演示不录屏、不读取下层 App；折射只作用于球内生成的暗场、波形与圆点。透明区域会原样显示底下内容。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AttributionCard(onOpenSource: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("非官方、非商业学习演示", style = MaterialTheme.typography.labelLarge)
        Text(
            "Shader 与动画行为参考 glass-voice-orb-study @ 3d7e981；Apple 和 Siri 是 Apple Inc. 的商标。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onOpenSource, contentPadding = PaddingValues(0.dp)) { Text("查看参考仓库与来源说明") }
    }
}
