package com.cxcboss.glassorb.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.cxcboss.glassorb.BuildConfig
import com.cxcboss.glassorb.data.ConfigGroup
import com.cxcboss.glassorb.data.ConfigPreset
import com.cxcboss.glassorb.model.OrbConfig
import com.cxcboss.glassorb.overlay.OverlayRuntimeStatus
import kotlinx.coroutines.launch
import androidx.compose.material3.ExperimentalMaterial3Api

private object Destinations {
    const val Home = "home"
    const val Overlay = "overlay"
    const val Group = "group/{group}"
    const val Presets = "presets"
    const val Data = "data"
    const val About = "about"

    fun group(group: ConfigGroup) = routeFor(group)
}

fun ConfigGroup.title(): String = when (this) {
    ConfigGroup.Geometry -> "胶囊与位置"
    ConfigGroup.Glass -> "玻璃"
    ConfigGroup.Container -> "暗场"
    ConfigGroup.Wave -> "波形"
    ConfigGroup.Dots -> "思考圆点"
    ConfigGroup.Motion -> "动画"
    ConfigGroup.Performance -> "性能"
}

@OptIn(ExperimentalMaterial3Api::class)
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
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var resetAllConfirmation by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            if (currentRoute == Destinations.Home || currentRoute == null) {
                LargeTopAppBar(
                    title = { Text("灵动玻璃球") },
                )
            } else {
                TopAppBar(
                    title = { Text(titleForRoute(currentRoute, backStackEntry?.arguments?.getString("group"))) },
                    navigationIcon = {
                        IconButton(
                            onClick = { navController.navigateUp() },
                            modifier = Modifier.semantics { contentDescription = "返回" },
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        }
                    },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Destinations.Home,
            modifier = Modifier.padding(padding),
        ) {
            composable(Destinations.Home) {
                HomeScreen(
                    runtimeStatus = runtimeStatus,
                    overlayPermission = overlayPermission,
                    onToggleOverlay = { enabled ->
                        when (resolveOverlayAction(enabled, overlayPermission, runtimeStatus)) {
                            OverlayAction.RequestPermission -> onRequestOverlayPermission()
                            OverlayAction.Show -> onShowOverlay()
                            OverlayAction.Start -> onStartOverlay()
                            OverlayAction.Hide -> onHideOverlay()
                            OverlayAction.None -> Unit
                        }
                    },
                    onNavigate = navController::navigateSingleTop,
                    onResetAll = { resetAllConfirmation = true },
                )
            }
            composable(Destinations.Overlay) {
                OverlayDetailsScreen(
                    runtimeStatus = runtimeStatus,
                    overlayPermission = overlayPermission,
                    onRequestOverlayPermission = onRequestOverlayPermission,
                    onStartOverlay = onStartOverlay,
                    onShowOverlay = onShowOverlay,
                    onHideOverlay = onHideOverlay,
                    onStopOverlay = onStopOverlay,
                )
            }
            composable(Destinations.Group) { entry ->
                val group = entry.arguments?.getString("group")
                    ?.let { name -> ConfigGroup.entries.firstOrNull { it.name == name } }
                if (group != null) {
                    ConfigEditor(config, onConfigChange, group) {
                        onResetGroup(group)
                        scope.launch { snackbarHostState.showSnackbar("已恢复本组默认值") }
                    }
                }
            }
            composable(Destinations.Presets) {
                PresetsScreen(onPreset = { preset, name ->
                    onPreset(preset)
                    scope.launch { snackbarHostState.showSnackbar("已应用$name") }
                })
            }
            composable(Destinations.Data) {
                DataManagementScreen(
                    onExportJson = {
                        val json = onExportJson()
                        val clipboard = context.getSystemService(ClipboardManager::class.java)
                        clipboard.setPrimaryClip(ClipData.newPlainText("灵动玻璃球参数", json))
                        scope.launch { snackbarHostState.showSnackbar("JSON 已复制") }
                    },
                    onImportJson = { input, done ->
                        onImportJson(input) { result ->
                            done(result)
                            scope.launch {
                                snackbarHostState.showSnackbar(if (result.isSuccess) "参数已导入" else "JSON 解析失败，现有参数未变更")
                            }
                        }
                    },
                )
            }
            composable(Destinations.About) { AboutScreen() }
        }
    }

    if (resetAllConfirmation) {
        AlertDialog(
            onDismissRequest = { resetAllConfirmation = false },
            title = { Text("恢复全部默认参数？") },
            text = { Text("七组配置都会恢复为参考原版，当前悬浮球状态不会改变。") },
            confirmButton = {
                TextButton(onClick = {
                    resetAllConfirmation = false
                    onResetAll()
                    scope.launch { snackbarHostState.showSnackbar("已恢复全部默认参数") }
                }) { Text("恢复") }
            },
            dismissButton = { TextButton(onClick = { resetAllConfirmation = false }) { Text("取消") } },
        )
    }
}

