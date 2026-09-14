package com.cxcboss.glassorb.ui

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
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
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.activity.BackEventCompat
import com.cxcboss.glassorb.BuildConfig
import com.cxcboss.glassorb.data.ConfigGroup
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
    val resetGroup: (ConfigGroup) -> Unit,
    val setAutoStartEnabled: (Boolean) -> Unit,
)

/**
 * Native Android settings surface. Layout remains ordinary Android Views while
 * interactive controls use the official Material 3 View implementations. No
 * app-specific imitation of sliders, switches or dialogs is used here.
 */
internal class NativeSettingsController(
    private val activity: ComponentActivity,
    private val root: FrameLayout,
    private val callbacks: NativeSettingsCallbacks,
    private val initialScreenKey: String? = null,
    private val onRootBack: (() -> Unit)? = null,
) {
    private sealed interface Screen {
        data object Home : Screen
        data object Overlay : Screen
        data class Group(val group: ConfigGroup) : Screen
        data object About : Screen
    }

    private enum class ParameterId { TouchAreaScale }

    private data class PageEntry(
        val screen: Screen,
        val view: View,
    )

    private val density = activity.resources.displayMetrics.density
    private val refreshRateController = SettingsRefreshRateController(activity)
    private val pages = ArrayDeque<PageEntry>()
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
        // Controls update their own visual state in-place. Rebuilding a page
        // after every switch/radio interaction interrupts its pressed feedback
        // and makes taps feel unreliable. Only explicit bulk changes request a
        // refresh to reflect values that changed outside the active control.
        val refreshScreen = refreshAfterConfigFor
        if (changed && refreshScreen != null) {
            refreshAfterConfigFor = null
            if (refreshScreen == currentScreen()) refreshCurrentPage()
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
                "about" -> Screen.About
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
            .setDuration(PAGE_NAVIGATION_DURATION_MS)
            .setInterpolator(PathInterpolator(0.2f, 0f, 0f, 1f))
            .withEndAction { navigationAnimating = false }
            .start()
    }

    fun goBack(): Boolean {
        if (pages.size <= 1) {
            onRootBack?.invoke()
            return onRootBack != null
        }
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
            .setDuration(PAGE_NAVIGATION_DURATION_MS)
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
        val initial = decodeScreen(initialScreenKey) ?: Screen.Home
        val page = createPage(initial)
        root.addView(page, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        pages.addLast(PageEntry(initial, page))
    }

    private fun restoreTopPage(state: Bundle?) {
        val restored = decodeScreen(state?.getString(KEY_SCREEN)) ?: return
        if (restored == currentScreen()) {
            val scrollY = state?.getInt(KEY_SCROLL_Y, 0) ?: 0
            pages.peekLast()?.view?.let { view ->
                findFirstScroll(view)?.let { scroll -> scroll.post { scroll.scrollTo(0, scrollY) } }
            }
            return
        }
        if (currentScreen() != Screen.Home) return
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
            title = ui(screenTitle(screen))
            setTitleTextColor(themeColor(android.R.attr.textColorPrimary))
            setBackgroundColor(themeColor(android.R.attr.colorBackground))
            // Material 3 uses a flat app bar; the edge tint only appears while
            // content scrolls beneath it, so do not add a permanent shadow.
            elevation = 0f
            minimumHeight = actionBarHeight()
        }
        if (screen !is Screen.Home) {
            toolbar.navigationIcon = ContextCompat.getDrawable(activity, com.cxcboss.glassorb.R.drawable.ic_arrow_back)
            toolbar.navigationContentDescription = ui("返回")
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
            setPadding(0, 0, 0, dp(32f))
                addView(buildScreen(screen), ViewGroup.LayoutParams.MATCH_PARENT)
            }
            refreshRateController.attach(scroll)
            page.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        return page
    }

    private fun buildScreen(screen: Screen): View = when (screen) {
        Screen.Home -> buildHome()
        Screen.Overlay -> buildOverlayDetails()
        is Screen.Group -> buildGroup(screen.group)
        Screen.About -> buildAbout()
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
            setPadding(0, 0, 0, dp(100f))
        }
        refreshRateController.attach(scroll)
        shell.addView(scroll, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val bottomBar = FrameLayout(activity).apply {
            // Use an ordinary rounded surface so MaterialCardView's theme
            // tint/foreground cannot replace the requested white/dark pill.
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(56f).toFloat()
                setColor(homeBottomBarColor())
            }
            clipToOutline = true
            elevation = dp(8f).toFloat()
            contentDescription = ui("设置页面切换")
        }
        val bottomButtons = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(6f), dp(4f), dp(6f), dp(4f))
        }
        val commonButton = homeTabButton(com.cxcboss.glassorb.R.drawable.ic_home_common, "常用")
        val detailButton = homeTabButton(com.cxcboss.glassorb.R.drawable.ic_home_detail, "详细")
        installPressScale(commonButton, bottomBar)
        installPressScale(detailButton, bottomBar)
        installPressScale(bottomBar)
        bottomButtons.addView(commonButton, LinearLayout.LayoutParams(0, dp(48f), 1f).apply {
            rightMargin = dp(2f)
        })
        bottomButtons.addView(detailButton, LinearLayout.LayoutParams(0, dp(48f), 1f).apply {
            leftMargin = dp(2f)
        })
        bottomBar.addView(bottomButtons, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        shell.addView(bottomBar, FrameLayout.LayoutParams(dp(156f), dp(56f)).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            bottomMargin = dp(10f)
        })

        fun selectTab(tab: HomeTab) {
            selectedTab = tab
            homeTab = tab
            val common = tab == HomeTab.Common
            styleHomeTabButton(commonButton, common)
            styleHomeTabButton(detailButton, !common)
            toolbar?.title = ui(if (common) "常用设置" else "详细设置")
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
            addSwitchRow(this, "显示灵动岛", null, runtimeStatus == OverlayRuntimeStatus.Visible) { enabled ->
                when (resolveOverlayAction(enabled, overlayPermission, runtimeStatus)) {
                    OverlayAction.RequestPermission -> callbacks.requestOverlayPermission()
                    OverlayAction.Show -> callbacks.showOverlay()
                    OverlayAction.Start -> callbacks.startOverlay()
                    OverlayAction.Hide -> callbacks.hideOverlay()
                    OverlayAction.None -> Unit
                }
            }
            addSwitchRow(this, "向下展开", null, config.geometry.expandBelowCapsule) { enabled ->
                updateConfig { it.copy(geometry = it.geometry.copy(expandBelowCapsule = enabled)) }
            }
        }

        addSubheading(content, "尺寸与位置")
        settingCard(content, "灵动岛大小") {
            addSlider(this, "宽度", config.geometry.capsuleWidthDp, OrbConfig.reference().geometry.capsuleWidthDp, 0f..220f, "dp") { value ->
                updateConfig { it.copy(geometry = it.geometry.copy(capsuleWidthDp = value)) }
            }
            addSlider(this, "高度", config.geometry.capsuleHeightDp, OrbConfig.reference().geometry.capsuleHeightDp, 0f..64f, "dp") { value ->
                updateConfig { it.copy(geometry = it.geometry.copy(capsuleHeightDp = value)) }
            }
        }
        settingCard(content, "位置") {
            val verticalDensity = activity.resources.displayMetrics.density.coerceAtLeast(0.1f)
            addSlider(
                this,
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
            addSlider(this, "水平", config.geometry.horizontalOffsetDp, OrbConfig.reference().geometry.horizontalOffsetDp, -200f..200f, "dp") { value ->
                updateConfig { current -> current.copy(geometry = current.geometry.copy(horizontalOffsetDp = value)) }
            }
        }
        settingCard(content, "玻璃球大小") {
            addSlider(this, "宽度", config.geometry.orbWidthDp, OrbConfig.reference().geometry.orbWidthDp, 88f..260f, "dp") { value ->
                updateConfig { it.copy(geometry = it.geometry.copy(orbWidthDp = value)) }
            }
            addSlider(this, "高度", config.geometry.orbHeightDp, OrbConfig.reference().geometry.orbHeightDp, 88f..260f, "dp") { value ->
                updateConfig { it.copy(geometry = it.geometry.copy(orbHeightDp = value)) }
            }
        }

        section(content, "背景效果") { addBackgroundEffectControls(this) }
        return content
    }

    private fun buildMoreContent(): View {
        val content = column()
        addSubheading(content, "全部设置")
        settingCard(content) {
            addCategoryRow(this, "位置与触控", "位置、尺寸和触控", com.cxcboss.glassorb.R.drawable.ic_category_geometry) {
                navigate(ConfigGroup.Geometry)
            }
            addCategoryRow(this, "颜色与光影", "颜色、高光和阴影", com.cxcboss.glassorb.R.drawable.ic_category_colors) {
                navigate(ConfigGroup.Glass)
            }
            addCategoryRow(this, "背景与暗部", "三种背景效果与暗部", com.cxcboss.glassorb.R.drawable.ic_category_container) {
                navigate(ConfigGroup.Container)
            }
            addCategoryRow(this, "波形与光带", "波形、色散和亮度", com.cxcboss.glassorb.R.drawable.ic_category_wave) {
                navigate(ConfigGroup.Wave)
            }
            addCategoryRow(this, "圆点与粒子", "圆点、光晕和旋转", com.cxcboss.glassorb.R.drawable.ic_category_dots) {
                navigate(ConfigGroup.Dots)
            }
            addCategoryRow(this, "手势与动效", "展开、收起与拖动", com.cxcboss.glassorb.R.drawable.ic_category_motion) {
                navigate(ConfigGroup.Motion)
            }
            addCategoryRow(this, "性能与清晰度", "渲染比例与刷新率", com.cxcboss.glassorb.R.drawable.ic_category_performance) {
                navigate(ConfigGroup.Performance)
            }
        }

        addSubheading(content, "其它")
        settingCard(content) {
            addCategoryRow(this, "运行与权限", "权限与系统兼容", com.cxcboss.glassorb.R.drawable.ic_category_accessibility) {
                navigate("overlay")
            }
            addCategoryRow(this, "关于", "版本与来源", com.cxcboss.glassorb.R.drawable.ic_category_about) {
                navigate("about")
            }
        }
        return content
    }

    private fun settingCard(parent: LinearLayout, title: String? = null, content: LinearLayout.() -> Unit) {
        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedSurface()
            clipToOutline = true
        }
        if (!title.isNullOrBlank()) {
            card.addView(TextView(activity).apply {
                text = ui(title)
                textSize = 18f
                setTextColor(themeColor(android.R.attr.textColorPrimary))
            setPadding(dp(20f), dp(20f), dp(20f), dp(10f))
            }, matchWrap())
        }
        card.content()
        parent.addView(card, matchWrap().apply {
            setMargins(dp(16f), 0, dp(16f), dp(16f))
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
            minimumHeight = dp(80f)
            setPadding(dp(20f), dp(10f), dp(12f), dp(10f))
            background = selectableBackground()
            contentDescription = ui(title)
            isClickable = true
            isFocusable = true
        }
        row.setOnClickListener { onClick() }
        val icon = ImageView(activity).apply {
            setImageResource(iconRes)
            imageTintList = android.content.res.ColorStateList.valueOf(themeColor(android.R.attr.textColorPrimary))
            contentDescription = null
        }
        row.addView(icon, LinearLayout.LayoutParams(dp(28f), dp(48f)).apply {
            rightMargin = dp(14f)
        })
        row.addView(
            labelColumn(title, detail),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        row.addView(ImageView(activity).apply {
            setImageResource(com.cxcboss.glassorb.R.drawable.ic_arrow_forward)
            alpha = 0.65f
            contentDescription = null
        }, LinearLayout.LayoutParams(dp(32f), dp(48f)))
        parent.addView(row, matchWrap())
        divider(parent)
    }

    private fun homeTabButton(iconRes: Int, description: String): LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        minimumHeight = dp(48f)
        setPadding(dp(8f), dp(3f), dp(8f), dp(3f))
        contentDescription = ui(description)
        stateListAnimator = null
        isFocusable = true
        isClickable = true
        addView(ImageView(activity).apply {
            tag = "home-tab-icon"
            setImageResource(iconRes)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
        }, LinearLayout.LayoutParams(dp(22f), dp(22f)))
        addView(TextView(activity).apply {
            tag = "home-tab-label"
            text = ui(description)
            textSize = 11f
            maxLines = 1
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(18f)))
    }

    private fun installPressScale(view: View, target: View = view) {
        val ease = PathInterpolator(0.2f, 0f, 0f, 1f)
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    target.animate().cancel()
                    target.animate()
                        .scaleX(1.024f)
                        .scaleY(1.024f)
                        .setDuration(220L)
                        .setInterpolator(ease)
                        .start()
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    target.animate().cancel()
                    target.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(320L)
                        .setInterpolator(ease)
                        .start()
                }
            }
            // Keep the normal View touch pipeline active so the existing
            // click listener and accessibility semantics remain unchanged.
            false
        }
    }

    private fun styleHomeTabButton(button: LinearLayout, selected: Boolean) {
        button.isSelected = selected
        button.isActivated = selected
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            button.stateDescription = ui(if (selected) "当前页面" else "切换页面")
        }
        val foreground = if (selected) {
            themeColor(android.R.attr.colorAccent)
        } else {
            themeColor(android.R.attr.textColorSecondary)
        }
        button.background = android.graphics.drawable.GradientDrawable().apply {
            // The selected area is an inner pill, with the same radius and
            // surface color as the floating bar around it.
            cornerRadius = dp(48f).toFloat()
            setColor(
                if (!selected) Color.TRANSPARENT
                else homeBottomBarColor(),
            )
        }
        button.clipToOutline = true
        (button.findViewWithTag<ImageView>("home-tab-icon"))?.imageTintList =
            android.content.res.ColorStateList.valueOf(foreground)
        (button.findViewWithTag<TextView>("home-tab-label"))?.setTextColor(foreground)
    }

    private fun homeBottomBarColor(): Int =
        if (isDarkTheme()) Color.rgb(35, 36, 39) else Color.WHITE

    private fun buildOverlayDetails(): View {
        val content = column()
        val status = runtimeStatus
        section(content, "状态") {
            addTextRow(this, "运行状态", nativeStatusLabel(status))
            addNavigationRow(this, "悬浮窗权限", if (overlayPermission) "已允许" else "未允许") {
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
                if (accessibilityEnabled) "已开启 · 用于状态栏触控" else "未开启 · 点击授权",
            ) { callbacks.requestAccessibilityPermission() }
            addNote(this, "仅用于状态栏触控和识别已知助手窗口；不读取文字、截图、录音或执行手势。")
        }
        addNote(content, "首页开关可显示或隐藏灵动岛；锁屏、安全页面和系统状态栏不保证显示。")
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
        addSlider(content, "水平微调", config.geometry.horizontalOffsetDp, OrbConfig.reference().geometry.horizontalOffsetDp, -200f..200f, "dp") { value ->
            updateConfig { current -> current.copy(geometry = current.geometry.copy(horizontalOffsetDp = value)) }
        }

        addSubheading(content, "灵动岛尺寸")
        addSlider(content, "灵动岛宽度", config.geometry.capsuleWidthDp, OrbConfig.reference().geometry.capsuleWidthDp, 0f..220f, "dp") { value ->
            updateConfig { current -> current.copy(geometry = current.geometry.copy(capsuleWidthDp = value)) }
        }
        addSlider(content, "灵动岛高度", config.geometry.capsuleHeightDp, OrbConfig.reference().geometry.capsuleHeightDp, 0f..64f, "dp") { value ->
            updateConfig { current -> current.copy(geometry = current.geometry.copy(capsuleHeightDp = value)) }
        }
        addSubheading(content, "玻璃球")
        addSwitchRow(content, "向下展开", null, config.geometry.expandBelowCapsule) { enabled ->
            updateConfig { it.copy(geometry = it.geometry.copy(expandBelowCapsule = enabled)) }
        }
        addSwitchRow(
            content,
            "全屏时隐藏灵动岛",
            "全屏时隐藏。",
            config.geometry.hideWhenStatusBarHidden,
        ) { enabled ->
            updateConfig { it.copy(geometry = it.geometry.copy(hideWhenStatusBarHidden = enabled)) }
        }
        addSwitchRow(
            content,
            "横屏隐藏",
            "横屏时隐藏。",
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
        addSlider(content, "外部效果余量", config.geometry.outerMarginDp, OrbConfig.reference().geometry.outerMarginDp, 8f..48f, "dp") { value ->
            updateConfig { current -> current.copy(geometry = current.geometry.copy(outerMarginDp = value)) }
        }
        addSlider(content, "效果画布比例", config.geometry.effectScale, OrbConfig.reference().geometry.effectScale, 0.9f..1.5f, decimals = 2) { value ->
            updateConfig { current -> current.copy(geometry = current.geometry.copy(effectScale = value)) }
        }
        addSubheading(content, "灵动岛触控")
        lateinit var touchScaleRow: View
        addSwitchRow(content, "扩大灵动岛触摸区域", null, config.geometry.enlargedTouchArea) { enabled ->
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
        addSubheading(content, "背景效果")
        addBackgroundEffectControls(content)
        addSubheading(content, "球体暗部")
        addSlider(content, "强度", config.container.strength, OrbConfig.reference().container.strength, 0f..4f, decimals = 2) { value ->
            updateConfig { current -> current.copy(container = current.container.copy(strength = value)) }
        }
        addSlider(content, "渐隐跨度", config.container.fade, OrbConfig.reference().container.fade, 0f..2f, decimals = 2) { value ->
            updateConfig { current -> current.copy(container = current.container.copy(fade = value)) }
        }
        addSlider(content, "暗部衰减", config.container.gaussian, OrbConfig.reference().container.gaussian, 0.5f..16f, decimals = 1) { value ->
            updateConfig { current -> current.copy(container = current.container.copy(gaussian = value)) }
        }
    }

    /** Keeps the three independently named background treatments identical on home and detail pages. */
    private fun addBackgroundEffectControls(parent: LinearLayout) {
        lateinit var lowerBlurRow: View
        lateinit var lowerBlurAmountRow: View
        lateinit var dimAmountRow: View
        lateinit var glassBlurRow: View
        lateinit var glassBlurAmountRow: View

        lowerBlurRow = addSwitchRow(
            parent,
            "下层模糊",
            null,
            config.container.backgroundAmbientBlurEnabled,
        ) { enabled ->
            setControlEnabled(glassBlurRow, !enabled)
            setControlEnabled(lowerBlurAmountRow, enabled)
            setControlEnabled(glassBlurAmountRow, !enabled && config.container.backgroundBlurEnabled)
            updateConfig { current ->
                current.copy(
                    container = current.container.copy(
                        backgroundAmbientBlurEnabled = enabled,
                        backgroundBlurEnabled = if (enabled) false else current.container.backgroundBlurEnabled,
                    ),
                )
            }
        }
        lowerBlurAmountRow = addSlider(
            parent,
            "下层模糊度",
            config.container.backgroundAmbientBlurPercent,
            OrbConfig.reference().container.backgroundAmbientBlurPercent,
            0f..100f,
            "%",
            decimals = 0,
            enabled = config.container.backgroundAmbientBlurEnabled,
        ) { value ->
            updateConfig { current ->
                current.copy(container = current.container.copy(backgroundAmbientBlurPercent = value))
            }
        }

        addSwitchRow(parent, "下层变暗", null, config.container.backgroundDimEnabled) { enabled ->
            setControlEnabled(dimAmountRow, enabled)
            updateConfig { current ->
                current.copy(container = current.container.copy(backgroundDimEnabled = enabled))
            }
        }
        dimAmountRow = addSlider(
            parent,
            "变暗程度",
            config.container.backgroundDimPercent,
            OrbConfig.reference().container.backgroundDimPercent,
            0f..60f,
            "%",
            decimals = 0,
            enabled = config.container.backgroundDimEnabled,
        ) { value ->
            updateConfig { current ->
                current.copy(container = current.container.copy(backgroundDimPercent = value))
            }
        }

        glassBlurRow = addSwitchRow(
            parent,
            "玻璃球模糊",
            null,
            config.container.backgroundBlurEnabled,
        ) { enabled ->
            setControlEnabled(lowerBlurRow, !enabled)
            setControlEnabled(lowerBlurAmountRow, !enabled && config.container.backgroundAmbientBlurEnabled)
            setControlEnabled(glassBlurAmountRow, enabled)
            updateConfig { current ->
                current.copy(
                    container = current.container.copy(
                        backgroundBlurEnabled = enabled,
                        backgroundAmbientBlurEnabled = if (enabled) false else current.container.backgroundAmbientBlurEnabled,
                    ),
                )
            }
        }
        glassBlurAmountRow = addSlider(
            parent,
            "玻璃球模糊度",
            config.container.backgroundBlurPercent,
            OrbConfig.reference().container.backgroundBlurPercent,
            0f..100f,
            "%",
            decimals = 0,
            enabled = config.container.backgroundBlurEnabled,
        ) { value ->
            updateConfig { current ->
                current.copy(container = current.container.copy(backgroundBlurPercent = value))
            }
        }

        setControlEnabled(lowerBlurRow, !config.container.backgroundBlurEnabled)
        setControlEnabled(lowerBlurAmountRow, config.container.backgroundAmbientBlurEnabled)
        setControlEnabled(dimAmountRow, config.container.backgroundDimEnabled)
        setControlEnabled(glassBlurRow, !config.container.backgroundAmbientBlurEnabled)
        setControlEnabled(glassBlurAmountRow, config.container.backgroundBlurEnabled)
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
        addSwitchRow(content, "自动收起", null, config.motion.autoCollapseEnabled) { enabled ->
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
        addNote(content, "展开、收起与玻璃球按 120 fps 请求刷新。")
        addSlider(content, "渲染比例", config.performance.renderScale, OrbConfig.reference().performance.renderScale, 0.5f..1.25f, decimals = 2) { value ->
            updateConfig { current -> current.copy(performance = current.performance.copy(renderScale = value)) }
        }
    }

    private fun buildAbout(): View {
        val content = column()
        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = roundedSurface()
            clipToOutline = true
            setPadding(dp(24f), dp(24f), dp(24f), dp(24f))
            val icon = ImageView(activity).apply {
                setImageResource(com.cxcboss.glassorb.R.mipmap.ic_launcher)
                contentDescription = ui("应用图标")
            }
            addView(icon, LinearLayout.LayoutParams(dp(72f), dp(72f)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(16f)
            })
            addCenteredAboutText(ui("App 版本"), BuildConfig.VERSION_NAME)
            addCenteredAboutText(ui("获取 App"), ui("关注抖音或微信公众号“你好小机”"))
            val source = TextView(activity).apply {
                text = ui("仓库参考")
                textSize = 15f
                gravity = Gravity.CENTER
                setTextColor(themeColor(android.R.attr.colorAccent))
                background = selectableBackground()
                contentDescription = ui("打开参考仓库")
                isClickable = true
                isFocusable = true
                setPadding(dp(16f), dp(12f), dp(16f), dp(6f))
                setOnClickListener {
                    activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(REFERENCE_REPOSITORY_URL)))
                }
            }
            addView(source, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dp(20f)
            })
            addView(TextView(activity).apply {
                text = ui("玻璃球视觉与动画参考开源学习项目")
                textSize = 12f
                gravity = Gravity.CENTER
                setTextColor(themeColor(android.R.attr.textColorSecondary))
                maxLines = 2
                setLineSpacing(dp(3f).toFloat(), 1f)
            }, matchWrap())
        }
        content.addView(card, matchWrap().apply { setMargins(dp(16f), dp(16f), dp(16f), dp(16f)) })
        return content
    }

    private fun LinearLayout.addCenteredAboutText(label: String, value: String) {
        addView(TextView(activity).apply {
            text = label
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(themeColor(android.R.attr.textColorSecondary))
        }, matchWrap().apply { topMargin = dp(8f) })
        addView(TextView(activity).apply {
            text = value
            textSize = 17f
            gravity = Gravity.CENTER
            setTextColor(themeColor(android.R.attr.textColorPrimary))
            maxLines = 2
            setLineSpacing(dp(2f).toFloat(), 1f)
        }, matchWrap())
    }

    private fun addAnchor(group: RadioGroup, label: String, anchor: HorizontalAnchor) {
        val radio = MaterialRadioButton(activity).apply {
            text = ui(label)
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
            text = ui(label)
            textSize = 16f
            setTextColor(themeColor(android.R.attr.textColorPrimary))
        }
        val titleColumn = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            addView(title, matchWrap())
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
            text = ui("还原")
            minHeight = dp(44f)
            minimumWidth = dp(64f)
            insetTop = 0
            insetBottom = 0
            elevation = 0f
            stateListAnimator = null
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.TRANSPARENT)
            setTextColor(themeColor(android.R.attr.colorAccent))
            contentDescription = "${ui(label)} ${ui("还原默认值")}"
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
            contentDescription = "${ui(label)} ${ui("调节")}"
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
                if (parameterId == ParameterId.TouchAreaScale && OverlayRuntime.status.value == OverlayRuntimeStatus.Visible) {
                    OrbOverlayService.setTouchPreview(activity, true)
                }
            }

            override fun onStopTrackingTouch(slider: Slider) {
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
    ): View {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(72f)
            setPadding(dp(16f), dp(4f), dp(12f), dp(4f))
            background = selectableBackground()
            contentDescription = ui(title)
        }
        val toggle = MaterialSwitch(activity).apply {
            isChecked = checked
            contentDescription = ui(title)
            setOnCheckedChangeListener { _, value -> onChanged(value) }
        }
        row.addView(
            labelColumn(title, detail),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        row.addView(toggle, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(48f)))
        row.setOnClickListener { toggle.performClick() }
        parent.addView(row, matchWrap())
        divider(parent)
        return row
    }

    private fun addNavigationRow(parent: LinearLayout, title: String, detail: String? = null, onClick: () -> Unit) {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(64f)
            setPadding(dp(16f), dp(4f), dp(12f), dp(4f))
            background = selectableBackground()
            contentDescription = ui(title)
            isClickable = true
            isFocusable = true
        }
        row.setOnClickListener { onClick() }
        row.addView(
            labelColumn(title, detail),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
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
            text = ui(title)
            textSize = 16f
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(64f)
            setPadding(dp(16f), 0, dp(16f), 0)
            setTextColor(if (destructive) destructiveColor() else themeColor(android.R.attr.colorAccent))
            background = selectableBackground()
            contentDescription = ui(title)
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
            text = ui(title)
            tag = "section-heading"
            textSize = 14f
            setTextColor(themeColor(android.R.attr.textColorSecondary))
            textSize = 16f
            setPadding(dp(20f), dp(28f), dp(20f), dp(10f))
        }
        parent.addView(heading, matchWrap())
    }

    private fun addNote(parent: LinearLayout, text: String) {
        val note = detailText(text, maxLines = Int.MAX_VALUE).apply {
            setPadding(dp(16f), dp(8f), dp(16f), dp(8f))
            textSize = 13f
        }
        parent.addView(note, matchWrap())
    }

    private fun detailText(text: String, maxLines: Int = 2): TextView = TextView(activity).apply {
        this.text = ui(text)
        textSize = 12f
        this.maxLines = maxLines
        setLineSpacing(dp(2f).toFloat(), 1f)
        setTextColor(themeColor(android.R.attr.textColorSecondary))
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
        parent.addView(group, matchWrap().apply { setMargins(dp(16f), 0, dp(16f), dp(16f)) })
    }

    private fun labelColumn(
        title: String,
        detail: String?,
    ): LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_VERTICAL
        val titleView = TextView(activity).apply {
            id = android.R.id.text1
            text = ui(title)
            textSize = 16f
            setTextColor(themeColor(android.R.attr.textColorPrimary))
        }
        addView(titleView, matchWrap())
        if (!detail.isNullOrBlank()) addView(detailText(detail), matchWrap())
    }

    private fun column(): LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(themeColor(android.R.attr.colorBackground))
        setPadding(0, dp(12f), 0, 0)
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
        cornerRadius = dp(24f).toFloat()
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
        Screen.About -> "about"
    }

    private fun decodeScreen(value: String?): Screen? = when {
        value.isNullOrBlank() -> null
        value == "home" -> Screen.Home
        value == "overlay" -> Screen.Overlay
        value == "about" -> Screen.About
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
            .setTitle(ui("恢复本组默认参数？"))
            .setMessage(if (isChineseLanguage()) "仅恢复“${group.title()}”中的参数。" else "Restore only the ${ui(group.title())} settings.")
            .setNegativeButton(ui("取消"), null)
            .setPositiveButton(ui("恢复")) { _, _ ->
                requestCurrentPageRefresh()
                callbacks.resetGroup(group)
                Toast.makeText(activity, ui("已恢复本组默认值"), Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun screenTitle(screen: Screen): String = when (screen) {
        Screen.Home -> "常用设置"
        Screen.Overlay -> "运行权限"
        is Screen.Group -> screen.group.title()
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

    private fun isChineseLanguage(): Boolean =
        activity.resources.configuration.locales[0].language.equals("zh", ignoreCase = true)

    /** Uses the system locale; Chinese stays native while other locales receive English UI copy. */
    private fun ui(source: String): String = if (isChineseLanguage()) source else ENGLISH_TEXT[source] ?: source

    private fun actionBarHeight(): Int {
        val value = TypedValue()
        activity.theme.resolveAttribute(android.R.attr.actionBarSize, value, true)
        return TypedValue.complexToDimensionPixelSize(value.data, activity.resources.displayMetrics)
            .coerceAtLeast(dp(64f))
    }

    private fun dp(value: Float): Int = (value * density + 0.5f).roundToInt()

    private fun matchWrap() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

    private companion object {
        const val REFERENCE_REPOSITORY_URL = "https://github.com/cxcboss/glass-voice-orb-study"
        const val PAGE_NAVIGATION_DURATION_MS = 220L
        const val SAVED_STATE_KEY = "native_settings_state"
        const val KEY_HOME_TAB = "home_tab"
        const val KEY_SCREEN = "screen"
        const val KEY_SCROLL_Y = "scroll_y"
        val ENGLISH_TEXT = mapOf(
            "常用" to "Common", "详细" to "Details", "常用设置" to "Common settings", "详细设置" to "All settings",
            "返回" to "Back", "设置页面切换" to "Settings page switch", "当前页面" to "Current page", "切换页面" to "Switch page", "全部设置" to "All settings", "其它" to "Other",
            "灵动岛" to "Dynamic Island", "显示灵动岛" to "Show Dynamic Island", "向下展开" to "Expand downward",
            "尺寸与位置" to "Size & position", "灵动岛大小" to "Dynamic Island size", "宽度" to "Width", "高度" to "Height",
            "位置" to "Position", "顶部偏移" to "Top offset", "水平" to "Horizontal", "玻璃球大小" to "Glass orb size",
            "背景效果" to "Background effects", "位置与触控" to "Position & touch", "位置、尺寸和触控" to "Position, size and touch",
            "颜色与光影" to "Color & lighting", "颜色、高光和阴影" to "Color, highlights and shadows",
            "背景与暗部" to "Background & shading", "三种背景效果与暗部" to "Background effects and shading",
            "波形与光带" to "Waves & light bands", "波形、色散和亮度" to "Waves, dispersion and brightness",
            "圆点与粒子" to "Dots & particles", "圆点、光晕和旋转" to "Dots, glow and rotation",
            "手势与动效" to "Gestures & motion", "展开、收起与拖动" to "Expand, collapse and drag",
            "性能与清晰度" to "Performance & clarity", "渲染比例与刷新率" to "Render scale and refresh rate",
            "运行与权限" to "Run & permissions", "运行权限" to "Run & permissions", "权限与系统兼容" to "Permissions & compatibility",
            "关于" to "About", "版本与来源" to "Version & sources", "状态" to "Status", "运行状态" to "Runtime status",
            "悬浮窗权限" to "Overlay permission", "已允许" to "Allowed", "未允许" to "Not allowed", "错误" to "Error",
            "状态栏触控" to "Status bar touch", "无障碍触控服务" to "Accessibility touch service",
            "已开启 · 用于状态栏触控" to "On · for status bar touch", "未开启 · 点击授权" to "Off · tap to allow",
            "仅用于状态栏触控和识别已知助手窗口；不读取文字、截图、录音或执行手势。" to "Used only for status-bar touch and known assistant windows. It does not read text, capture screens, record audio, or perform gestures.",
            "首页开关可显示或隐藏灵动岛；锁屏、安全页面和系统状态栏不保证显示。" to "The Home switch shows or hides Dynamic Island. It may not appear on lock screens, security pages, or system UI.",
            "重置本组" to "Reset this group", "恢复本组默认值" to "Restore group defaults", "恢复本组默认参数？" to "Restore group defaults?",
            "取消" to "Cancel", "恢复" to "Restore", "已恢复本组默认值" to "Group defaults restored",
            "左侧" to "Left", "居中" to "Center", "右侧" to "Right", "水平微调" to "Horizontal offset",
            "灵动岛尺寸" to "Dynamic Island size", "玻璃球" to "Glass orb", "全屏时隐藏灵动岛" to "Hide in fullscreen",
            "横屏隐藏" to "Hide in landscape", "全屏时隐藏。" to "Hide in fullscreen.", "横屏时隐藏。" to "Hide in landscape.", "球体直径" to "Orb diameter", "玻璃球宽度" to "Orb width", "玻璃球高度" to "Orb height",
            "外部效果余量" to "Effect margin", "效果画布比例" to "Effect canvas scale", "灵动岛触控" to "Dynamic Island touch",
            "扩大灵动岛触摸区域" to "Enlarge touch area", "触摸区域倍率" to "Touch-area scale",
            "玻璃形态与光影" to "Glass form & lighting", "内部深度" to "Inner depth", "曲率" to "Curvature", "高光" to "Highlights",
            "高光亮度" to "Highlight intensity", "高光宽度" to "Highlight width", "高光收束" to "Highlight focus", "动态颜色" to "Dynamic color",
            "内部颜色" to "Inner color", "上半部高光颜色" to "Upper highlight color", "上半部高光强度" to "Upper highlight intensity",
            "阴影与焦散" to "Shadows & caustics", "阴影" to "Shadow", "焦散强度" to "Caustic intensity", "阴影偏移" to "Shadow offset",
            "焦散偏移" to "Caustic offset", "光影柔度" to "Light softness", "球体暗部" to "Orb shading", "强度" to "Intensity",
            "渐隐跨度" to "Fade range", "暗部衰减" to "Shading falloff", "下层模糊" to "Lower-layer blur", "下层模糊度" to "Lower blur amount",
            "下层变暗" to "Lower-layer dim", "变暗程度" to "Dim amount", "玻璃球模糊" to "Glass orb blur", "玻璃球模糊度" to "Glass blur amount",
            "波形与色彩" to "Waves & color", "振幅" to "Amplitude", "尺度" to "Scale", "色散" to "Dispersion", "光带" to "Light band",
            "线宽" to "Line width", "亮度" to "Brightness", "填光" to "Fill light", "填光厚度" to "Fill thickness", "柔化" to "Softness",
            "白色辉光" to "White glow", "色相偏移" to "Hue shift", "粒子外观与运动" to "Particle look & motion",
            "环半径" to "Ring radius", "点半径" to "Dot radius", "辉光" to "Glow", "转速" to "Rotation speed",
            "上滑与拖动" to "Swipe up & drag", "收起跟手距离" to "Collapse distance", "拖拽弹性范围" to "Drag range", "拖拽响应系数" to "Drag response",
            "形变与回弹" to "Deformation & rebound", "形变位移上限" to "Deformation limit", "形变幅度" to "Deformation amount",
            "形变回弹反应" to "Rebound response", "形变回弹阻尼" to "Rebound damping", "展开与收起" to "Expand & collapse",
            "展开响应" to "Expand response", "展开阻尼" to "Expand damping", "收起响应" to "Collapse response", "收起阻尼" to "Collapse damping",
            "收起回弹幅度" to "Collapse rebound", "波形渐入延迟" to "Wave fade-in delay", "呼吸与点击" to "Breathing & press",
            "呼吸幅度" to "Breathing amount", "呼吸速度" to "Breathing speed", "点击放大" to "Press scale", "思考停留" to "Thinking duration",
            "自动收起" to "Auto-collapse", "等待时间" to "Delay", "帧率与清晰度" to "Frame rate & clarity", "展开、收起与玻璃球按 120 fps 请求刷新。" to "Expand, collapse, and Glass Orb request 120 fps refresh.", "渲染比例" to "Render scale",
            "未运行" to "Not running", "正在显示" to "Visible", "已隐藏" to "Hidden", "需要权限" to "Permission required", "运行异常" to "Runtime error",
            "还原" to "Reset", "还原默认值" to "reset to default", "调节" to "adjust", "参数" to "Settings",
            "应用图标" to "App icon", "App 版本" to "App version", "获取 App" to "Get the app", "关注抖音或微信公众号“你好小机”" to "Follow Douyin or the WeChat Official Account “Hello Xiaojī”.",
            "仓库参考" to "Repository reference", "打开参考仓库" to "Open reference repository", "玻璃球视觉与动画参考开源学习项目" to "Glass-orb visuals and motion reference an open-source study project.",
        )
    }
}
