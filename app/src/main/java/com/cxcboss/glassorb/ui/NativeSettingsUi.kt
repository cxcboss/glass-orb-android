package com.cxcboss.glassorb.ui

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
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.activity.BackEventCompat
import com.cxcboss.glassorb.BuildConfig
import com.cxcboss.glassorb.data.ConfigGroup
import com.cxcboss.glassorb.data.ConfigPreset
import com.cxcboss.glassorb.model.HorizontalAnchor
import com.cxcboss.glassorb.model.OrbConfig
import com.cxcboss.glassorb.overlay.OrbOverlayService
import com.cxcboss.glassorb.overlay.OverlayRuntime
import com.cxcboss.glassorb.overlay.OverlayRuntimeStatus
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.radiobutton.MaterialRadioButton
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.util.ArrayDeque
import java.util.Locale
import kotlin.math.roundToInt

private fun ConfigGroup.title(): String = when (this) {
    ConfigGroup.Geometry -> "尺寸、位置与触控"
    ConfigGroup.Glass -> "玻璃质感"
    ConfigGroup.Container -> "背景与暗部"
    ConfigGroup.Wave -> "波形"
    ConfigGroup.Dots -> "思考圆点"
    ConfigGroup.Motion -> "手势与动画"
    ConfigGroup.Performance -> "性能"
}