private fun titleForRoute(route: String?, group: String?): String = when (route) {
    Destinations.Overlay -> "悬浮球"
    Destinations.Presets -> "预设"
    Destinations.Data -> "导入与导出"
    Destinations.About -> "关于"
    Destinations.Group -> ConfigGroup.entries.firstOrNull { it.name == group }?.title() ?: "参数"
    else -> "设置"
}

private fun NavHostController.navigateSingleTop(route: String) {
    navigate(route) { launchSingleTop = true }
}

@Composable
private fun HomeScreen(
    runtimeStatus: OverlayRuntimeStatus,
    overlayPermission: Boolean,
    onToggleOverlay: (Boolean) -> Unit,
    onNavigate: (String) -> Unit,
    onResetAll: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("settings-list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item {
            SettingsSection("悬浮球") {
                SettingsListItem(
                    title = "显示悬浮球",
                    detail = statusLabel(runtimeStatus),
                    trailing = {
                        Switch(
                            checked = runtimeStatus == OverlayRuntimeStatus.Visible,
                            onCheckedChange = onToggleOverlay,
                            modifier = Modifier.testTag("master-switch").semantics { role = Role.Switch },
                        )
                    },
                    modifier = Modifier.semantics { contentDescription = "显示悬浮球" },
                )
                SettingsListItem(
                    title = "运行与权限",
                    detail = if (overlayPermission) "权限已允许" else "需要悬浮窗权限",
                    onClick = { onNavigate(Destinations.Overlay) },
                )
            }
        }
        item {
            SettingsSection("外观") {
                ConfigGroup.entries.take(5).forEach { group ->
                    SettingsListItem(group.title()) { onNavigate(Destinations.group(group)) }
                }
            }
        }
        item {
            SettingsSection("交互") {
                ConfigGroup.entries.drop(5).forEach { group ->
                    SettingsListItem(group.title()) { onNavigate(Destinations.group(group)) }
                }
            }
        }
        item {
            SettingsSection("参数") {
                SettingsListItem("预设") { onNavigate(Destinations.Presets) }
                SettingsListItem("导入与导出") { onNavigate(Destinations.Data) }
                SettingsListItem("全部恢复", destructive = true, onClick = onResetAll)
            }
        }
        item {
            SettingsSection("关于") {
                SettingsListItem("效果边界、参考来源与许可证") { onNavigate(Destinations.About) }
            }
        }
    }
}

