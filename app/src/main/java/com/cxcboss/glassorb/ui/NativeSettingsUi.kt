package com.cxcboss.glassorb.ui

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toolbar
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.cxcboss.glassorb.BuildConfig
import com.cxcboss.glassorb.data.ConfigGroup
import com.cxcboss.glassorb.data.ConfigPreset
import com.cxcboss.glassorb.model.HorizontalAnchor
import com.cxcboss.glassorb.model.OrbConfig
import com.cxcboss.glassorb.overlay.OrbOverlayService
import com.cxcboss.glassorb.overlay.OverlayRuntime
import com.cxcboss.glassorb.overlay.OverlayRuntimeStatus
import android.window.BackEvent
import android.window.OnBackAnimationCallback
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import java.util.ArrayDeque
import java.util.Locale
import kotlin.math.pow
import kotlin.math.roundToInt

private fun ConfigGroup.title(): String = when (this) {
    ConfigGroup.Geometry -> "胶囊与位置"
    ConfigGroup.Glass -> "玻璃"
    ConfigGroup.Container -> "暗场"
    ConfigGroup.Wave -> "波形"
    ConfigGroup.Dots -> "思考圆点"
    ConfigGroup.Motion -> "动画"
    ConfigGroup.Performance -> "性能"
}

internal data class NativeSettingsCallbacks(
    val requestOverlayPermission: () -> Unit,
    val startOverlay: () -> Unit,
    val showOverlay: () -> Unit,
    val hideOverlay: () -> Unit,
    val stopOverlay: () -> Unit,
    val updateConfig: (OrbConfig) -> Unit,
    val applyPreset: (ConfigPreset) -> Unit,
    val resetGroup: (ConfigGroup) -> Unit,
    val resetAll: () -> Unit,
    val exportJson: () -> String,
    val importJson: (String, (Result<OrbConfig>) -> Unit) -> Unit,
)

/**
 * Native Android settings surface. It intentionally uses platform widgets
 * (Toolbar, ScrollView, Switch, SeekBar, RadioButton, EditText and Button)
 * instead of a Compose or third-party control layer.
 */