internal data class NativeSettingsCallbacks(
    val requestOverlayPermission: () -> Unit,
    val requestAccessibilityPermission: () -> Unit,
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
 * Native Android settings surface. Layout remains ordinary Android Views while
 * interactive controls use the official Material 3 View implementations. No
 * app-specific imitation of sliders, switches or dialogs is used here.
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
        data object Licenses : Screen
    }

    private enum class ParameterId { TouchAreaScale }

    private data class PageEntry(
        val screen: Screen,
        val view: View,
    )

    private val density = activity.resources.displayMetrics.density
    private val pages = ArrayDeque<PageEntry>()
    private var sliderTracking = false
    private var refreshAfterConfigFor: Screen? = null
    private var navigationAnimating = false
    private var config: OrbConfig = OrbConfig.reference()
    private var runtimeStatus: OverlayRuntimeStatus = OverlayRuntimeStatus.Stopped
    private var overlayPermission: Boolean = false
    private var accessibilityEnabled: Boolean = false
    var onBackAvailabilityChanged: ((Boolean) -> Unit)? = null
    val canGoBack: Boolean get() = pages.size > 1
    private var predictiveBack = false
    private var backDirection = 1f

    init {
        root.setBackgroundColor(themeColor(android.R.attr.colorBackground))
        addInitialPage()
    }

    fun updateConfig(value: OrbConfig) {
        val changed = config != value
        config = value
        // Do not rebuild the active page for ordinary slider samples: it would
        // steal the gesture. Explicit reset/import actions request one refresh.
        if (changed && !sliderTracking) {
            refreshAfterConfigFor = null
            refreshCurrentPage()
        }
    }

    fun updateRuntimeStatus(value: OverlayRuntimeStatus) {
        if (runtimeStatus == value) return
        runtimeStatus = value
        if (currentScreen() is Screen.Home || currentScreen() is Screen.Overlay) refreshCurrentPage()
    }

    fun updateOverlayPermission(value: Boolean) {
        if (overlayPermission == value) return
        overlayPermission = value
        if (currentScreen() is Screen.Home || currentScreen() is Screen.Overlay) refreshCurrentPage()
    }

    fun updateAccessibilityEnabled(value: Boolean) {
        if (accessibilityEnabled == value) return
        accessibilityEnabled = value
        if (currentScreen() is Screen.Overlay) refreshCurrentPage()
    }

    fun navigate(screen: Any) {
        val target = when (screen) {
            is ConfigGroup -> Screen.Group(screen)
            is String -> when (screen) {
                "overlay" -> Screen.Overlay
                "presets" -> Screen.Presets
                "data" -> Screen.Data
                "about" -> Screen.About
                "licenses" -> Screen.Licenses
                else -> Screen.Home
            }
            else -> Screen.Home
        }
        if (currentScreen() == target || navigationAnimating) return
        val page = createPage(target)
        root.addView(page, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        pages.addLast(PageEntry(target, page))
        onBackAvailabilityChanged?.invoke(canGoBack)
        page.translationX = (root.width.takeIf { it > 0 } ?: activity.resources.displayMetrics.widthPixels).toFloat()
        navigationAnimating = true
        page.animate()
            .translationX(0f)
            .setDuration(300L)
            .setInterpolator(PathInterpolator(0.2f, 0f, 0f, 1f))
            .withEndAction { navigationAnimating = false }
            .start()
    }

    fun goBack(): Boolean {
        if (pages.size <= 1) return false
        // Keep the previous page mounted and untouched. This preserves scroll
        // position and slider progress while the top page animates away.
        if (navigationAnimating) return true
        val leaving = pages.peekLast() ?: return false
        val distance = (root.width.takeIf { it > 0 } ?: activity.resources.displayMetrics.widthPixels).toFloat() *
            if (predictiveBack) backDirection else 1f
        predictiveBack = false
        navigationAnimating = true
        leaving.view.animate()
            .translationX(distance)
            .setDuration(300L)
            .setInterpolator(PathInterpolator(0.2f, 0f, 0f, 1f))
            .withEndAction {
                if (pages.peekLast() === leaving) {
                    pages.removeLast()
                    root.removeView(leaving.view)
                    onBackAvailabilityChanged?.invoke(canGoBack)
                }
                navigationAnimating = false
            }
            .start()
        return true
    }

    fun startPredictiveBack(event: BackEventCompat) {
        if (!canGoBack || navigationAnimating) return
        predictiveBack = true
        backDirection = if (event.swipeEdge == BackEventCompat.EDGE_LEFT) 1f else -1f
    }

    fun progressPredictiveBack(event: BackEventCompat) {
        if (!predictiveBack) return
        val page = pages.peekLast()?.view ?: return
        val progress = event.progress.coerceIn(0f, 1f)
        page.pivotX = page.width / 2f
        page.pivotY = page.height / 2f
        page.scaleX = 1f - 0.08f * progress
        page.scaleY = 1f - 0.08f * progress
        page.translationX = backDirection * dp(32f) * progress
    }

    fun cancelPredictiveBack() {
        if (!predictiveBack) return
        predictiveBack = false
        navigationAnimating = true
        pages.peekLast()?.view?.animate()?.translationX(0f)?.scaleX(1f)?.scaleY(1f)
            ?.setDuration(180L)?.withEndAction { navigationAnimating = false }?.start()
    }

    private fun addInitialPage() {
        val page = createPage(Screen.Home)
        root.addView(page, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        pages.addLast(PageEntry(Screen.Home, page))
    }

    private fun refreshCurrentPage() {
        if (predictiveBack) return
        val current = pages.pollLast() ?: return
        val scrollY = (current.view as? ViewGroup)?.getChildAt(1)?.scrollY ?: 0
        current.view.animate().cancel()
        navigationAnimating = false
        val replacement = createPage(current.screen)
        root.removeView(current.view)
        root.addView(replacement, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        pages.addLast(PageEntry(current.screen, replacement))
        (replacement as? ViewGroup)?.getChildAt(1)?.let { scroll -> scroll.post { scroll.scrollTo(0, scrollY) } }
    }

    private fun currentScreen(): Screen = pages.peekLast()?.screen ?: Screen.Home

    private fun requestCurrentPageRefresh() {
        refreshAfterConfigFor = currentScreen()
    }

    private fun createPage(screen: Screen): View {
        val page = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(themeColor(android.R.attr.colorBackground))
        }
        val toolbar = MaterialToolbar(activity).apply {
            title = screenTitle(screen)
            setTitleTextColor(themeColor(android.R.attr.textColorPrimary))
            setBackgroundColor(themeColor(android.R.attr.colorBackground))
            // Material 3 uses a flat app bar; the edge tint only appears while
            // content scrolls beneath it, so do not add a permanent shadow.
            elevation = 0f
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
        return page
    }

    private fun buildScreen(screen: Screen): View = when (screen) {
        Screen.Home -> buildHome()
        Screen.Overlay -> buildOverlayDetails()
        is Screen.Group -> buildGroup(screen.group)
        Screen.Presets -> buildPresets()
        Screen.Data -> buildDataManagement()
        Screen.About -> buildAbout()
        Screen.Licenses -> buildLicenses()
    }

    private fun buildHome(): View {
        val content = column()
        section(content, "运行") {
            addSwitchRow(this, "显示灵动岛", nativeStatusLabel(runtimeStatus), runtimeStatus == OverlayRuntimeStatus.Visible) { enabled ->
                when (resolveOverlayAction(enabled, overlayPermission, runtimeStatus)) {
                    OverlayAction.RequestPermission -> callbacks.requestOverlayPermission()
                    OverlayAction.Show -> callbacks.showOverlay()
                    OverlayAction.Start -> callbacks.startOverlay()
                    OverlayAction.Hide -> callbacks.hideOverlay()
                    OverlayAction.None -> Unit
                }
            }
            addNavigationRow(this, "运行与权限", if (overlayPermission) "悬浮窗权限已开启" else "需要悬浮窗权限") { navigate("overlay") }
        }
        section(content, "外观与位置") {
            addNavigationRow(this, "尺寸、位置与触控", "胶囊、玻璃球、向下展开与触控范围") { navigate(ConfigGroup.Geometry) }
            addNavigationRow(this, "背景与暗部", "屏幕压暗、暗部强度与渐变") { navigate(ConfigGroup.Container) }
            addNavigationRow(this, "玻璃质感", "高光、折射与投影") { navigate(ConfigGroup.Glass) }
            addNavigationRow(this, "波形效果", "颜色、线条与亮度") { navigate(ConfigGroup.Wave) }
            addNavigationRow(this, "思考圆点", "大小、光晕与旋转") { navigate(ConfigGroup.Dots) }
        }
        section(content, "交互与运行效率") {
            addNavigationRow(this, "手势与动画", "展开、收起、拖动与自动收起") { navigate(ConfigGroup.Motion) }
            addNavigationRow(this, "性能", "帧率与渲染清晰度") { navigate(ConfigGroup.Performance) }
        }
        section(content, "配置") {
            addNavigationRow(this, "效果预设", "默认、柔和与明亮") { navigate("presets") }
            addNavigationRow(this, "备份与恢复", "导入、导出与重置设置") { navigate("data") }
        }
        section(content, "应用") { addNavigationRow(this, "关于") { navigate("about") } }
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
        section(content, "状态栏触控") {
            addNavigationRow(
                this,
                "无障碍触控服务",
                if (accessibilityEnabled) "已开启 · 用于状态栏区域触控" else "未开启 · 点击前往系统授权",
            ) { callbacks.requestAccessibilityPermission() }
            addNote(this, "仅用于创建可信触控窗口；不读取屏幕、不截图、不执行手势、不监听按键。国产 ROM 如有电池优化或自启动管理，请将本应用设为允许。")
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
        addSubheading(content, "重置本组")
        addActionRow(content, "恢复本组默认值", destructive = true) {
            confirmResetGroup(group)
        }
        addNote(content, "仅恢复本页参数，其他分组保持当前设置。")
        return groupLooseContent(content)
    }

    private fun buildGeometry(content: LinearLayout) {
        addSubheading(content, "位置")
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

        val verticalDensity = activity.resources.displayMetrics.density.coerceAtLeast(0.1f)
        addSlider(
            content,
            "顶部偏移",
            config.geometry.verticalOffsetDp * verticalDensity,
            OrbConfig.reference().geometry.verticalOffsetDp * verticalDensity,
            0f..300f,
            "px",
            decimals = 0,
        ) { value ->
            updateConfig { current ->
                current.copy(geometry = current.geometry.copy(verticalOffsetDp = value / verticalDensity))
            }
        }
        addNote(content, "0 px = 真实物理屏幕顶边，最多向下 300 px。")
        addSlider(content, "水平微调", config.geometry.horizontalOffsetDp, OrbConfig.reference().geometry.horizontalOffsetDp, -200f..200f, "dp") { value ->
            updateConfig { current -> current.copy(geometry = current.geometry.copy(horizontalOffsetDp = value)) }
        }

        addSubheading(content, "胶囊尺寸")
        addNote(content, "宽度或高度为 0 时隐藏胶囊图形，触控入口仍然保留。")
        addSlider(content, "胶囊宽度", config.geometry.capsuleWidthDp, OrbConfig.reference().geometry.capsuleWidthDp, 0f..220f, "dp") { value ->
            updateConfig { current -> current.copy(geometry = current.geometry.copy(capsuleWidthDp = value)) }
        }
        addSlider(content, "胶囊高度", config.geometry.capsuleHeightDp, OrbConfig.reference().geometry.capsuleHeightDp, 0f..64f, "dp") { value ->
            updateConfig { current -> current.copy(geometry = current.geometry.copy(capsuleHeightDp = value)) }
        }
        addSubheading(content, "玻璃球")
        addSwitchRow(content, "向下展开", "展开后球体顶部位于胶囊底部下方 20 px", config.geometry.expandBelowCapsule) { enabled ->
            updateConfig { it.copy(geometry = it.geometry.copy(expandBelowCapsule = enabled)) }
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
        addSubheading(content, "胶囊触控")
        addNote(content, "展开后触控随球体轮廓变化，球体外可操作下层内容。")
        lateinit var touchScaleRow: View
        addSwitchRow(content, "扩大胶囊触摸区域", "仅扩大胶囊触控范围，最小 38 × 38 dp", config.geometry.enlargedTouchArea) { enabled ->
            setControlEnabled(touchScaleRow, enabled)
            updateConfig { current -> current.copy(geometry = current.geometry.copy(enlargedTouchArea = enabled)) }
        }
        touchScaleRow = addSlider(
            parent = content,
            label = "触摸区域倍率",
            value = config.geometry.touchAreaScale,
            defaultValue = OrbConfig.reference().geometry.touchAreaScale,
            range = 1f..3f,
            suffix = "×",
            decimals = 2,
            parameterId = ParameterId.TouchAreaScale,
        ) { value -> updateConfig { current -> current.copy(geometry = current.geometry.copy(touchAreaScale = value)) } }
        setControlEnabled(touchScaleRow, config.geometry.enlargedTouchArea)
    }

    private fun buildGlass(content: LinearLayout) {
        addSubheading(content, "玻璃形态与光影")
        addSlider(content, "内部深度", config.glass.internalDepth, OrbConfig.reference().glass.internalDepth, 0f..40f) { value ->
            updateConfig { current -> current.copy(glass = current.glass.copy(internalDepth = value)) }
        }
        addSlider(content, "曲率", config.glass.curvature, OrbConfig.reference().glass.curvature, 0f..1f, decimals = 2) { value ->
            updateConfig { current -> current.copy(glass = current.glass.copy(curvature = value)) }
        }
        addSubheading(content, "高光")
        addSlider(content, "高光亮度", config.glass.highlightAmount, OrbConfig.reference().glass.highlightAmount, 0f..2f, decimals = 2) { value ->
            updateConfig { current -> current.copy(glass = current.glass.copy(highlightAmount = value)) }
        }
        addSlider(content, "高光宽度", config.glass.highlightWidth, OrbConfig.reference().glass.highlightWidth, 0.2f..8f, decimals = 2) { value ->
            updateConfig { current -> current.copy(glass = current.glass.copy(highlightWidth = value)) }
        }
        addSlider(content, "高光收束", config.glass.highlightCut, OrbConfig.reference().glass.highlightCut, 0f..1f, decimals = 2) { value ->
            updateConfig { current -> current.copy(glass = current.glass.copy(highlightCut = value)) }
        }
        addSubheading(content, "阴影与焦散")
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
        addSubheading(content, "屏幕背景")
        addSwitchRow(content, "背景压暗", "从顶部 28% 黑色渐变到透明，随展开与收起淡入淡出", config.container.backgroundDimEnabled) { enabled ->
            updateConfig { it.copy(container = it.container.copy(backgroundDimEnabled = enabled)) }
        }
        addSubheading(content, "球体暗部")
        addSlider(content, "强度", config.container.strength, OrbConfig.reference().container.strength, 0f..4f, decimals = 2) { value ->
            updateConfig { current -> current.copy(container = current.container.copy(strength = value)) }
        }
        addNote(content, "0 = 完全透明；提高强度可让暗部更深，最高可达到不透明纯黑。")
        addSlider(content, "渐隐跨度", config.container.fade, OrbConfig.reference().container.fade, 0f..2f, decimals = 2) { value ->
            updateConfig { current -> current.copy(container = current.container.copy(fade = value)) }
        }
        addSlider(content, "暗部衰减", config.container.gaussian, OrbConfig.reference().container.gaussian, 0.5f..16f, decimals = 1) { value ->
            updateConfig { current -> current.copy(container = current.container.copy(gaussian = value)) }
        }
    }

    private fun buildWave(content: LinearLayout) {
        addSubheading(content, "波形与色彩")
        addSlider(content, "振幅", config.wave.amplitude, OrbConfig.reference().wave.amplitude, 0f..0.6f, decimals = 3) { value ->
            updateConfig { current -> current.copy(wave = current.wave.copy(amplitude = value)) }
        }
        addSlider(content, "尺度", config.wave.scale, OrbConfig.reference().wave.scale, 0.4f..1.6f, decimals = 2) { value ->
            updateConfig { current -> current.copy(wave = current.wave.copy(scale = value)) }
        }
        addSlider(content, "色散", config.wave.chromaticAberration, OrbConfig.reference().wave.chromaticAberration, 0f..8f, decimals = 2) { value ->
            updateConfig { current -> current.copy(wave = current.wave.copy(chromaticAberration = value)) }
        }
        addSubheading(content, "光带")
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
        addSlider(content, "白色辉光", config.wave.whiteBloom, OrbConfig.reference().wave.whiteBloom, 0f..3f, decimals = 2) { value ->
            updateConfig { current -> current.copy(wave = current.wave.copy(whiteBloom = value)) }
        }
        addSlider(content, "色相偏移", config.wave.hueShiftDegrees, OrbConfig.reference().wave.hueShiftDegrees, -180f..180f, "°", 0) { value ->
            updateConfig { current -> current.copy(wave = current.wave.copy(hueShiftDegrees = value)) }
        }
    }

    private fun buildDots(content: LinearLayout) {
        addSubheading(content, "粒子外观与运动")
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
        addSubheading(content, "上滑与拖动")
        addSlider(content, "收起跟手距离", config.motion.collapseRangeDp, OrbConfig.reference().motion.collapseRangeDp, 24f..120f, "dp") { value ->
            updateConfig { current -> current.copy(motion = current.motion.copy(collapseRangeDp = value)) }
        }
        addSlider(content, "拖拽弹性范围", config.motion.dragRangeDp, OrbConfig.reference().motion.dragRangeDp, 16f..160f, "dp") { value ->
            updateConfig { current -> current.copy(motion = current.motion.copy(dragRangeDp = value)) }
        }
        addSlider(content, "拖拽响应系数", config.motion.dragResistance, OrbConfig.reference().motion.dragResistance, 0.05f..2f, decimals = 2) { value ->
            updateConfig { current -> current.copy(motion = current.motion.copy(dragResistance = value)) }
        }
        addSubheading(content, "形变与回弹")
        addSlider(content, "形变位移上限", config.motion.deformLimitDp, OrbConfig.reference().motion.deformLimitDp, 0f..8f, "dp") { value ->
            updateConfig { current -> current.copy(motion = current.motion.copy(deformLimitDp = value)) }
        }
        addSlider(content, "形变幅度", config.motion.deformScaleDelta, OrbConfig.reference().motion.deformScaleDelta, 0f..0.02f, decimals = 3) { value ->
            updateConfig { current -> current.copy(motion = current.motion.copy(deformScaleDelta = value)) }
        }
        addSlider(content, "形变回弹反应", config.motion.deformResponse, OrbConfig.reference().motion.deformResponse, 0.08f..1.5f, "s", 2) { value ->
            updateConfig { current -> current.copy(motion = current.motion.copy(deformResponse = value)) }
        }
        addSlider(content, "形变回弹阻尼", config.motion.deformDamping, OrbConfig.reference().motion.deformDamping, 0.1f..1.5f, decimals = 2) { value ->
            updateConfig { current -> current.copy(motion = current.motion.copy(deformDamping = value)) }
        }
        addSubheading(content, "展开与收起")
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
        addSlider(content, "收起回弹幅度", config.motion.closeBounce, OrbConfig.reference().motion.closeBounce, 0f..0.12f, decimals = 3) { value ->
            updateConfig { current -> current.copy(motion = current.motion.copy(closeBounce = value)) }
        }
        addSlider(content, "波形渐入延迟", config.motion.waveFadeDelayMs.toFloat(), OrbConfig.reference().motion.waveFadeDelayMs.toFloat(), 0f..500f, "ms", 0) { value ->
            updateConfig { current -> current.copy(motion = current.motion.copy(waveFadeDelayMs = value.toInt())) }
        }
        addSubheading(content, "呼吸与点击")
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
        addSubheading(content, "自动收起")
        lateinit var countdown: View
        addSwitchRow(content, "自动收起", "无操作时自动返回胶囊", config.motion.autoCollapseEnabled) { enabled ->
            setControlEnabled(countdown, enabled)
            updateConfig { it.copy(motion = it.motion.copy(autoCollapseEnabled = enabled)) }
        }
        countdown = addSlider(content, "等待时间", config.motion.autoCollapseSeconds, OrbConfig.reference().motion.autoCollapseSeconds, 1f..60f, "s", 1) { value ->
            updateConfig { it.copy(motion = it.motion.copy(autoCollapseSeconds = value)) }
        }
        setControlEnabled(countdown, config.motion.autoCollapseEnabled)
    }

    private fun buildPerformance(content: LinearLayout) {
        addSubheading(content, "帧率与清晰度")
        addNote(content, "更高的帧率与渲染比例会增加耗电；实际帧率受屏幕刷新率限制。")
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
            "默认效果" to ConfigPreset.Reference,
            "柔和" to ConfigPreset.Soft,
            "明亮" to ConfigPreset.Bright,
        ).forEach { (name, preset) ->
            val radio = MaterialRadioButton(activity).apply {
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
                    requestCurrentPageRefresh()
                    callbacks.applyPreset(preset)
                    Toast.makeText(activity, "已应用$name", Toast.LENGTH_SHORT).show()
                }
            }
            radios.addView(radio, matchWrap())
        }
        content.addView(radios, matchWrap())
        return groupLooseContent(content)
    }

    private fun buildDataManagement(): View {
        val content = column()
        addSubheading(content, "导出")
        addActionRow(content, "分享 JSON") {
            val json = callbacks.exportJson()
            val share = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_TEXT, json)
            }
            activity.startActivity(Intent.createChooser(share, "分享设置 JSON"))
        }
        addSubheading(content, "导入 JSON")
        val input = TextInputEditText(activity).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            gravity = Gravity.TOP or Gravity.START
            minLines = 8
            maxLines = 16
            setPadding(dp(16f), dp(12f), dp(16f), dp(12f))
            contentDescription = "参数 JSON"
        }
        val inputLayout = TextInputLayout(activity).apply {
            hint = "输入导出的 JSON"
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            addView(input, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ))
        }
        content.addView(inputLayout, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(220f)))
        val error = TextView(activity).apply {
            setTextColor(themeColor(android.R.attr.colorAccent))
            visibility = View.GONE
            setPadding(dp(16f), dp(4f), dp(16f), dp(4f))
        }
        content.addView(error, matchWrap())
        addActionRow(content, "导入参数") {
            if (input.text.isNullOrBlank()) {
                error.text = "请先输入参数 JSON"
                error.visibility = View.VISIBLE
            } else {
                requestCurrentPageRefresh()
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
        addNote(content, "导入会覆盖当前设置，建议先分享一份备份。应用不会读取或写入系统剪贴板。")
        addSubheading(content, "重置")
        addActionRow(content, "恢复全部默认设置", destructive = true) { confirmResetAll() }
        return groupLooseContent(content)
    }

    private fun buildAbout(): View {
        val content = column()
        section(content, "灵动玻璃球") {
            val icon = ImageView(activity).apply {
                setImageResource(com.cxcboss.glassorb.R.mipmap.ic_launcher)
                contentDescription = "应用图标"
            }
            addView(icon, LinearLayout.LayoutParams(dp(72f), dp(72f)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                setMargins(0, dp(20f), 0, dp(12f))
            })
            addTextRow(this, "版本", BuildConfig.VERSION_NAME)
            addTextRow(this, "构建编号", BuildConfig.VERSION_CODE.toString())
        }
        section(content, "隐私与许可") {
            addTextRow(this, "本地运行", "设置仅保存在本机。")
            addNavigationRow(this, "开源许可") { navigate("licenses") }
        }
        return content
    }

    private fun buildLicenses(): View {
        val content = column()
        section(content, "许可与归属") {
            val notice = activity.assets.open("NOTICE.txt").bufferedReader().use { it.readText() }
            addTextRow(this, "第三方声明", notice)
            val license = activity.assets.open("AndroidLiquidGlass-LICENSE.txt").bufferedReader().use { it.readText() }
            addTextRow(this, "许可证", license)
        }
        return content
    }

    private fun addAnchor(group: RadioGroup, label: String, anchor: HorizontalAnchor) {
        val radio = MaterialRadioButton(activity).apply {
            text = label
            minHeight = dp(44f)
            gravity = Gravity.CENTER_VERTICAL
            textSize = 16f
            minimumHeight = dp(48f)
            isChecked = config.geometry.horizontalAnchor == anchor
            setPadding(dp(8f), 0, dp(16f), 0)
            setOnClickListener {
                updateConfig { it.copy(geometry = it.geometry.copy(horizontalAnchor = anchor)) }
                for (index in 0 until group.childCount) {
                    (group.getChildAt(index) as? MaterialRadioButton)?.isChecked = index == group.indexOfChild(this)
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
        enabled: Boolean = true,
        onValueChange: (Float) -> Unit,
    ): View {
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
        labels.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val valueText = TextView(activity).apply {
            textSize = 14f
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
            setTextColor(themeColor(android.R.attr.colorAccent))
            minWidth = dp(92f)
        }
        labels.addView(valueText, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44f)))
        lateinit var slider: Slider
        lateinit var updateLabels: (Float) -> Unit
        val reset = MaterialButton(activity, null, com.google.android.material.R.attr.materialButtonStyle).apply {
            text = "还原"
            minHeight = dp(44f)
            minimumWidth = dp(64f)
            insetTop = 0
            insetBottom = 0
            elevation = 0f
            stateListAnimator = null
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.TRANSPARENT)
            setTextColor(themeColor(android.R.attr.colorAccent))
            contentDescription = "$label 还原默认值"
            setOnClickListener {
                val restored = snapToSliderStep(defaultValue, range, decimals)
                slider.value = restored
                updateLabels(restored)
                onValueChange(restored)
            }
        }
        labels.addView(reset, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44f)))
        row.addView(labels, matchWrap())

        val initialValue = snapToSliderStep(value, range, decimals)
        slider = Slider(activity).apply {
            contentDescription = "$label 调节"
            minimumHeight = dp(48f)
            valueFrom = range.start
            valueTo = range.endInclusive
            // Keep the official Material control continuous, then snap only
            // the published setting. This avoids Material Slider throwing
            // when a persisted float is infinitesimally off a discrete step.
            stepSize = 0f
            setValue(initialValue)
            // Keep the entire 48dp control target active, including the track
            // ends, so a tap positions the thumb and a drag never gets lost.
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48f))
            isEnabled = enabled
        }
        row.addView(slider)
        parent.addView(row, matchWrap())
        row.alpha = if (enabled) 1f else 0.42f

        updateLabels = { current: Float ->
            valueText.text = formatParameter(current, suffix, decimals)
            reset.visibility = if (isParameterModified(current, defaultValue, decimals)) View.VISIBLE else View.INVISIBLE
        }
        updateLabels(initialValue)
        slider.addOnChangeListener(Slider.OnChangeListener { _, next, fromUser ->
            if (!fromUser) return@OnChangeListener
            val snapped = snapToSliderStep(next, range, decimals)
            updateLabels(snapped)
            onValueChange(snapped)
        })
        slider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {
                sliderTracking = true
                if (parameterId == ParameterId.TouchAreaScale && OverlayRuntime.status.value == OverlayRuntimeStatus.Visible) {
                    OrbOverlayService.setTouchPreview(activity, true)
                }
            }

            override fun onStopTrackingTouch(slider: Slider) {
                sliderTracking = false
                if (parameterId == ParameterId.TouchAreaScale && OverlayRuntime.status.value == OverlayRuntimeStatus.Visible) {
                    OrbOverlayService.setTouchPreview(activity, false)
                }
            }
        })
        return row
    }

    private fun setControlEnabled(view: View, enabled: Boolean) {
        fun applyEnabled(child: View) {
            child.isEnabled = enabled
            if (child is ViewGroup) for (index in 0 until child.childCount) applyEnabled(child.getChildAt(index))
        }
        applyEnabled(view)
        view.alpha = if (enabled) 1f else 0.42f
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
        val toggle = MaterialSwitch(activity).apply {
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
            tag = "section-heading"
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
            background = roundedSurface()
            clipToOutline = true
            elevation = 0f
            content()
        }
        parent.addView(group, matchWrap().apply { setMargins(dp(12f), 0, dp(12f), dp(12f)) })
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
        setPadding(0, dp(8f), 0, 0)
        isFocusable = true
    }

    private fun groupLooseContent(source: LinearLayout): View {
        val result = column()
        var title = "参数"
        val pending = mutableListOf<View>()
        fun flush() {
            if (pending.isEmpty()) return
            section(result, title) { pending.forEach { addView(it) } }
            pending.clear()
        }
        while (source.childCount > 0) {
            val child = source.getChildAt(0)
            source.removeViewAt(0)
            if (child.tag == "section-heading") {
                flush()
                title = (child as TextView).text.toString()
            } else pending.add(child)
        }
        flush()
        return result
    }

    private fun roundedSurface(): android.graphics.drawable.Drawable = android.graphics.drawable.GradientDrawable().apply {
        setColor(themeColor(com.google.android.material.R.attr.colorSurface))
        cornerRadius = dp(16f).toFloat()
    }

    private fun updateConfig(transform: (OrbConfig) -> OrbConfig) {
        config = transform(config).normalized()
        callbacks.updateConfig(config)
    }

    private fun confirmResetGroup(group: ConfigGroup) {
        MaterialAlertDialogBuilder(activity)
            .setTitle("恢复本组默认参数？")
            .setMessage("仅恢复“${group.title()}”中的参数。")
            .setNegativeButton("取消", null)
            .setPositiveButton("恢复") { _, _ ->
                requestCurrentPageRefresh()
                callbacks.resetGroup(group)
                Toast.makeText(activity, "已恢复本组默认值", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun confirmResetAll() {
        MaterialAlertDialogBuilder(activity)
            .setTitle("恢复全部默认参数？")
            .setMessage("七组配置都会恢复为默认效果，当前悬浮球状态不会改变。")
            .setNegativeButton("取消", null)
            .setPositiveButton("恢复") { _, _ ->
                requestCurrentPageRefresh()
                callbacks.resetAll()
                Toast.makeText(activity, "已恢复全部默认参数", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun screenTitle(screen: Screen): String = when (screen) {
        Screen.Home -> "灵动玻璃球"
        Screen.Licenses -> "开源许可"
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