@Composable
private fun OverlayDetailsScreen(
    runtimeStatus: OverlayRuntimeStatus,
    overlayPermission: Boolean,
    onRequestOverlayPermission: () -> Unit,
    onStartOverlay: () -> Unit,
    onShowOverlay: () -> Unit,
    onHideOverlay: () -> Unit,
    onStopOverlay: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item {
            SettingsSection("状态") {
                SettingsListItem("运行状态", statusLabel(runtimeStatus))
                SettingsListItem("悬浮权限", if (overlayPermission) "已允许" else "未允许", onClick = onRequestOverlayPermission)
                if (runtimeStatus is OverlayRuntimeStatus.Error) {
                    Text(runtimeStatus.message, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
                }
            }
        }
        item {
            SettingsSection("操作") {
                SettingsListItem(if (overlayPermission) "启动悬浮层" else "授权并返回", onClick = if (overlayPermission) onStartOverlay else onRequestOverlayPermission)
                SettingsListItem("显示悬浮层", onClick = if (overlayPermission) onShowOverlay else onRequestOverlayPermission)
                SettingsListItem("隐藏悬浮层", onClick = onHideOverlay)
                SettingsListItem("停止悬浮层", destructive = true, onClick = onStopOverlay)
            }
        }
        item {
            Text(
                "首页开关关闭时仅隐藏悬浮球。停止会结束服务；重新开启总开关即可启动。系统状态栏、安全页面与锁屏不保证覆盖。",
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PresetsScreen(onPreset: (ConfigPreset, String) -> Unit) {
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    val presets = listOf("参考原版" to ConfigPreset.Reference, "柔和" to ConfigPreset.Soft, "明亮" to ConfigPreset.Bright)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { Text("选择预设后会替换全部参数。", style = MaterialTheme.typography.bodyMedium) }
        item {
            SettingsSection("预设") {
                presets.forEach { (name, preset) ->
                    SettingsListItem(
                        title = name,
                        trailing = { RadioButton(selected = selected == name, onClick = null) },
                        onClick = { selected = name; onPreset(preset, name) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DataManagementScreen(
    onExportJson: () -> Unit,
    onImportJson: (String, (Result<OrbConfig>) -> Unit) -> Unit,
) {
    var importText by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            SettingsSection("导出") {
                SettingsListItem("复制 JSON", onClick = onExportJson)
            }
        }
        item {
            SettingsSection("导入 JSON") {
                OutlinedTextField(
                    value = importText,
                    onValueChange = { importText = it; error = null },
                    modifier = Modifier.fillMaxWidth().padding(16.dp).heightIn(min = 220.dp),
                    minLines = 8,
                    maxLines = 16,
                    label = { Text("参数 JSON") },
                    placeholder = { Text("粘贴从本应用导出的 JSON") },
                    keyboardOptions = KeyboardOptions.Default,
                )
                if (error != null) {
                    Text(error!!, Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.error)
                }
                Button(
                    onClick = {
                        if (importText.isBlank()) {
                            error = "请先粘贴参数 JSON"
                        } else {
                            onImportJson(importText) { result ->
                                if (result.isSuccess) error = null
                                else error = "JSON 解析失败，现有参数未变更"
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                ) { Text("导入参数") }
            }
        }
        item {
            Text(
                "schemaVersion = 1。缺失字段使用默认值，越界数值自动夹紧。",
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AboutScreen() {
    val context = LocalContext.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item {
            SettingsSection("灵动玻璃球") {
                Text("版本 ${BuildConfig.VERSION_NAME} · 非官方、非商业学习演示", Modifier.padding(16.dp))
            }
        }
        item {
            SettingsSection("效果边界") {
                Text(
                    "本演示不录屏、不读取下层 App；折射只作用于球内生成的暗场、波形与圆点。透明区域原样显示底下内容。没有语音助手、麦克风或后台录音功能。",
                    Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            SettingsSection("参考来源") {
                Text("Shader 与动画参考 glass-voice-orb-study @ 3d7e981。Apple 和 Siri 是 Apple Inc. 的商标。", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                SettingsListItem("查看效果参考仓库") {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/cxcboss/glass-voice-orb-study")))
                }
                Text("设置控件使用 Android Material 3；玻璃球渲染核心保持独立。", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            SettingsSection("许可证") {
                Text("AndroidLiquidGlass 与 Backdrop 依赖的许可证及归属见项目 LICENSE / NOTICE。", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(title, Modifier.padding(start = 16.dp, bottom = 8.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(content = content)
        }
    }
}

@Composable
private fun SettingsListItem(
    title: String,
    detail: String? = null,
    destructive: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Column {
        ListItem(
            headlineContent = { Text(title, color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface) },
            supportingContent = detail?.let { { Text(it) } },
            trailingContent = trailing ?: if (onClick != null && !destructive) {
                {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else null,
            modifier = modifier.fillMaxWidth().then(
                if (onClick != null) Modifier
                    .clickable(onClick = onClick)
                    .semantics { role = Role.Button }
                else Modifier,
            ),
        )
        HorizontalDivider(Modifier.padding(start = 16.dp), thickness = 0.5.dp)
    }
}

@Composable
fun SettingsRow(
    title: String,
    detail: String? = null,
    destructive: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    SettingsListItem(title = title, detail = detail, destructive = destructive, onClick = onClick)
}

private fun statusLabel(status: OverlayRuntimeStatus): String = when (status) {
    OverlayRuntimeStatus.Stopped -> "未运行"
    OverlayRuntimeStatus.Visible -> "正在显示"
    OverlayRuntimeStatus.Hidden -> "已隐藏"
    OverlayRuntimeStatus.PermissionRequired -> "需要权限"
    is OverlayRuntimeStatus.Error -> "运行异常"
}
