package com.cxcboss.glassorb.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.cxcboss.glassorb.BuildConfig
import com.cxcboss.glassorb.data.ConfigGroup
import com.cxcboss.glassorb.data.ConfigPreset
import com.cxcboss.glassorb.model.OrbConfig
import com.cxcboss.glassorb.overlay.OverlayRuntimeStatus
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.delay

private val NavigationSaver = listSaver<SettingsNavigator, String>(
    save = { it.save() }, restore = { SettingsNavigator.restore(it) },
)
private val GroupShape = RoundedCornerShape(12.dp)

fun ConfigGroup.title(): String = when (this) {
    ConfigGroup.Geometry -> "胶囊与位置"
    ConfigGroup.Glass -> "玻璃"
    ConfigGroup.Container -> "暗场"
    ConfigGroup.Wave -> "波形"
    ConfigGroup.Dots -> "思考圆点"
    ConfigGroup.Motion -> "动画"
    ConfigGroup.Performance -> "性能"
}

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
    var navigator by rememberSaveable(stateSaver = NavigationSaver) { mutableStateOf(SettingsNavigator()) }
    var direction by remember { mutableIntStateOf(1) }
    var confirmation by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    var feedback by remember { mutableStateOf<String?>(null) }
    val push: (SettingsRoute) -> Unit = { direction = 1; navigator = navigator.push(it) }
    val pop: () -> Unit = { direction = -1; navigator = navigator.pop() }
    BackHandler(enabled = navigator.depth > 1, onBack = pop)
    LaunchedEffect(feedback) { if (feedback != null) { delay(3500); feedback = null } }
    val route = navigator.current
    val slide = remember { Animatable(0f) }
    var previousRoute by remember { mutableStateOf(route) }
    LaunchedEffect(route) {
        if (route != previousRoute) {
            slide.snapTo(if (direction > 0) 1f else -.28f)
            previousRoute = route
            slide.animateTo(0f, tween(360, easing = CubicBezierEasing(.32f, .72f, 0f, 1f)))
        }
    }
    val backdrop = rememberLayerBackdrop()
    val stateHolder = rememberSaveableStateHolder()
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
      Box(Modifier.fillMaxSize()) {
        // A single screen-level recording layer. Controls may also record their tiny tracks.
        Box(Modifier.fillMaxSize().layerBackdrop(backdrop).background(MaterialTheme.colorScheme.background))
        CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
            Column(Modifier.align(Alignment.TopCenter).widthIn(max = 680.dp).fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
                if (route != SettingsRoute.Home) {
                    Box(Modifier.fillMaxWidth().height(56.dp), contentAlignment = Alignment.Center) {
                        LiquidGlassTextButton(onClick = pop, modifier = Modifier.align(Alignment.CenterStart).padding(start = 12.dp)) {
                            Text("‹ 返回", color = MaterialTheme.colorScheme.primary)
                        }
                        Text(when (route) {
                            SettingsRoute.OverlayDetails -> "悬浮球"
                            is SettingsRoute.ConfigGroupDetail -> route.group.title()
                            SettingsRoute.Presets -> "预设"
                            SettingsRoute.DataManagement -> "导入与导出"
                            SettingsRoute.About -> "关于"
                        }, fontWeight = FontWeight.SemiBold, fontSize = 17.sp,
                            modifier = Modifier.semantics { heading() })
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
                    Box(Modifier.fillMaxSize().graphicsLayer { translationX = size.width * slide.value }) {
                        stateHolder.SaveableStateProvider(navigator.save().last()) {
                            when (route) {
                                is SettingsRoute.ConfigGroupDetail -> ConfigEditor(config, onConfigChange, route.group) {
                                    confirmation = "恢复${route.group.title()}的默认值？" to {
                                        onResetGroup(route.group); feedback = "已恢复本组默认值"
                                    }
                                }
                                else -> LazyColumn(
                                    Modifier.fillMaxSize().testTag("settings-list"),
                                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 32.dp),
                                    verticalArrangement = Arrangement.spacedBy(24.dp),
                                ) {
                                    when (route) {
                                        SettingsRoute.Home -> {
                                            item { Text("灵动玻璃球", fontSize = 34.sp, fontWeight = FontWeight.Bold,
                                                modifier = Modifier.semantics { heading() }) }
                                            item { SettingsGroup("悬浮球") {
                                                ToggleRow("显示悬浮球", runtimeStatus == OverlayRuntimeStatus.Visible) { enabled ->
                                                    when (resolveOverlayAction(enabled, overlayPermission, runtimeStatus)) {
                                                        OverlayAction.RequestPermission -> onRequestOverlayPermission()
                                                        OverlayAction.Show -> onShowOverlay()
                                                        OverlayAction.Start -> onStartOverlay()
                                                        OverlayAction.Hide -> onHideOverlay()
                                                        OverlayAction.None -> Unit
                                                    }
                                                }
                                                SettingsRow("运行与权限", statusLabel(runtimeStatus)) { push(SettingsRoute.OverlayDetails) }
                                            } }
                                            item { SettingsGroup("外观") {
                                                listOf(ConfigGroup.Geometry, ConfigGroup.Glass, ConfigGroup.Container, ConfigGroup.Wave, ConfigGroup.Dots).forEach { group ->
                                                    SettingsRow(group.title()) { push(SettingsRoute.ConfigGroupDetail(group)) }
                                                }
                                            } }
                                            item { SettingsGroup("交互") {
                                                listOf(ConfigGroup.Motion, ConfigGroup.Performance).forEach { group ->
                                                    SettingsRow(group.title()) { push(SettingsRoute.ConfigGroupDetail(group)) }
                                                }
                                            } }
                                            item { SettingsGroup("参数") {
                                                SettingsRow("预设") { push(SettingsRoute.Presets) }
                                                SettingsRow("导入与导出") { push(SettingsRoute.DataManagement) }
                                                SettingsRow("全部恢复", destructive = true) {
                                                    confirmation = "恢复全部默认参数？" to { onResetAll(); feedback = "已恢复全部默认参数" }
                                                }
                                            } }
                                            item { SettingsGroup("关于") {
                                                SettingsRow("效果边界、参考来源与许可证") { push(SettingsRoute.About) }
                                            } }
                                        }
                                        SettingsRoute.OverlayDetails -> {
                                            item { SettingsGroup("状态") {
                                                SettingsRow("运行状态", statusLabel(runtimeStatus))
                                                SettingsRow("悬浮权限", if (overlayPermission) "已允许" else "未允许") { onRequestOverlayPermission() }
                                                if (runtimeStatus is OverlayRuntimeStatus.Error) Note(runtimeStatus.message)
                                            } }
                                            item { SettingsGroup("操作") {
                                                SettingsRow(if (overlayPermission) "启动悬浮层" else "授权并返回") {
                                                    if (overlayPermission) onStartOverlay() else onRequestOverlayPermission()
                                                }
                                                SettingsRow("显示悬浮层") { if (overlayPermission) onShowOverlay() else onRequestOverlayPermission() }
                                                SettingsRow("隐藏悬浮层", onClick = onHideOverlay)
                                                SettingsRow("停止悬浮层", destructive = true) {
                                                    confirmation = "停止悬浮球服务？" to onStopOverlay
                                                }
                                            } }
                                            item { Note("首页开关关闭时仅隐藏悬浮球。停止会结束服务；重新开启总开关即可启动。系统状态栏、安全页面与锁屏不保证覆盖。") }
                                        }
                                        SettingsRoute.Presets -> {
                                            item { Note("应用预设会替换全部参数。可先到「导入与导出」复制当前 JSON 备份。") }
                                            item { SettingsGroup("预设") {
                                                listOf("参考原版" to ConfigPreset.Reference, "柔和" to ConfigPreset.Soft, "明亮" to ConfigPreset.Bright).forEach { (name, preset) ->
                                                    SettingsRow(name) {
                                                        confirmation = "应用「$name」并替换当前参数？" to { onPreset(preset); feedback = "已应用$name" }
                                                    }
                                                }
                                            } }
                                        }
                                        SettingsRoute.DataManagement -> {
                                            item { SettingsGroup("导出") {
                                                SettingsRow("复制 JSON") {
                                                    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(
                                                        ClipData.newPlainText("灵动玻璃球参数", onExportJson()))
                                                    feedback = "JSON 已复制"
                                                }
                                            } }
                                            item {
                                                var importText by rememberSaveable { mutableStateOf("") }
                                                var error by rememberSaveable { mutableStateOf<String?>(null) }
                                                SettingsGroup("导入 JSON") {
                                                    BasicTextField(
                                                        value = importText, onValueChange = { importText = it; error = null },
                                                        modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp, max = 380.dp)
                                                            .padding(16.dp).semantics { contentDescription = "参数 JSON" },
                                                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                                                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                                        decorationBox = { inner ->
                                                            Box { if (importText.isEmpty()) Text("在这里粘贴参数 JSON", color = MaterialTheme.colorScheme.onSurfaceVariant); inner() }
                                                        },
                                                    )
                                                }
                                                Note(error ?: "schemaVersion = 1。缺失字段使用默认值，越界数值自动夹紧。")
                                                LiquidGlassButton(onClick = {
                                                    if (importText.isBlank()) error = "请先粘贴参数 JSON"
                                                    else onImportJson(importText) { result ->
                                                        if (result.isSuccess) { error = null; feedback = "参数已导入" }
                                                        else error = "JSON 解析失败，现有参数未变更"
                                                    }
                                                }, modifier = Modifier.fillMaxWidth()) { Text("导入参数", color = Color.White) }
                                            }
                                        }
                                        SettingsRoute.About -> {
                                            item { SettingsGroup("灵动玻璃球") { Note("版本 ${BuildConfig.VERSION_NAME} · 非官方、非商业学习演示") } }
                                            item { SettingsGroup("效果边界") {
                                                Note("本演示不录屏、不读取下层 App；折射只作用于球内生成的暗场、波形与圆点。透明区域原样显示底下内容。没有语音助手、麦克风或后台录音功能。")
                                            } }
                                            item { SettingsGroup("参考来源") {
                                                Note("Shader 与动画参考 glass-voice-orb-study @ 3d7e981。Apple 和 Siri 是 Apple Inc. 的商标。")
                                                SettingsRow("查看效果参考仓库") { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/cxcboss/glass-voice-orb-study"))) }
                                                Note("设置控件使用 Kyant AndroidLiquidGlass @ 65ab177 与 Backdrop 2.0.1；本版本修补状态同步、触摸范围与尺寸。")
                                                SettingsRow("查看 AndroidLiquidGlass") { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Kyant0/AndroidLiquidGlass"))) }
                                            } }
                                            item { SettingsGroup("许可证") {
                                                Note("AndroidLiquidGlass：Apache License 2.0。效果参考的归属与非商业要求见项目 LICENSE / NOTICE。")
                                                val license = remember { context.assets.open("AndroidLiquidGlass-LICENSE.txt").bufferedReader().use { it.readText() } }
                                                Note(license)
                                            } }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            feedback?.let { message ->
                Text(message, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(20.dp)
                    .background(MaterialTheme.colorScheme.surface, GroupShape).padding(16.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite }, color = MaterialTheme.colorScheme.onSurface)
            }
            confirmation?.let { (title, action) ->
                Dialog(onDismissRequest = { confirmation = null }) {
                    Column(Modifier.widthIn(max = 320.dp).fillMaxWidth().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))) {
                        Text(title, Modifier.padding(24.dp), fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .25f))
                        Row(Modifier.fillMaxWidth()) {
                            Text("取消", Modifier.weight(1f).clickable { confirmation = null }.padding(18.dp), color = MaterialTheme.colorScheme.primary)
                            Text("确认", Modifier.weight(1f).clickable { confirmation = null; action() }.padding(18.dp), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
      }
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(title, Modifier.padding(start = 16.dp, bottom = 7.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, GroupShape), content = content)
    }
}

@Composable
fun SettingsRow(title: String, detail: String? = null, destructive: Boolean = false, onClick: (() -> Unit)? = null) {
    Column {
        Row(Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = 50.dp).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), fontSize = 17.sp, color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            if (detail != null) Text(detail, Modifier.padding(start = 8.dp), fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (onClick != null && !destructive) Text("›", Modifier.padding(start = 10.dp), fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider(Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = .18f), thickness = .5.dp)
    }
}

@Composable
private fun Note(text: String) {
    Text(text, Modifier.padding(16.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun statusLabel(status: OverlayRuntimeStatus): String = when (status) {
    OverlayRuntimeStatus.Stopped -> "未运行"
    OverlayRuntimeStatus.Visible -> "正在显示"
    OverlayRuntimeStatus.Hidden -> "已隐藏"
    OverlayRuntimeStatus.PermissionRequired -> "需要权限"
    is OverlayRuntimeStatus.Error -> "运行异常"
}
