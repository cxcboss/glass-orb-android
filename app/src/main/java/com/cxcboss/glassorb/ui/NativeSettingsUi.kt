package com.cxcboss.glassorb.ui

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.ImageButton
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
import com.google.android.material.card.MaterialCardView
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.radiobutton.MaterialRadioButton
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.util.ArrayDeque
import java.util.Locale
import kotlin.math.roundToInt

private fun ConfigGroup.title(): String = when (this) {
    ConfigGroup.Geometry -> "位置与触控"
    ConfigGroup.Glass -> "颜色与光影"
    ConfigGroup.Container -> "背景与暗部"
    ConfigGroup.Wave -> "波形与光带"
    ConfigGroup.Dots -> "圆点与粒子"
    ConfigGroup.Motion -> "手势与动效"
    ConfigGroup.Performance -> "性能与清晰度"
}

private enum class HomeTab { Common, Detail }

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
    val setAutoStartEnabled: (Boolean) -> Unit,
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
    private var autoStartEnabled: Boolean = false
    private var homeTab: HomeTab = HomeTab.Common
    var onBackAvailabilityChanged: ((Boolean) -> Unit)? = null
    val canGoBack: Boolean get() = pages.size > 1
    private var predictiveBack = false
    private var backDirection = 1f

    init {
        val state = activity.savedStateRegistry.consumeRestoredStateForKey(SAVED_STATE_KEY)
        homeTab = if (state?.getString(KEY_HOME_TAB) == HomeTab.Detail.name) HomeTab.Detail else HomeTab.Common
        activity.savedStateRegistry.registerSavedStateProvider(SAVED_STATE_KEY) {
            Bundle().apply {
                putString(KEY_HOME_TAB, homeTab.name)
                putString(KEY_SCREEN, screenKey(pages.peekLast()?.screen ?: Screen.Home))
                putInt(KEY_SCROLL_Y, pages.peekLast()?.view?.let { findFirstScroll(it)?.scrollY } ?: 0)
            }
        }
        root.setBackgroundColor(themeColor(android.R.attr.colorBackground))
        addInitialPage()
        restoreTopPage(state)
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

    fun updateAutoStartEnabled(value: Boolean) {
        if (autoStartEnabled == value) return
        autoStartEnabled = value
        if (currentScreen() is Screen.Home) refreshCurrentPage()
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

    private fun restoreTopPage(state: Bundle?) {
        val restored = decodeScreen(state?.getString(KEY_SCREEN)) ?: return
        if (restored == Screen.Home) {
            val scrollY = state?.getInt(KEY_SCROLL_Y, 0) ?: 0
            pages.peekLast()?.view?.let { view ->
                findFirstScroll(view)?.let { scroll -> scroll.post { scroll.scrollTo(0, scrollY) } }
            }
            return
        }
        val page = createPage(restored)
        root.addView(page, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        pages.addLast(PageEntry(restored, page))
        val scrollY = state?.getInt(KEY_SCROLL_Y, 0) ?: 0
        findFirstScroll(page)?.let { scroll -> scroll.post { scroll.scrollTo(0, scrollY) } }
    }

    private fun refreshCurrentPage() {
        if (predictiveBack) return
        val current = pages.pollLast() ?: return
        val scrollY = findFirstScroll(current.view)?.scrollY ?: 0
        current.view.animate().cancel()
        navigationAnimating = false
        val replacement = createPage(current.screen)
        root.removeView(current.view)
        root.addView(replacement, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        pages.addLast(PageEntry(current.screen, replacement))
        findFirstScroll(replacement)?.let { scroll -> scroll.post { scroll.scrollTo(0, scrollY) } }
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

        if (screen == Screen.Home) {
            page.addView(buildHome(toolbar), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        } else {
            val scroll = ScrollView(activity).apply {
                tag = "settings-scroll"
                isFillViewport = true
                clipToPadding = false
                setPadding(0, 0, 0, dp(24f))
                addView(buildScreen(screen), ViewGroup.LayoutParams.MATCH_PARENT)
            }
            page.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
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

    private fun buildHome(toolbar: MaterialToolbar? = null): View {
        var selectedTab = homeTab
        val shell = FrameLayout(activity).apply {
            setBackgroundColor(themeColor(android.R.attr.colorBackground))
        }
        val scroll = ScrollView(activity).apply {
            tag = "settings-scroll"
            isFillViewport = true
            clipToPadding = false
            setPadding(0, 0, 0, dp(92f))
        }
        shell.addView(scroll, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val bottomBar = MaterialCardView(activity).apply {
            radius = dp(28f).toFloat()
            cardElevation = dp(8f).toFloat()
            setCardBackgroundColor(themeColor(com.google.android.material.R.attr.colorSurface))
            contentDescription = "设置页面切换"
        }
        val bottomButtons = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(8f), dp(8f), dp(8f), dp(8f))
        }
        val commonButton = homeTabButton(android.R.drawable.ic_menu_view, "常用设置")
        val detailButton = homeTabButton(android.R.drawable.ic_menu_manage, "详细设置")
        bottomButtons.addView(commonButton, LinearLayout.LayoutParams(dp(48f), dp(48f)).apply {
            rightMargin = dp(4f)
        })
        bottomButtons.addView(detailButton, LinearLayout.LayoutParams(dp(48f), dp(48f)).apply {
            leftMargin = dp(4f)
        })
        bottomBar.addView(bottomButtons, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        shell.addView(bottomBar, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            bottomMargin = dp(16f)
        })

        fun selectTab(tab: HomeTab) {
            selectedTab = tab
            homeTab = tab
            val common = tab == HomeTab.Common
            styleHomeTabButton(commonButton, common)
            styleHomeTabButton(detailButton, !common)
            toolbar?.title = if (common) "常用设置" else "详细设置"
            scroll.removeAllViews()
            scroll.addView(
                if (common) buildCommonContent() else buildMoreContent(),
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            scroll.scrollTo(0, 0)
        }
        commonButton.setOnClickListener { selectTab(HomeTab.Common) }
        detailButton.setOnClickListener { selectTab(HomeTab.Detail) }
        selectTab(selectedTab)
        return shell
    }

    private fun buildCommonContent(): View {
        val content = column()
        section(content, "灵动岛") {
            addSwitchRow(this, "显示灵动岛", "控制悬浮层是否显示在其他应用上方。", runtimeStatus == OverlayRuntimeStatus.Visible) { enabled ->
                when (resolveOverlayAction(enabled, overlayPermission, runtimeStatus)) {
                    OverlayAction.RequestPermission -> callbacks.requestOverlayPermission()
                    OverlayAction.Show -> callbacks.showOverlay()
                    OverlayAction.Start -> callbacks.startOverlay()
                    OverlayAction.Hide -> callbacks.hideOverlay()
                    OverlayAction.None -> Unit
                }
            }
            addSwitchRow(this, "向下展开", "只改变玻璃球弹出位置，胶囊位置保持不变。", config.geometry.expandBelowCapsule) { enabled ->
                updateConfig { it.copy(geometry = it.geometry.copy(expandBelowCapsule = enabled)) }
            }
            addSwitchRow(this, "开机自启动", "开机后自动恢复悬浮层。", autoStartEnabled) { enabled ->
                autoStartEnabled = enabled
                callbacks.setAutoStartEnabled(enabled)
            }
        }

        addSubheading(content, "尺寸与位置")
        settingCard(content, "胶囊") {
            addSlider(this, "胶囊宽度", config.geometry.capsuleWidthDp, OrbConfig.reference().geometry.capsuleWidthDp, 0f..220f, "dp") { value ->
                updateConfig { it.copy(geometry = it.geometry.copy(capsuleWidthDp = value)) }
            }
            addSlider(this, "胶囊高度", config.geometry.capsuleHeightDp, OrbConfig.reference().geometry.capsuleHeightDp, 0f..64f, "dp") { value ->
                updateConfig { it.copy(geometry = it.geometry.copy(capsuleHeightDp = value)) }
            }
        }
        settingCard(content, "玻璃球") {
            addSlider(this, "玻璃球宽度", config.geometry.orbWidthDp, OrbConfig.reference().geometry.orbWidthDp, 88f..260f, "dp") { value ->
                updateConfig { it.copy(geometry = it.geometry.copy(orbWidthDp = value)) }
            }
            addSlider(this, "玻璃球高度", config.geometry.orbHeightDp, OrbConfig.reference().geometry.orbHeightDp, 88f..260f, "dp") { value ->
                updateConfig { it.copy(geometry = it.geometry.copy(orbHeightDp = value)) }
            }
        }

        section(content, "显示行为") {
            addSwitchRow(this, "背景压暗", "展开时压暗范围随玻璃球大小变化，收起时平滑消失。", config.container.backgroundDimEnabled) { enabled ->
                updateConfig { it.copy(container = it.container.copy(backgroundDimEnabled = enabled)) }
            }
            addSwitchRow(this, "横屏隐藏", "横屏时隐藏灵动岛与触控区域。", config.geometry.hideWhenLandscape) { enabled ->
                updateConfig { it.copy(geometry = it.geometry.copy(hideWhenLandscape = enabled)) }
            }
        }
        return content
    }

    private fun buildMoreContent(): View {
        val content = column()
        addSubheading(content, "全部设置")
        settingCard(content) {
            addCategoryRow(this, "位置与触控", "锚点、偏移、尺寸、触控倍率和横屏策略", android.R.drawable.ic_menu_manage) {
                navigate(ConfigGroup.Geometry)
            }
            addCategoryRow(this, "颜色与光影", "内部颜色、高光、阴影与自动混合焦散", android.R.drawable.ic_menu_view) {
                navigate(ConfigGroup.Glass)
            }
            addCategoryRow(this, "背景与暗部", "背景压暗、局部模糊与球体暗部", android.R.drawable.ic_menu_gallery) {
                navigate(ConfigGroup.Container)
            }
            addCategoryRow(this, "波形与光带", "波形线条、色散、填光与亮度", android.R.drawable.ic_menu_slideshow) {
                navigate(ConfigGroup.Wave)
            }
            addCategoryRow(this, "圆点与粒子", "圆点大小、光晕与旋转", android.R.drawable.ic_menu_crop) {
                navigate(ConfigGroup.Dots)
            }
            addCategoryRow(this, "手势与动效", "展开、收起、拖动、回弹和自动收起", android.R.drawable.ic_menu_rotate) {
                navigate(ConfigGroup.Motion)
            }
            addCategoryRow(this, "性能与清晰度", "帧率、渲染比例和静态胶囊省电策略", android.R.drawable.ic_menu_sort_by_size) {
                navigate(ConfigGroup.Performance)
            }
        }

        addSubheading(content, "工具与应用")
        settingCard(content) {
            addCategoryRow(this, "运行与权限", "悬浮窗、无障碍触控和国产 ROM 适配", android.R.drawable.ic_menu_preferences) {
                navigate("overlay")
            }
            addCategoryRow(this, "预设与备份", "切换效果与恢复配置", android.R.drawable.ic_menu_save) {
                navigate("presets")
            }
            addCategoryRow(this, "关于", "版本、权限边界与开源许可", android.R.drawable.ic_menu_info_details) {
                navigate("about")
            }
        }
        return content
    }

    private fun addAnchorPicker(parent: LinearLayout) {
        val anchors = RadioGroup(activity).apply {
            orientation = RadioGroup.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12f), 0, dp(12f), dp(4f))
        }
        addAnchor(anchors, "左侧", HorizontalAnchor.Left)
        addAnchor(anchors, "居中", HorizontalAnchor.Center)
        addAnchor(anchors, "右侧", HorizontalAnchor.Right)
        parent.addView(anchors, matchWrap())
    }

    private fun settingCard(parent: LinearLayout, title: String? = null, content: LinearLayout.() -> Unit) {
        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedSurface()
            clipToOutline = true
        }
        if (!title.isNullOrBlank()) {
            card.addView(TextView(activity).apply {
                text = title
                textSize = 18f
                setTextColor(themeColor(android.R.attr.textColorPrimary))
                setPadding(dp(20f), dp(18f), dp(20f), dp(8f))
            }, matchWrap())
        }
        card.content()
        parent.addView(card, matchWrap().apply {
            setMargins(dp(12f), 0, dp(12f), dp(12f))
        })
    }

    private fun addCategoryRow(
        parent: LinearLayout,
        title: String,
        detail: String,
        iconRes: Int,
        onClick: () -> Unit,
    ) {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(72f)
            setPadding(dp(20f), dp(8f), dp(12f), dp(8f))
            background = selectableBackground()
            contentDescription = title
            setOnClickListener { onClick() }
        }
        val icon = ImageView(activity).apply {
            setImageResource(iconRes)
            imageTintList = android.content.res.ColorStateList.valueOf(themeColor(android.R.attr.colorAccent))
            background = roundedSurfaceVariant()
            setPadding(dp(9f), dp(9f), dp(9f), dp(9f))
            contentDescription = null
        }
        row.addView(icon, LinearLayout.LayoutParams(dp(42f), dp(42f)).apply {
            rightMargin = dp(14f)
        })
        row.addView(labelColumn(title, detail), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(ImageView(activity).apply {
            setImageResource(com.cxcboss.glassorb.R.drawable.ic_arrow_forward)
            alpha = 0.65f
            contentDescription = null
        }, LinearLayout.LayoutParams(dp(32f), dp(48f)))
        parent.addView(row, matchWrap())
        divider(parent)
    }

    private fun homeTabButton(iconRes: Int, description: String): ImageButton = ImageButton(activity).apply {
        setImageResource(iconRes)
        contentDescription = description
        scaleType = ImageView.ScaleType.CENTER
        setPadding(dp(12f), dp(12f), dp(12f), dp(12f))
        stateListAnimator = null
        isFocusable = true
    }

    private fun styleHomeTabButton(button: ImageButton, selected: Boolean) {
        button.isSelected = selected
        button.isActivated = selected
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            button.stateDescription = if (selected) "当前页面" else "切换页面"
        }
        button.background = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(if (selected) themeColor(android.R.attr.colorAccent) else Color.rgb(43, 46, 53))
        }
        button.imageTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
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
            addNote(this, "用于创建可信触控窗口并监听已知系统语音助手的窗口出现与消失；仅读取交互窗口的包名元数据，不读取文字、不截图、不录音、不执行手势、不监听按键。国产 ROM 如有电池优化或自启动管理，请将本应用设为允许。")
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
        addSwitchRow(
            content,
            "全屏时隐藏胶囊",
            "状态栏隐藏时隐藏胶囊和触控入口，系统语音助手唤醒时仍显示玻璃球",
            config.geometry.hideWhenStatusBarHidden,
        ) { enabled ->
            updateConfig { it.copy(geometry = it.geometry.copy(hideWhenStatusBarHidden = enabled)) }
        }
        addSwitchRow(
            content,
            "横屏隐藏",
            "横屏时隐藏灵动岛图形和触控区域，恢复竖屏后使用平滑动画显示",
            config.geometry.hideWhenLandscape,
        ) { enabled ->
            updateConfig { it.copy(geometry = it.geometry.copy(hideWhenLandscape = enabled)) }
        }
        addSlider(content, "球体直径", config.geometry.orbDiameterDp, OrbConfig.reference().geometry.orbDiameterDp, 88f..220f, "dp") { value ->
            updateConfig { current ->
                current.copy(geometry = current.geometry.copy(
                    orbDiameterDp = value,
                    orbWidthDp = value,
                    orbHeightDp = value,
                ))
            }
        }
        addSlider(content, "玻璃球宽度", config.geometry.orbWidthDp, OrbConfig.reference().geometry.orbWidthDp, 88f..260f, "dp") { value ->
            updateConfig { current -> current.copy(geometry = current.geometry.copy(orbWidthDp = value)) }
        }
        addSlider(content, "玻璃球高度", config.geometry.orbHeightDp, OrbConfig.reference().geometry.orbHeightDp, 88f..260f, "dp") { value ->
            updateConfig { current -> current.copy(geometry = current.geometry.copy(orbHeightDp = value)) }
        }
        addNote(content, "宽度与高度相同为圆形；调整其中一项即可变为椭圆。")
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
        addSubheading(content, "动态颜色")
        addSlider(
            content,
            "内部颜色",
            config.glass.internalHueDegrees,
            OrbConfig.reference().glass.internalHueDegrees,
            0f..360f,
            "°",
            decimals = 0,
        ) { value ->
            updateConfig { current -> current.copy(glass = current.glass.copy(internalHueDegrees = value)) }
        }
        addNote(content, "调整玻璃球内部波形与圆点的整体色相，焦散颜色会同步变化。")
        addSlider(
            content,
            "上半部高光颜色",
            config.glass.upperHighlightHueDegrees,
            OrbConfig.reference().glass.upperHighlightHueDegrees,
            0f..360f,
            "°",
            decimals = 0,
        ) { value ->
            updateConfig { current -> current.copy(glass = current.glass.copy(upperHighlightHueDegrees = value)) }
        }
        addSlider(
            content,
            "上半部高光强度",
            config.glass.upperHighlightAmount,
            OrbConfig.reference().glass.upperHighlightAmount,
            0f..2f,
            decimals = 2,
        ) { value ->
            updateConfig { current -> current.copy(glass = current.glass.copy(upperHighlightAmount = value)) }
        }
        addSubheading(content, "阴影与焦散")
        addSlider(content, "阴影", config.glass.shadowAmount, OrbConfig.reference().glass.shadowAmount, 0f..1.5f, decimals = 2) { value ->
            updateConfig { current -> current.copy(glass = current.glass.copy(shadowAmount = value)) }
        }
        addSlider(content, "焦散强度", config.glass.causticAmount, OrbConfig.reference().glass.causticAmount, 0f..3f, decimals = 2) { value ->
            updateConfig { current -> current.copy(glass = current.glass.copy(causticAmount = value)) }
        }
        addNote(content, "焦散颜色会自动跟随玻璃球内部颜色变化，此处仅调节焦散强度。")
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
        addSwitchRow(
            content,
            "背景局部模糊",
            "Android 12+ 且圆形玻璃球可用；椭圆或设备不支持时仅关闭模糊，保留现有效果",
            config.container.backgroundBlurEnabled,
        ) { enabled ->
            updateConfig { it.copy(container = it.container.copy(backgroundBlurEnabled = enabled)) }
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
        addSubheading(content, "配置文件")
        addNavigationRow(content, "备份与恢复", "导入或导出 JSON 配置") { navigate("data") }
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
        val titleColumn = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            addView(title, matchWrap())
            addView(TextView(activity).apply {
                text = sliderDescription(label)
                textSize = 12f
                maxLines = 2
                setTextColor(themeColor(android.R.attr.textColorSecondary))
            }, matchWrap())
        }
        labels.addView(titleColumn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
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

    private fun roundedSurfaceVariant(): android.graphics.drawable.Drawable = android.graphics.drawable.GradientDrawable().apply {
        setColor(themeColor(com.google.android.material.R.attr.colorSurfaceVariant))
        cornerRadius = dp(18f).toFloat()
    }

    private fun sliderDescription(label: String): String = when (label) {
        "顶部偏移" -> "控制灵动岛距离真实屏幕顶部的物理距离。"
        "水平微调" -> "在左、中、右锚点基础上做细微横向移动。"
        "胶囊宽度" -> "设置黑色胶囊的宽度，0 会隐藏图形。"
        "胶囊高度" -> "设置黑色胶囊的高度，0 会隐藏图形。"
        "球体直径" -> "同时调整玻璃球宽高，快速恢复圆形。"
        "玻璃球宽度" -> "单独调整玻璃球横向尺寸。"
        "玻璃球高度" -> "单独调整玻璃球纵向尺寸。"
        "外部效果余量" -> "给发光、阴影和回弹预留额外画布空间。"
        "效果画布比例" -> "放大效果画布，避免边缘发光被裁切。"
        "触摸区域倍率" -> "按胶囊尺寸放大触控范围，关闭扩大后不生效。"
        "内部深度" -> "控制球体内部色彩和空间感的深度。"
        "曲率" -> "控制玻璃球边缘的弯曲与折射程度。"
        "高光亮度" -> "调整玻璃球整体高光的明暗。"
        "高光宽度" -> "调整高光带的扩散宽度。"
        "高光收束" -> "调整高光向边缘收束的程度。"
        "内部颜色" -> "旋转球体内部波形与圆点的整体色相。"
        "上半部高光颜色" -> "调整球体上半部跳动高光的色相。"
        "上半部高光强度" -> "控制非黑色上半部高光的可见程度。"
        "阴影" -> "调整球体边缘暗部的深浅。"
        "焦散强度" -> "调整随内部颜色自动变化的焦散亮度。"
        "阴影偏移" -> "移动暗部的光影方向。"
        "焦散偏移" -> "移动焦散纹理的光影方向。"
        "光影柔度" -> "让阴影和焦散边缘更硬或更柔和。"
        "暗部强度", "强度" -> "调节屏幕压暗或球体暗部的整体强度。"
        "暗部渐隐跨度", "渐隐跨度" -> "控制黑色从 28% 向 0% 透明度过渡的范围。"
        "暗部衰减" -> "控制暗部从中心向外衰减的速度。"
        "振幅" -> "调整波形上下起伏的幅度。"
        "尺度" -> "调整波形在球体内的整体尺度。"
        "色散" -> "调整边缘红蓝色彩分离的程度。"
        "线宽" -> "调整波形光带的线条粗细。"
        "亮度" -> "调整波形光带的整体亮度。"
        "填光" -> "调整波形内部的填充亮度。"
        "填光厚度" -> "调整波形填充区域的厚度。"
        "柔化" -> "调整波形边缘的柔和程度。"
        "白色辉光" -> "调整光带外沿的白色辉光。"
        "色相偏移" -> "整体偏移波形光带的色相。"
        "环半径" -> "调整思考圆点环绕中心的半径。"
        "点半径" -> "调整每个思考圆点的大小。"
        "辉光" -> "调整思考圆点周围的光晕。"
        "转速" -> "调整思考圆点的环绕速度与方向。"
        "收起跟手距离" -> "调整上滑多少距离会触发收起。"
        "拖拽弹性范围" -> "调整横向拖动的最大回弹范围。"
        "拖拽响应系数" -> "调整拖动跟手的灵敏度。"
        "形变位移上限" -> "限制胶囊在拖动时的位移形变。"
        "形变幅度" -> "调整胶囊被拉伸或压缩的幅度。"
        "形变回弹反应" -> "控制形变恢复速度，数值越大越慢。"
        "形变回弹阻尼" -> "控制形变回弹时的余韵。"
        "展开响应" -> "控制玻璃球展开的弹簧响应时间。"
        "展开阻尼" -> "控制展开后的回弹与稳定速度。"
        "收起响应" -> "控制上滑收起的弹簧响应时间。"
        "收起阻尼" -> "控制收起后的多层回弹余韵。"
        "收起回弹幅度" -> "控制胶囊飞出后超短距离回弹的幅度。"
        "波形渐入延迟" -> "控制展开后波形内容出现的延迟。"
        "呼吸幅度" -> "调整静态胶囊与展开状态的轻微呼吸感。"
        "呼吸速度" -> "调整呼吸动效的节奏。"
        "点击放大" -> "调整按下胶囊时的轻微放大反馈。"
        "思考停留" -> "控制分析状态的停留时间。"
        "等待时间" -> "无操作后自动收起前的等待时间。"
        "胶囊帧率" -> "仅影响仍有动画时的胶囊渲染频率。"
        "展开帧率" -> "控制玻璃球展开时的渲染频率。"
        "渲染比例" -> "平衡玻璃球清晰度与 GPU 压力。"
        else -> "拖动调整此项，右侧数值会即时更新。"
    }

    private fun findFirstScroll(view: View): ScrollView? {
        if (view is ScrollView) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                findFirstScroll(view.getChildAt(index))?.let { return it }
            }
        }
        return null
    }

    private fun screenKey(screen: Screen): String = when (screen) {
        Screen.Home -> "home"
        Screen.Overlay -> "overlay"
        is Screen.Group -> "group:${screen.group.name}"
        Screen.Presets -> "presets"
        Screen.Data -> "data"
        Screen.About -> "about"
        Screen.Licenses -> "licenses"
    }

    private fun decodeScreen(value: String?): Screen? = when {
        value.isNullOrBlank() -> null
        value == "home" -> Screen.Home
        value == "overlay" -> Screen.Overlay
        value == "presets" -> Screen.Presets
        value == "data" -> Screen.Data
        value == "about" -> Screen.About
        value == "licenses" -> Screen.Licenses
        value.startsWith("group:") -> value.removePrefix("group:")
            .let { name -> ConfigGroup.entries.firstOrNull { it.name == name }?.let(Screen::Group) }
        else -> null
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
        Screen.Home -> "常用设置"
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

    private companion object {
        const val SAVED_STATE_KEY = "native_settings_state"
        const val KEY_HOME_TAB = "home_tab"
        const val KEY_SCREEN = "screen"
        const val KEY_SCROLL_Y = "scroll_y"
    }
}