internal class NativeSettingsController(
    private val activity: MainActivity,
    private val root: FrameLayout,
    private val callbacks: NativeSettingsCallbacks,
) {
    private sealed interface Screen {
        data object Home : Screen
        data object Overlay : Screen
        data class Group(val group: ConfigGroup) : Screen
        data object Presets : Screen
        data object Data : Screen
        data object About : Screen
    }

    private enum class ParameterId { TouchAreaScale }

    private val density = activity.resources.displayMetrics.density
    private val stack = ArrayDeque<Screen>().apply { addLast(Screen.Home) }
    private var pageLayer: View? = null
    private var backGestureActive = false
    private var sliderTracking = false
    private var config: OrbConfig = OrbConfig.reference()
    private var runtimeStatus: OverlayRuntimeStatus = OverlayRuntimeStatus.Stopped
    private var overlayPermission: Boolean = false

    @Suppress("NewApi")
    @get:RequiresApi(Build.VERSION_CODES.TIRAMISU)
    val backInvokedCallback: OnBackInvokedCallback by lazy(LazyThreadSafetyMode.NONE) {
        OnBackInvokedCallback {
            if (!goBack()) activity.finish()
        }
    }

    @Suppress("NewApi")
    @get:RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    val backAnimationCallback: OnBackAnimationCallback by lazy(LazyThreadSafetyMode.NONE) {
        object : OnBackAnimationCallback {
            override fun onBackStarted(backEvent: BackEvent) {
                if (stack.size <= 1) return
                backGestureActive = true
                pageLayer?.animate()?.cancel()
                pageLayer?.translationX = 0f
            }

            override fun onBackProgressed(backEvent: BackEvent) {
                if (!backGestureActive) return
                val width = (root.width.takeIf { it > 0 } ?: activity.resources.displayMetrics.widthPixels).toFloat()
                pageLayer?.translationX = width * backEvent.progress.coerceIn(0f, 1f)
            }

            override fun onBackCancelled() {
                if (!backGestureActive) return
                backGestureActive = false
                pageLayer?.animate()
                    ?.translationX(0f)
                    ?.setDuration(180L)
                    ?.setInterpolator(PathInterpolator(0.2f, 0f, 0f, 1f))
                    ?.start()
            }

            override fun onBackInvoked() {
                backGestureActive = false
                if (!goBack()) activity.finish()
            }
        }
    }

    init {
        root.setBackgroundColor(themeColor(android.R.attr.colorBackground))
        render(animated = false)
    }

    fun updateConfig(value: OrbConfig) {
        val changed = config != value
        config = value
        // Sliders update the ViewModel continuously while the finger is down.
        // Rebuilding the page for every sample would steal the SeekBar gesture.
        // Discrete actions (reset, preset, switches and JSON import) are safe to
        // rebuild so the visible controls immediately reflect the new snapshot.
        if (changed && !sliderTracking && (stack.last() is Screen.Group || stack.last() == Screen.Presets)) {
            render(animated = false)
        }
    }

    fun updateRuntimeStatus(value: OverlayRuntimeStatus) {
        if (runtimeStatus == value) return
        runtimeStatus = value
        if (stack.last() is Screen.Home || stack.last() is Screen.Overlay) render(animated = false)
    }

    fun updateOverlayPermission(value: Boolean) {
        if (overlayPermission == value) return
        overlayPermission = value
        if (stack.last() is Screen.Home || stack.last() is Screen.Overlay) render(animated = false)
    }

    fun navigate(screen: Any) {
        val target = when (screen) {
            is ConfigGroup -> Screen.Group(screen)
            is String -> when (screen) {
                "overlay" -> Screen.Overlay
                "presets" -> Screen.Presets
                "data" -> Screen.Data
                "about" -> Screen.About
                else -> Screen.Home
            }
            else -> Screen.Home
        }
        if (stack.last() == target) return
        stack.addLast(target)
        render(animated = true)
    }

    fun goBack(): Boolean {
        if (stack.size <= 1) return false
        stack.removeLast()
        render(animated = false)
        return true
    }

    private fun render(animated: Boolean) {
        val old = pageLayer
        val page = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(themeColor(android.R.attr.colorBackground))
        }
        val screen = stack.last()
        val toolbar = Toolbar(activity).apply {
            title = screenTitle(screen)
            setTitleTextColor(themeColor(android.R.attr.textColorPrimary))
            setBackgroundColor(themeColor(android.R.attr.colorBackground))
            elevation = dp(2f).toFloat()
            minimumHeight = actionBarHeight()
        }
        if (screen !is Screen.Home) {
            toolbar.navigationIcon = ContextCompat.getDrawable(activity, com.cxcboss.glassorb.R.drawable.ic_arrow_back)
            toolbar.navigationContentDescription = "返回"
            toolbar.setNavigationOnClickListener { goBack() }
        }
        page.addView(toolbar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, actionBarHeight()))

        val scroll = ScrollView(activity).apply {
            isFillViewport = true
            clipToPadding = false
            setPadding(0, 0, 0, dp(24f))
            addView(buildScreen(screen), ViewGroup.LayoutParams.MATCH_PARENT)
        }
        page.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.removeAllViews()
        root.addView(page, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        pageLayer = page

        if (animated) {
            page.translationX = (root.width.takeIf { it > 0 } ?: activity.resources.displayMetrics.widthPixels).toFloat()
            page.animate()
                .translationX(0f)
                .setDuration(220L)
                .setInterpolator(PathInterpolator(0.2f, 0f, 0f, 1f))
                .start()
        } else {
            old?.animate()?.cancel()
            page.translationX = 0f
        }
    }

    private fun buildScreen(screen: Screen): View = when (screen) {
        Screen.Home -> buildHome()
        Screen.Overlay -> buildOverlayDetails()
        is Screen.Group -> buildGroup(screen.group)
        Screen.Presets -> buildPresets()
        Screen.Data -> buildDataManagement()
        Screen.About -> buildAbout()
    }

    private fun buildHome(): View {
        val content = column()
        section(content, "悬浮球") {
            addSwitchRow(
                parent = this,
                title = "显示悬浮球",
                detail = nativeStatusLabel(runtimeStatus),
                checked = runtimeStatus == OverlayRuntimeStatus.Visible,
                onChanged = { enabled ->
                    when (resolveOverlayAction(enabled, overlayPermission, runtimeStatus)) {
                        OverlayAction.RequestPermission -> callbacks.requestOverlayPermission()
                        OverlayAction.Show -> callbacks.showOverlay()
                        OverlayAction.Start -> callbacks.startOverlay()
                        OverlayAction.Hide -> callbacks.hideOverlay()
                        OverlayAction.None -> Unit
                    }
                },
            )
            addNavigationRow(this, "运行与权限", if (overlayPermission) "权限已允许" else "需要悬浮窗权限") {
                navigate("overlay")
            }
        }
        section(content, "外观") {
            ConfigGroup.entries.take(5).forEach { group ->
                addNavigationRow(this, group.title()) { navigate(group) }
            }
        }
        section(content, "交互") {
            ConfigGroup.entries.drop(5).forEach { group ->
                addNavigationRow(this, group.title()) { navigate(group) }
            }
        }
        section(content, "参数") {
            addNavigationRow(this, "预设") { navigate("presets") }
            addNavigationRow(this, "导入与导出") { navigate("data") }
            addActionRow(this, "全部恢复", destructive = true) { confirmResetAll() }
        }
        section(content, "关于") {
            addNavigationRow(this, "效果边界、参考来源与许可证") { navigate("about") }
        }
        return content
    }

    private fun buildOverlayDetails(): View {
        val content = column()
        val status = runtimeStatus
        section(content, "状态") {
            addTextRow(this, "运行状态", nativeStatusLabel(status))
            addNavigationRow(this, "悬浮权限", if (overlayPermission) "已允许" else "未允许") {
                callbacks.requestOverlayPermission()
            }
            if (status is OverlayRuntimeStatus.Error) {
                addTextRow(this, "错误", status.message, destructive = true)
            }
        }
        section(content, "操作") {
            addNavigationRow(this, if (overlayPermission) "启动悬浮层" else "授权并返回") {
                if (overlayPermission) callbacks.startOverlay() else callbacks.requestOverlayPermission()
            }
            addNavigationRow(this, "显示悬浮层") {
                if (overlayPermission) callbacks.showOverlay() else callbacks.requestOverlayPermission()
            }
            addNavigationRow(this, "隐藏悬浮层") { callbacks.hideOverlay() }
            addActionRow(this, "停止悬浮层", destructive = true) { callbacks.stopOverlay() }
        }
        addNote(
            content,
            "首页开关关闭时仅隐藏悬浮球。停止会结束服务；重新开启总开关即可启动。系统状态栏、安全页面与锁屏不保证覆盖。",
        )
        return content
    }

    private fun buildGroup(group: ConfigGroup): View {
        val content = column()
        when (group) {
            ConfigGroup.Geometry -> buildGeometry(content)
            ConfigGroup.Glass -> buildGlass(content)
            ConfigGroup.Container -> buildContainer(content)
            ConfigGroup.Wave -> buildWave(content)
            ConfigGroup.Dots -> buildDots(content)
            ConfigGroup.Motion -> buildMotion(content)
            ConfigGroup.Performance -> buildPerformance(content)
        }
        addActionRow(content, "恢复本组默认值", destructive = true) {
            confirmResetGroup(group)
        }
        addNote(content, "仅恢复本页参数，其他分组保持当前设置。")
        return content
    }

    private fun buildGeometry(content: LinearLayout) {
        addSubheading(content, "水平预设")
        val anchors = RadioGroup(activity).apply {
            orientation = RadioGroup.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12f), 0, dp(12f), dp(4f))
        }
        addAnchor(anchors, "左侧", HorizontalAnchor.Left)
        addAnchor(anchors, "居中", HorizontalAnchor.Center)
        addAnchor(anchors, "右侧", HorizontalAnchor.Right)
        content.addView(anchors, matchWrap())
        divider(content)

        addSlider(content, "胶囊宽度", config.geometry.capsuleWidthDp, OrbConfig.reference().geometry.capsuleWidthDp, 24f..220f, "dp") { value ->
            updateConfig { current -> current.copy(geometry = current.geometry.copy(capsuleWidthDp = value)) }
        }
        addSlider(content, "胶囊高度", config.geometry.capsuleHeightDp, OrbConfig.reference().geometry.capsuleHeightDp, 24f..64f, "dp") { value ->
            updateConfig { current -> current.copy(geometry = current.geometry.copy(capsuleHeightDp = value)) }
        }
        addSlider(content, "球体直径", config.geometry.orbDiameterDp, OrbConfig.reference().geometry.orbDiameterDp, 88f..220f, "dp") { value ->
            updateConfig { current -> current.copy(geometry = current.geometry.copy(orbDiameterDp = value)) }
        }
        addSlider(content, "外部效果余量", config.geometry.outerMarginDp, OrbConfig.reference().geometry.outerMarginDp, 8f..48f, "dp") { value ->
            updateConfig { current -> current.copy(geometry = current.geometry.copy(outerMarginDp = value)) }
        }
        addSlider(content, "效果画布比例", config.geometry.effectScale, OrbConfig.reference().geometry.effectScale, 0.9f..1.5f, decimals = 2) { value ->
            updateConfig { current -> current.copy(geometry = current.geometry.copy(effectScale = value)) }
        }
        addSlider(content, "顶部偏移", config.geometry.verticalOffsetDp, OrbConfig.reference().geometry.verticalOffsetDp, -64f..240f, "dp") { value ->
            updateConfig { current -> current.copy(geometry = current.geometry.copy(verticalOffsetDp = value)) }
        }
        addNote(content, "0 dp = 真实物理屏幕顶边。")
        addSlider(content, "水平微调", config.geometry.horizontalOffsetDp, OrbConfig.reference().geometry.horizontalOffsetDp, -200f..200f, "dp") { value ->
            updateConfig { current -> current.copy(geometry = current.geometry.copy(horizontalOffsetDp = value)) }
        }
        addSwitchRow(content, "扩大胶囊触摸区域", null, config.geometry.enlargedTouchArea) { enabled ->
            updateConfig { current -> current.copy(geometry = current.geometry.copy(enlargedTouchArea = enabled)) }
        }
        if (config.geometry.enlargedTouchArea) {
            addSlider(
                parent = content,
                label = "触摸区域倍率",
                value = config.geometry.touchAreaScale,
                defaultValue = OrbConfig.reference().geometry.touchAreaScale,
                range = 1f..3f,
                suffix = "×",
                decimals = 2,
                parameterId = ParameterId.TouchAreaScale,
            ) { value -> updateConfig { current -> current.copy(geometry = current.geometry.copy(touchAreaScale = value)) } }
        }
    }

    private fun buildGlass(content: LinearLayout) {
        addSlider(content, "内部深度", config.glass.internalDepth, OrbConfig.reference().glass.internalDepth, 0f..40f) { value ->
            updateConfig { current -> current.copy(glass = current.glass.copy(internalDepth = value)) }
        }
        addSlider(content, "曲率", config.glass.curvature, OrbConfig.reference().glass.curvature, 0f..1f, decimals = 2) { value ->
            updateConfig { current -> current.copy(glass = current.glass.copy(curvature = value)) }
        }
        addSlider(content, "高光亮度", config.glass.highlightAmount, OrbConfig.reference().glass.highlightAmount, 0f..2f, decimals = 2) { value ->
            updateConfig { current -> current.copy(glass = current.glass.copy(highlightAmount = value)) }
        }
        addSlider(content, "高光宽度", config.glass.highlightWidth, OrbConfig.reference().glass.highlightWidth, 0.2f..8f, decimals = 2) { value ->
            updateConfig { current -> current.copy(glass = current.glass.copy(highlightWidth = value)) }
        }
        addSlider(content, "高光收束", config.glass.highlightCut, OrbConfig.reference().glass.highlightCut, 0f..1f, decimals = 2) { value ->
            updateConfig { current -> current.copy(glass = current.glass.copy(highlightCut = value)) }
        }
        addSlider(content, "阴影", config.glass.shadowAmount, OrbConfig.reference().glass.shadowAmount, 0f..1.5f, decimals = 2) { value ->
            updateConfig { current -> current.copy(glass = current.glass.copy(shadowAmount = value)) }
        }
        addSlider(content, "焦散", config.glass.causticAmount, OrbConfig.reference().glass.causticAmount, 0f..3f, decimals = 2) { value ->
            updateConfig { current -> current.copy(glass = current.glass.copy(causticAmount = value)) }
        }
        addSlider(content, "阴影偏移", config.glass.shadowOffset, OrbConfig.reference().glass.shadowOffset, -3f..3f, decimals = 2) { value ->
            updateConfig { current -> current.copy(glass = current.glass.copy(shadowOffset = value)) }
        }
        addSlider(content, "焦散偏移", config.glass.causticOffset, OrbConfig.reference().glass.causticOffset, -4f..4f, decimals = 2) { value ->
            updateConfig { current -> current.copy(glass = current.glass.copy(causticOffset = value)) }
        }
        addSlider(content, "光影柔度", config.glass.lightSoftness, OrbConfig.reference().glass.lightSoftness, 0.5f..10f, decimals = 2) { value ->
            updateConfig { current -> current.copy(glass = current.glass.copy(lightSoftness = value)) }
        }
    }

    private fun buildContainer(content: LinearLayout) {
        addSlider(content, "强度", config.container.strength, OrbConfig.reference().container.strength, 0f..1.5f, decimals = 2) { value ->
            updateConfig { current -> current.copy(container = current.container.copy(strength = value)) }
        }
        addSlider(content, "纯黑区域", config.container.blackLevel, OrbConfig.reference().container.blackLevel, 0f..1f, decimals = 2) { value ->
            updateConfig { current -> current.copy(container = current.container.copy(blackLevel = value)) }
        }
        addSlider(content, "渐隐跨度", config.container.fade, OrbConfig.reference().container.fade, 0f..2f, decimals = 2) { value ->
            updateConfig { current -> current.copy(container = current.container.copy(fade = value)) }
        }
        addSlider(content, "高斯斜率", config.container.gaussian, OrbConfig.reference().container.gaussian, 0.5f..16f, decimals = 1) { value ->
            updateConfig { current -> current.copy(container = current.container.copy(gaussian = value)) }
        }
    }

    private fun buildWave(content: LinearLayout) {
        addSlider(content, "振幅", config.wave.amplitude, OrbConfig.reference().wave.amplitude, 0f..0.6f, decimals = 3) { value ->
            updateConfig { current -> current.copy(wave = current.wave.copy(amplitude = value)) }
        }
        addSlider(content, "尺度", config.wave.scale, OrbConfig.reference().wave.scale, 0.4f..1.6f, decimals = 2) { value ->
            updateConfig { current -> current.copy(wave = current.wave.copy(scale = value)) }
        }
        addSlider(content, "色散", config.wave.chromaticAberration, OrbConfig.reference().wave.chromaticAberration, 0f..8f, decimals = 2) { value ->
            updateConfig { current -> current.copy(wave = current.wave.copy(chromaticAberration = value)) }
        }
        addSlider(content, "线宽", config.wave.lineWidth, OrbConfig.reference().wave.lineWidth, 0.5f..8f, decimals = 2) { value ->
            updateConfig { current -> current.copy(wave = current.wave.copy(lineWidth = value)) }
        }
        addSlider(content, "亮度", config.wave.intensity, OrbConfig.reference().wave.intensity, 0f..5f, decimals = 2) { value ->
            updateConfig { current -> current.copy(wave = current.wave.copy(intensity = value)) }
        }
        addSlider(content, "填光", config.wave.bandFill, OrbConfig.reference().wave.bandFill, 0f..60_000f, decimals = 0) { value ->
            updateConfig { current -> current.copy(wave = current.wave.copy(bandFill = value)) }
        }
        addSlider(content, "填光厚度", config.wave.bandFillThickness, OrbConfig.reference().wave.bandFillThickness, 0f..0.3f, decimals = 3) { value ->
            updateConfig { current -> current.copy(wave = current.wave.copy(bandFillThickness = value)) }
        }
        addSlider(content, "柔化", config.wave.softness, OrbConfig.reference().wave.softness, 0.2f..8f, decimals = 2) { value ->
            updateConfig { current -> current.copy(wave = current.wave.copy(softness = value)) }
        }
        addSlider(content, "白色 Bloom", config.wave.whiteBloom, OrbConfig.reference().wave.whiteBloom, 0f..3f, decimals = 2) { value ->
            updateConfig { current -> current.copy(wave = current.wave.copy(whiteBloom = value)) }
        }
        addSlider(content, "色相偏移", config.wave.hueShiftDegrees, OrbConfig.reference().wave.hueShiftDegrees, -180f..180f, "°", 0) { value ->
            updateConfig { current -> current.copy(wave = current.wave.copy(hueShiftDegrees = value)) }
        }
    }

    private fun buildDots(content: LinearLayout) {
        addSlider(content, "环半径", config.dots.ringRadius, OrbConfig.reference().dots.ringRadius, 0.15f..0.75f, decimals = 3) { value ->
            updateConfig { current -> current.copy(dots = current.dots.copy(ringRadius = value)) }
        }
        addSlider(content, "点半径", config.dots.dotRadius, OrbConfig.reference().dots.dotRadius, 0.025f..0.22f, decimals = 3) { value ->
            updateConfig { current -> current.copy(dots = current.dots.copy(dotRadius = value)) }
        }
        addSlider(content, "辉光", config.dots.glow, OrbConfig.reference().dots.glow, 0f..0.2f, decimals = 3) { value ->
            updateConfig { current -> current.copy(dots = current.dots.copy(glow = value)) }
        }
        addSlider(content, "转速", config.dots.rotationSpeed, OrbConfig.reference().dots.rotationSpeed, -3f..3f, decimals = 2) { value ->
            updateConfig { current -> current.copy(dots = current.dots.copy(rotationSpeed = value)) }
        }
    }

    private fun buildMotion(content: LinearLayout) {
        addSlider(content, "收起跟手距离", config.motion.collapseRangeDp, OrbConfig.reference().motion.collapseRangeDp, 24f..120f, "dp") { value ->
            updateConfig { current -> current.copy(motion = current.motion.copy(collapseRangeDp = value)) }
        }
        addSlider(content, "拖拽弹性范围", config.motion.dragRangeDp, OrbConfig.reference().motion.dragRangeDp, 16f..160f, "dp") { value ->
            updateConfig { current -> current.copy(motion = current.motion.copy(dragRangeDp = value)) }
        }
        addSlider(content, "拖拽响应系数", config.motion.dragResistance, OrbConfig.reference().motion.dragResistance, 0.05f..2f, decimals = 2) { value ->
            updateConfig { current -> current.copy(motion = current.motion.copy(dragResistance = value)) }
        }
        addSlider(content, "最大轻微位移", config.motion.deformLimitDp, OrbConfig.reference().motion.deformLimitDp, 0f..8f, "dp") { value ->
            updateConfig { current -> current.copy(motion = current.motion.copy(deformLimitDp = value)) }
        }
        addSlider(content, "形变幅度", config.motion.deformScaleDelta, OrbConfig.reference().motion.deformScaleDelta, 0f..0.02f, decimals = 3) { value ->
            updateConfig { current -> current.copy(motion = current.motion.copy(deformScaleDelta = value)) }
        }
        addSlider(content, "形变回弹响应", config.motion.deformResponse, OrbConfig.reference().motion.deformResponse, 0.08f..1.5f, "s", 2) { value ->
            updateConfig { current -> current.copy(motion = current.motion.copy(deformResponse = value)) }
        }
        addSlider(content, "形变回弹阻尼", config.motion.deformDamping, OrbConfig.reference().motion.deformDamping, 0.1f..1.5f, decimals = 2) { value ->
            updateConfig { current -> current.copy(motion = current.motion.copy(deformDamping = value)) }
        }
        addSlider(content, "展开响应", config.motion.openResponse, OrbConfig.reference().motion.openResponse, 0.12f..1.2f, "s", 2) { value ->
            updateConfig { current -> current.copy(motion = current.motion.copy(openResponse = value)) }
        }
        addSlider(content, "展开阻尼", config.motion.openDamping, OrbConfig.reference().motion.openDamping, 0.2f..1.5f, decimals = 2) { value ->
            updateConfig { current -> current.copy(motion = current.motion.copy(openDamping = value)) }
        }
        addSlider(content, "收起响应", config.motion.closeResponse, OrbConfig.reference().motion.closeResponse, 0.12f..1.2f, "s", 2) { value ->
            updateConfig { current -> current.copy(motion = current.motion.copy(closeResponse = value)) }
        }
        addSlider(content, "收起阻尼", config.motion.closeDamping, OrbConfig.reference().motion.closeDamping, 0.2f..1.5f, decimals = 2) { value ->
            updateConfig { current -> current.copy(motion = current.motion.copy(closeDamping = value)) }
        }
        addSlider(content, "负向软回弹", config.motion.closeBounce, OrbConfig.reference().motion.closeBounce, 0f..0.12f, decimals = 3) { value ->
            updateConfig { current -> current.copy(motion = current.motion.copy(closeBounce = value)) }
        }
        addSlider(content, "波形渐入延迟", config.motion.waveFadeDelayMs.toFloat(), OrbConfig.reference().motion.waveFadeDelayMs.toFloat(), 0f..500f, "ms", 0) { value ->
            updateConfig { current -> current.copy(motion = current.motion.copy(waveFadeDelayMs = value.toInt())) }
        }
        addSlider(content, "呼吸幅度", config.motion.breathingAmplitude, OrbConfig.reference().motion.breathingAmplitude, 0f..0.08f, decimals = 3) { value ->
            updateConfig { current -> current.copy(motion = current.motion.copy(breathingAmplitude = value)) }
        }
        addSlider(content, "呼吸速度", config.motion.breathingSpeed, OrbConfig.reference().motion.breathingSpeed, 0f..4f, decimals = 2) { value ->
            updateConfig { current -> current.copy(motion = current.motion.copy(breathingSpeed = value)) }
        }
        addSlider(content, "点击放大", config.motion.pressScale, OrbConfig.reference().motion.pressScale, 1f..1.08f, decimals = 3) { value ->
            updateConfig { current -> current.copy(motion = current.motion.copy(pressScale = value)) }
        }
        addSlider(content, "思考停留", config.motion.thinkingDurationMs.toFloat(), OrbConfig.reference().motion.thinkingDurationMs.toFloat(), 300f..5_000f, "ms", 0) { value ->
            updateConfig { current -> current.copy(motion = current.motion.copy(thinkingDurationMs = value.toInt())) }
        }
        addSwitchRow(content, "自动收起玻璃球", null, config.motion.autoCollapseEnabled) { enabled ->
            updateConfig { current -> current.copy(motion = current.motion.copy(autoCollapseEnabled = enabled)) }
        }
        if (config.motion.autoCollapseEnabled) {
            addSlider(content, "自动收起倒计时", config.motion.autoCollapseSeconds, OrbConfig.reference().motion.autoCollapseSeconds, 1f..60f, "s", 1) { value ->
                updateConfig { current -> current.copy(motion = current.motion.copy(autoCollapseSeconds = value)) }
            }
        }
    }

    private fun buildPerformance(content: LinearLayout) {
        addSlider(content, "胶囊帧率", config.performance.collapsedFps.toFloat(), OrbConfig.reference().performance.collapsedFps.toFloat(), 24f..120f, "fps", 0) { value ->
            updateConfig { current -> current.copy(performance = current.performance.copy(collapsedFps = value.toInt())) }
        }
        addSlider(content, "展开帧率", config.performance.expandedFps.toFloat(), OrbConfig.reference().performance.expandedFps.toFloat(), 24f..120f, "fps", 0) { value ->
            updateConfig { current -> current.copy(performance = current.performance.copy(expandedFps = value.toInt())) }
        }
        addSlider(content, "渲染比例", config.performance.renderScale, OrbConfig.reference().performance.renderScale, 0.5f..1.25f, decimals = 2) { value ->
            updateConfig { current -> current.copy(performance = current.performance.copy(renderScale = value)) }
        }
    }

    private fun buildPresets(): View {
        val content = column()
        addNote(content, "选择预设后会替换全部参数。")
        addSubheading(content, "预设")
        val radios = RadioGroup(activity).apply {
            orientation = RadioGroup.VERTICAL
            setPadding(dp(8f), 0, dp(8f), 0)
        }
        listOf(
            "参考原版" to ConfigPreset.Reference,
            "柔和" to ConfigPreset.Soft,
            "明亮" to ConfigPreset.Bright,
        ).forEach { (name, preset) ->
            val radio = RadioButton(activity).apply {
                text = name
                textSize = 16f
                minHeight = dp(48f)
                setPadding(dp(8f), 0, dp(8f), 0)
                isChecked = config == when (preset) {
                    ConfigPreset.Reference -> OrbConfig.reference()
                    ConfigPreset.Soft -> OrbConfig.soft()
                    ConfigPreset.Bright -> OrbConfig.bright()
                }
                setOnClickListener {
                    callbacks.applyPreset(preset)
                    Toast.makeText(activity, "已应用$name", Toast.LENGTH_SHORT).show()
                }
            }
            radios.addView(radio, matchWrap())
        }
        content.addView(radios, matchWrap())
        return content
    }

    private fun buildDataManagement(): View {
        val content = column()
        addSubheading(content, "导出")
        addActionRow(content, "复制 JSON") {
            val json = callbacks.exportJson()
            val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("灵动玻璃球参数", json))
            Toast.makeText(activity, "JSON 已复制", Toast.LENGTH_SHORT).show()
        }
        addSubheading(content, "导入 JSON")
        val input = EditText(activity).apply {
            hint = "粘贴从本应用导出的 JSON"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            gravity = Gravity.TOP or Gravity.START
            minLines = 8
            maxLines = 16
            setPadding(dp(16f), dp(12f), dp(16f), dp(12f))
            contentDescription = "参数 JSON"
        }
        content.addView(input, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(220f)))
        val error = TextView(activity).apply {
            setTextColor(themeColor(android.R.attr.colorAccent))
            visibility = View.GONE
            setPadding(dp(16f), dp(4f), dp(16f), dp(4f))
        }
        content.addView(error, matchWrap())
        addActionRow(content, "导入参数") {
            if (input.text.isNullOrBlank()) {
                error.text = "请先粘贴参数 JSON"
                error.visibility = View.VISIBLE
            } else {
                callbacks.importJson(input.text.toString()) { result ->
                    activity.runOnUiThread {
                        if (result.isSuccess) {
                            error.visibility = View.GONE
                            Toast.makeText(activity, "参数已导入", Toast.LENGTH_SHORT).show()
                        } else {
                            error.text = "JSON 解析失败，现有参数未变更"
                            error.visibility = View.VISIBLE
                        }
                    }
                }
            }
        }
        addNote(content, "schemaVersion = 1。缺失字段使用默认值，越界数值自动夹紧。")
        return content
    }

    private fun buildAbout(): View {
        val content = column()
        addSubheading(content, "灵动玻璃球")
        addTextRow(content, "版本", "${BuildConfig.VERSION_NAME} · 非官方、非商业学习演示")
        addSubheading(content, "效果边界")
        addTextRow(
            content,
            "说明",
            "本演示不录屏、不读取下层 App；折射只作用于球内生成的暗场、波形与圆点。透明区域原样显示底下内容。没有语音助手、麦克风或后台录音功能。",
        )
        addSubheading(content, "参考来源")
        addTextRow(content, "Shader", "glass-voice-orb-study @ 3d7e981。Apple 和 Siri 是 Apple Inc. 的商标。")
        addNavigationRow(content, "查看效果参考仓库") {
            runCatching {
                activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/cxcboss/glass-voice-orb-study")))
            }
        }
        addTextRow(content, "设置控件", "Android 平台原生 View 控件；玻璃球渲染核心保持独立。")
        addSubheading(content, "许可证")
        addTextRow(content, "第三方", "AndroidLiquidGlass 来源项目的许可证及归属见项目 LICENSE / NOTICE；本应用设置页不打包该依赖。")
        return content
    }

    private fun addAnchor(group: RadioGroup, label: String, anchor: HorizontalAnchor) {
        val radio = RadioButton(activity).apply {
            text = label
            textSize = 16f
            minimumHeight = dp(48f)
            isChecked = config.geometry.horizontalAnchor == anchor
            setPadding(dp(8f), 0, dp(16f), 0)
            setOnClickListener {
                updateConfig { it.copy(geometry = it.geometry.copy(horizontalAnchor = anchor)) }
                for (index in 0 until group.childCount) {
                    (group.getChildAt(index) as? RadioButton)?.isChecked = index == group.indexOfChild(this)
                }
            }
        }
        group.addView(radio, RadioGroup.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    }

    private fun addSlider(
        parent: LinearLayout,
        label: String,
        value: Float,
        defaultValue: Float,
        range: ClosedFloatingPointRange<Float>,
        suffix: String = "",
        decimals: Int = 1,
        parameterId: ParameterId? = null,
        onValueChange: (Float) -> Unit,
    ) {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16f), dp(8f), dp(16f), dp(4f))
        }
        val labels = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = TextView(activity).apply {
            text = label
            textSize = 16f
            setTextColor(themeColor(android.R.attr.textColorPrimary))
        }
        labels.addView(title, LinearLayout.LayoutParams(0, dp(44f), 1f))
        val valueText = TextView(activity).apply {
            textSize = 14f
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
            setTextColor(themeColor(android.R.attr.colorAccent))
            minWidth = dp(92f)
        }
        labels.addView(valueText, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44f)))
        lateinit var seek: SeekBar
        lateinit var updateLabels: (Float) -> Unit
        val reset = Button(activity).apply {
            text = "还原"
            minHeight = dp(44f)
            setOnClickListener {
                seek.progress = encodeSliderValue(defaultValue, range, decimals)
                onValueChange(defaultValue)
                updateLabels(defaultValue)
            }
        }
        labels.addView(reset, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44f)))
        row.addView(labels, matchWrap())

        seek = SeekBar(activity).apply {
            contentDescription = "$label 调节"
            minimumHeight = dp(48f)
            max = sliderSteps(range, decimals)
            progress = encodeSliderValue(value, range, decimals)
        }
        row.addView(seek, matchWrap())
        parent.addView(row, matchWrap())

        updateLabels = { current: Float ->
            valueText.text = formatParameter(current, suffix, decimals)
            reset.visibility = if (isParameterModified(current, defaultValue, decimals)) View.VISIBLE else View.INVISIBLE
        }
        updateLabels(value)
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val next = decodeSliderValue(progress, range, decimals)
                updateLabels(next)
                onValueChange(next)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
                sliderTracking = true
                if (parameterId == ParameterId.TouchAreaScale && OverlayRuntime.status.value == OverlayRuntimeStatus.Visible) {
                    OrbOverlayService.setTouchPreview(activity, true)
                }
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                sliderTracking = false
                if (parameterId == ParameterId.TouchAreaScale && OverlayRuntime.status.value == OverlayRuntimeStatus.Visible) {
                    OrbOverlayService.setTouchPreview(activity, false)
                }
            }
        })
    }

    private fun addSwitchRow(
        parent: LinearLayout,
        title: String,
        detail: String?,
        checked: Boolean,
        onChanged: (Boolean) -> Unit,
    ) {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(64f)
            setPadding(dp(16f), dp(4f), dp(12f), dp(4f))
            background = selectableBackground()
            contentDescription = title
        }
        val labels = labelColumn(title, detail)
        row.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val toggle = Switch(activity).apply {
            isChecked = checked
            contentDescription = title
            setOnCheckedChangeListener { _, value -> onChanged(value) }
        }
        row.addView(toggle, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(48f)))
        row.setOnClickListener { toggle.performClick() }
        parent.addView(row, matchWrap())
        divider(parent)
    }

    private fun addNavigationRow(parent: LinearLayout, title: String, detail: String? = null, onClick: () -> Unit) {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(56f)
            setPadding(dp(16f), dp(4f), dp(12f), dp(4f))
            background = selectableBackground()
            contentDescription = title
            setOnClickListener { onClick() }
        }
        row.addView(labelColumn(title, detail), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val arrow = ImageView(activity).apply {
            setImageResource(com.cxcboss.glassorb.R.drawable.ic_arrow_forward)
            contentDescription = null
            alpha = 0.65f
        }
        row.addView(arrow, LinearLayout.LayoutParams(dp(32f), dp(48f)))
        parent.addView(row, matchWrap())
        divider(parent)
    }

    private fun addActionRow(parent: LinearLayout, title: String, destructive: Boolean = false, onClick: () -> Unit) {
        val row = TextView(activity).apply {
            text = title
            textSize = 16f
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(56f)
            setPadding(dp(16f), 0, dp(16f), 0)
            setTextColor(if (destructive) destructiveColor() else themeColor(android.R.attr.colorAccent))
            background = selectableBackground()
            contentDescription = title
            setOnClickListener { onClick() }
        }
        parent.addView(row, matchWrap())
        divider(parent)
    }

    private fun addTextRow(parent: LinearLayout, title: String, detail: String, destructive: Boolean = false) {
        val row = labelColumn(title, detail).apply {
            setPadding(dp(16f), dp(10f), dp(16f), dp(10f))
            if (destructive) {
                findViewById<TextView>(android.R.id.text1)?.setTextColor(destructiveColor())
            }
        }
        parent.addView(row, matchWrap())
        divider(parent)
    }

    private fun addSubheading(parent: LinearLayout, title: String) {
        val heading = TextView(activity).apply {
            text = title
            textSize = 14f
            setTextColor(themeColor(android.R.attr.textColorSecondary))
            setPadding(dp(16f), dp(22f), dp(16f), dp(8f))
        }
        parent.addView(heading, matchWrap())
    }

    private fun addNote(parent: LinearLayout, text: String) {
        val note = TextView(activity).apply {
            this.text = text
            textSize = 13f
            setTextColor(themeColor(android.R.attr.textColorSecondary))
            setPadding(dp(16f), dp(8f), dp(16f), dp(8f))
        }
        parent.addView(note, matchWrap())
    }

    private fun section(parent: LinearLayout, title: String, content: LinearLayout.() -> Unit) {
        addSubheading(parent, title)
        val group = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            content()
        }
        parent.addView(group, matchWrap())
    }

    private fun labelColumn(title: String, detail: String?): LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_VERTICAL
        val titleView = TextView(activity).apply {
            id = android.R.id.text1
            text = title
            textSize = 16f
            setTextColor(themeColor(android.R.attr.textColorPrimary))
        }
        addView(titleView, matchWrap())
        if (!detail.isNullOrBlank()) {
            val detailView = TextView(activity).apply {
                id = android.R.id.text2
                text = detail
                textSize = 13f
                setTextColor(themeColor(android.R.attr.textColorSecondary))
            }
            addView(detailView, matchWrap())
        }
    }

    private fun column(): LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(themeColor(android.R.attr.colorBackground))
        isFocusable = true
    }

    private fun updateConfig(transform: (OrbConfig) -> OrbConfig) {
        callbacks.updateConfig(transform(config).normalized())
    }

    private fun confirmResetGroup(group: ConfigGroup) {
        AlertDialog.Builder(activity)
            .setTitle("恢复本组默认参数？")
            .setMessage("仅恢复“${group.title()}”中的参数。")
            .setNegativeButton("取消", null)
            .setPositiveButton("恢复") { _, _ ->
                callbacks.resetGroup(group)
                Toast.makeText(activity, "已恢复本组默认值", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun confirmResetAll() {
        AlertDialog.Builder(activity)
            .setTitle("恢复全部默认参数？")
            .setMessage("七组配置都会恢复为参考原版，当前悬浮球状态不会改变。")
            .setNegativeButton("取消", null)
            .setPositiveButton("恢复") { _, _ ->
                callbacks.resetAll()
                Toast.makeText(activity, "已恢复全部默认参数", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun screenTitle(screen: Screen): String = when (screen) {
        Screen.Home -> "灵动玻璃球"
        Screen.Overlay -> "悬浮球"
        is Screen.Group -> screen.group.title()
        Screen.Presets -> "预设"
        Screen.Data -> "导入与导出"
        Screen.About -> "关于"
    }

    private fun nativeStatusLabel(status: OverlayRuntimeStatus): String = when (status) {
        OverlayRuntimeStatus.Stopped -> "未运行"
        OverlayRuntimeStatus.Visible -> "正在显示"
        OverlayRuntimeStatus.Hidden -> "已隐藏"
        OverlayRuntimeStatus.PermissionRequired -> "需要权限"
        is OverlayRuntimeStatus.Error -> "运行异常"
    }

    private fun sliderSteps(range: ClosedFloatingPointRange<Float>, decimals: Int): Int {
        val multiplier = 10.0.pow(decimals.coerceIn(0, 3)).toFloat()
        return ((range.endInclusive - range.start) * multiplier).roundToInt().coerceAtLeast(1)
    }

    private fun encodeSliderValue(value: Float, range: ClosedFloatingPointRange<Float>, decimals: Int): Int {
        val multiplier = 10.0.pow(decimals.coerceIn(0, 3)).toFloat()
        return ((value.coerceIn(range) - range.start) * multiplier).roundToInt().coerceIn(0, sliderSteps(range, decimals))
    }

    private fun decodeSliderValue(progress: Int, range: ClosedFloatingPointRange<Float>, decimals: Int): Float {
        val multiplier = 10.0.pow(decimals.coerceIn(0, 3)).toFloat()
        return (range.start + progress.coerceIn(0, sliderSteps(range, decimals)) / multiplier)
            .coerceIn(range)
    }

    private fun formatParameter(value: Float, suffix: String, decimals: Int): String = buildString {
        append(String.format(Locale.US, "%.${decimals.coerceIn(0, 3)}f", value))
        if (suffix.isNotEmpty()) append(' ').append(suffix)
    }

    private fun divider(parent: LinearLayout) {
        val line = View(activity).apply { setBackgroundColor(themeColor(android.R.attr.divider)) }
        parent.addView(line, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1))
    }

    private fun selectableBackground(): android.graphics.drawable.Drawable? {
        val value = TypedValue()
        activity.theme.resolveAttribute(android.R.attr.selectableItemBackground, value, true)
        return if (value.resourceId != 0) ContextCompat.getDrawable(activity, value.resourceId) else ColorDrawable(Color.TRANSPARENT)
    }

    private fun themeColor(attribute: Int): Int {
        val value = TypedValue()
        activity.theme.resolveAttribute(attribute, value, true)
        return if (value.resourceId != 0) ContextCompat.getColor(activity, value.resourceId) else value.data
    }

    private fun destructiveColor(): Int = if (isDarkTheme()) Color.rgb(255, 105, 97) else Color.rgb(190, 30, 45)

    private fun isDarkTheme(): Boolean = activity.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
        android.content.res.Configuration.UI_MODE_NIGHT_YES

    private fun actionBarHeight(): Int {
        val value = TypedValue()
        activity.theme.resolveAttribute(android.R.attr.actionBarSize, value, true)
        return TypedValue.complexToDimensionPixelSize(value.data, activity.resources.displayMetrics)
            .coerceAtLeast(dp(56f))
    }

    private fun dp(value: Float): Int = (value * density + 0.5f).roundToInt()

    private fun matchWrap() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
}
