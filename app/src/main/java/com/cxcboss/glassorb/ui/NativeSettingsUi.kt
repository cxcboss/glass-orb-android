package com.cxcboss.glassorb.ui

import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.cxcboss.glassorb.BuildConfig
import com.cxcboss.glassorb.R
import com.cxcboss.glassorb.data.ConfigGroup
import com.cxcboss.glassorb.model.HorizontalAnchor
import com.cxcboss.glassorb.model.OrbConfig
import com.cxcboss.glassorb.overlay.OrbOverlayService
import com.cxcboss.glassorb.overlay.OverlayRuntime
import com.cxcboss.glassorb.overlay.OverlayRuntimeStatus
import com.cxcboss.glassorb.ui.glass.BackdropHostLayout
import com.cxcboss.glassorb.ui.glass.GlassAlert
import com.cxcboss.glassorb.ui.glass.GlassBackdrop
import com.cxcboss.glassorb.ui.glass.GlassContextMenu
import com.cxcboss.glassorb.ui.glass.GlassNavBar
import com.cxcboss.glassorb.ui.glass.GlassSearchBar
import com.cxcboss.glassorb.ui.glass.LiquidGlassView
import com.cxcboss.glassorb.ui.ios.IosPalette
import com.cxcboss.glassorb.ui.ios.IosRows
import com.cxcboss.glassorb.ui.ios.IosTint
import com.cxcboss.glassorb.ui.ios.RowHandle
import com.cxcboss.glassorb.ui.ios.RowIcon
import com.cxcboss.glassorb.ui.ios.dp
import com.cxcboss.glassorb.ui.ios.dpF
import com.cxcboss.glassorb.ui.ios.setRowEnabled
import java.util.ArrayDeque

private fun ConfigGroup.title(): String = when (this) {
    ConfigGroup.Geometry -> "位置与触控"
    ConfigGroup.Glass -> "颜色与光影"
    ConfigGroup.Container -> "背景与暗部"
    ConfigGroup.Wave -> "波形与光带"
    ConfigGroup.Dots -> "圆点与粒子"
    ConfigGroup.Motion -> "手势与动效"
    ConfigGroup.Performance -> "性能与清晰度"
}

private fun ConfigGroup.icon(): RowIcon = when (this) {
    ConfigGroup.Geometry -> RowIcon(R.drawable.ic_category_geometry, IosTint.Blue)
    ConfigGroup.Glass -> RowIcon(R.drawable.ic_category_colors, IosTint.Orange)
    ConfigGroup.Container -> RowIcon(R.drawable.ic_category_container, IosTint.Indigo)
    ConfigGroup.Wave -> RowIcon(R.drawable.ic_category_wave, IosTint.Teal)
    ConfigGroup.Dots -> RowIcon(R.drawable.ic_category_dots, IosTint.Purple)
    ConfigGroup.Motion -> RowIcon(R.drawable.ic_category_motion, IosTint.Green)
    ConfigGroup.Performance -> RowIcon(R.drawable.ic_category_performance, IosTint.Pink)
}

private fun ConfigGroup.description(): String = when (this) {
    ConfigGroup.Geometry -> "调整灵动岛与玻璃球的尺寸、位置、显示时机和触控范围。"
    ConfigGroup.Glass -> "调整玻璃的深度、曲率、高光、动态颜色、阴影与焦散。"
    ConfigGroup.Container -> "设置展开时的下层模糊、下层变暗、玻璃球模糊和球体暗部。"
    ConfigGroup.Wave -> "调整玻璃球内部波形的振幅、色散、线宽与辉光。"
    ConfigGroup.Dots -> "调整思考状态下环形圆点的大小、辉光与转速。"
    ConfigGroup.Motion -> "调整展开、收起、拖动形变、呼吸和自动收起的手感。"
    ConfigGroup.Performance -> "调整渲染清晰度。展开、收起与玻璃球始终按 120 fps 请求刷新。"
}

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
 * iOS 26 Settings, rebuilt from plain Android Views.
 *
 * Structure follows the real Settings app: a root list titled 设置 with an
 * account-style card, icon rows without subtitles and a floating Liquid Glass
 * search capsule at the bottom; second-level pages open with an icon/title/
 * description header card and an inline title that appears once it scrolls
 * away. Chrome (scroll-edge navigation bar, glass back button, search bar,
 * context menu) sits beside the [BackdropHostLayout] it refracts, never inside.
 * Status-driven rows update in place through [Page.refreshers].
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
        data object Licenses : Screen
    }

    private class Page(val screen: Screen, val scroll: ScrollView, val column: LinearLayout) {
        /** Re-applies runtime state (switch positions, status text) without rebuilding the page. */
        val refreshers = ArrayList<() -> Unit>()
        var navTitle: String = ""
        var titleFromPx = 0f
        var titleToPx = 1f
        var mainContent: LinearLayout? = null
        var searchContent: LinearLayout? = null
    }

    private data class SearchEntry(val title: String, val screen: Screen, val pageTitle: String)

    private val density = activity.resources.displayMetrics.density
    private val palette = IosPalette.of(activity)
    private val text = SettingsText(activity)
    private val rows = IosRows(activity, palette, text::tr)
    private val refreshRateController = SettingsRefreshRateController(activity)
    private val pages = ArrayDeque<Page>()
    private val host = BackdropHostLayout(activity)
    private val backdrop = GlassBackdrop(host).also { host.backdrop = it }
    private val navBar: GlassNavBar
    private val bottomEdge: LiquidGlassView
    private val searchBar: GlassSearchBar
    private val contextMenu: GlassContextMenu
    private var searchIndex: List<SearchEntry>? = null
    private var insetTop = 0
    private var insetBottom = 0
    private var insetIme = 0
    private var refreshAfterConfigFor: Screen? = null
    private var navigationAnimating = false
    private var config: OrbConfig = OrbConfig.reference()
    private var runtimeStatus: OverlayRuntimeStatus = OverlayRuntimeStatus.Stopped
    private var overlayPermission: Boolean = false
    private var accessibilityEnabled: Boolean = false
    private var autoStartEnabled: Boolean = false
    var onBackAvailabilityChanged: ((Boolean) -> Unit)? = null

    /** True while back should be handled here: a pushed page, active search or an open menu. */
    val canGoBack: Boolean get() = pages.size > 1 || searchBar.active || contextMenu.isShowing
    private var predictiveBack = false
    private var backDirection = 1f

    init {
        val state = activity.savedStateRegistry.consumeRestoredStateForKey(SAVED_STATE_KEY)
        activity.savedStateRegistry.registerSavedStateProvider(SAVED_STATE_KEY) {
            Bundle().apply {
                putString(KEY_SCREEN, screenKey(pages.peekLast()?.screen ?: Screen.Home))
                putInt(KEY_SCROLL_Y, pages.peekLast()?.scroll?.scrollY ?: 0)
            }
        }
        activity.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) = refreshRateController.dispose()
        })

        root.setBackgroundColor(palette.groupedBackground)
        host.setBackgroundColor(palette.groupedBackground)
        root.addView(host, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        navBar = GlassNavBar(activity, palette, backdrop) { goBack() }.apply {
            setBackDescription(ui("返回"))
        }
        root.addView(navBar, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, navBar.barHeightPx))
        bottomEdge = LiquidGlassView(activity, palette).apply {
            backdrop = this@NativeSettingsController.backdrop
            cornerRadiusPx = 0f
            blurDp = 10f
            lensDp = 0f
            rimEnabled = false
            shadowEnabled = false
            sheenEnabled = false
            edgeFade = LiquidGlassView.EDGE_BOTTOM
            edgeFadeLengthPx = dp(44f).toFloat()
            tintOverride = (palette.groupedBackground and 0x00FFFFFF) or (0xB3 shl 24)
        }
        root.addView(bottomEdge, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(BOTTOM_EDGE_DP), Gravity.BOTTOM,
        ))
        searchBar = GlassSearchBar(
            context = activity,
            palette = palette,
            backdrop = backdrop,
            hint = ui("搜索"),
            closeDescription = ui("取消"),
            onQueryChanged = ::renderSearch,
            onActiveChanged = ::onSearchActiveChanged,
        )
        root.addView(searchBar, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM,
        ).apply {
            leftMargin = dp(16f - GlassSearchBar.INSET_DP)
            rightMargin = dp(16f - GlassSearchBar.INSET_DP)
            bottomMargin = dp(SEARCH_MARGIN_DP)
        })
        contextMenu = GlassContextMenu(root, palette, backdrop)
        rows.sliderResetPresenter = { anchor, label, defaultText, reset ->
            contextMenu.show(anchor, label, ui("恢复默认值"), String.format(ui("默认值：%s"), defaultText), reset)
            onBackAvailabilityChanged?.invoke(canGoBack)
        }

        addInitialPage()
        restoreTopPage(state)
        syncChrome(animated = false)
    }

    // region External state

    fun applyInsets(top: Int, bottom: Int, ime: Int = 0) {
        insetTop = top
        insetBottom = bottom
        insetIme = ime
        navBar.setTopInset(top)
        (searchBar.layoutParams as? FrameLayout.LayoutParams)?.let {
            it.bottomMargin = maxOf(bottom, ime) + dp(SEARCH_MARGIN_DP)
            searchBar.layoutParams = it
        }
        (bottomEdge.layoutParams as? FrameLayout.LayoutParams)?.let {
            it.height = maxOf(bottom, ime) + dp(BOTTOM_EDGE_DP)
            bottomEdge.layoutParams = it
        }
        pages.forEach { applyPagePadding(it) }
    }

    fun updateConfig(value: OrbConfig) {
        val changed = config != value
        config = value
        // Controls own their visual state while the user drags them. Only an
        // explicit bulk change (group reset) asks the visible page to rebuild.
        val refreshScreen = refreshAfterConfigFor
        if (changed && refreshScreen != null) {
            refreshAfterConfigFor = null
            if (refreshScreen == currentScreen()) refreshCurrentPage()
        }
    }

    fun updateRuntimeStatus(value: OverlayRuntimeStatus) {
        if (runtimeStatus == value) return
        runtimeStatus = value
        runRefreshers()
    }

    fun updateOverlayPermission(value: Boolean) {
        if (overlayPermission == value) return
        overlayPermission = value
        runRefreshers()
    }

    fun updateAccessibilityEnabled(value: Boolean) {
        if (accessibilityEnabled == value) return
        accessibilityEnabled = value
        runRefreshers()
    }

    fun updateAutoStartEnabled(value: Boolean) {
        if (autoStartEnabled == value) return
        autoStartEnabled = value
        runRefreshers()
    }

    private fun runRefreshers() {
        pages.forEach { page -> page.refreshers.forEach { refresh -> refresh() } }
    }

    // endregion

    // region Navigation

    fun navigate(screen: Any) {
        val target = when (screen) {
            is ConfigGroup -> Screen.Group(screen)
            is String -> when (screen) {
                "overlay" -> Screen.Overlay
                "about" -> Screen.About
                "licenses" -> Screen.Licenses
                else -> Screen.Home
            }
            else -> Screen.Home
        }
        if (currentScreen() == target || navigationAnimating) return
        val below = pages.peekLast()
        val page = createPage(target)
        host.addView(page.scroll, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        pages.addLast(page)
        onBackAvailabilityChanged?.invoke(canGoBack)
        val width = rootWidth()
        page.scroll.translationX = width
        syncChrome(animated = true)
        navigationAnimating = true
        below?.scroll?.animate()?.translationX(-width * PARALLAX)?.setDuration(PAGE_NAVIGATION_DURATION_MS)
            ?.setInterpolator(EASE)?.start()
        page.scroll.animate()
            .translationX(0f)
            .setDuration(PAGE_NAVIGATION_DURATION_MS)
            .setInterpolator(EASE)
            .withEndAction {
                // A covered page costs nothing to draw or to capture for glass.
                below?.scroll?.visibility = View.INVISIBLE
                navigationAnimating = false
            }
            .start()
    }

    fun goBack(): Boolean {
        if (contextMenu.isShowing) {
            contextMenu.dismiss()
            onBackAvailabilityChanged?.invoke(canGoBack)
            return true
        }
        if (searchBar.active) {
            searchBar.deactivate()
            return true
        }
        if (pages.size <= 1) {
            onRootBack?.invoke()
            return onRootBack != null
        }
        // The previous page stays mounted, so scroll position and slider state
        // survive the round trip.
        if (navigationAnimating) return true
        val leaving = pages.peekLast() ?: return false
        val previous = pages.toList().getOrNull(pages.size - 2)
        val width = rootWidth()
        val distance = width * if (predictiveBack) backDirection else 1f
        predictiveBack = false
        navigationAnimating = true
        previous?.scroll?.let { scroll ->
            scroll.visibility = View.VISIBLE
            scroll.animate().translationX(0f).setDuration(PAGE_NAVIGATION_DURATION_MS).setInterpolator(EASE).start()
        }
        // Chrome follows the page that is becoming visible immediately.
        pages.removeLast()
        syncChrome(animated = true)
        pages.addLast(leaving)
        leaving.scroll.animate()
            .translationX(distance)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(PAGE_NAVIGATION_DURATION_MS)
            .setInterpolator(EASE)
            .withEndAction {
                if (pages.peekLast() === leaving) {
                    pages.removeLast()
                    host.removeView(leaving.scroll)
                    onBackAvailabilityChanged?.invoke(canGoBack)
                }
                navigationAnimating = false
                syncChrome(animated = false)
            }
            .start()
        return true
    }

    fun startPredictiveBack(event: BackEventCompat) {
        if (pages.size <= 1 || navigationAnimating || searchBar.active || contextMenu.isShowing) return
        predictiveBack = true
        backDirection = if (event.swipeEdge == BackEventCompat.EDGE_LEFT) 1f else -1f
    }

    fun progressPredictiveBack(event: BackEventCompat) {
        if (!predictiveBack) return
        val page = pages.peekLast()?.scroll ?: return
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
        pages.peekLast()?.scroll?.animate()?.translationX(0f)?.scaleX(1f)?.scaleY(1f)
            ?.setDuration(180L)?.withEndAction { navigationAnimating = false }?.start()
    }

    private fun rootWidth(): Float = (root.width.takeIf { it > 0 } ?: activity.resources.displayMetrics.widthPixels).toFloat()

    private fun addInitialPage() {
        val initial = decodeScreen(initialScreenKey) ?: Screen.Home
        val page = createPage(initial)
        host.addView(page.scroll, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        pages.addLast(page)
    }

    private fun restoreTopPage(state: Bundle?) {
        val restored = decodeScreen(state?.getString(KEY_SCREEN)) ?: return
        val scrollY = state?.getInt(KEY_SCROLL_Y, 0) ?: 0
        if (restored == currentScreen()) {
            pages.peekLast()?.scroll?.let { scroll -> scroll.post { scroll.scrollTo(0, scrollY) } }
            return
        }
        if (currentScreen() != Screen.Home) return
        val below = pages.peekLast()
        val page = createPage(restored)
        host.addView(page.scroll, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        pages.addLast(page)
        below?.scroll?.visibility = View.INVISIBLE
        page.scroll.post { page.scroll.scrollTo(0, scrollY) }
        onBackAvailabilityChanged?.invoke(canGoBack)
    }

    private fun refreshCurrentPage() {
        if (predictiveBack) return
        val current = pages.pollLast() ?: return
        val scrollY = current.scroll.scrollY
        current.scroll.animate().cancel()
        navigationAnimating = false
        val replacement = createPage(current.screen)
        host.removeView(current.scroll)
        host.addView(replacement.scroll, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        pages.addLast(replacement)
        replacement.scroll.post { replacement.scroll.scrollTo(0, scrollY) }
        syncChrome(animated = false)
    }

    private fun currentScreen(): Screen = pages.peekLast()?.screen ?: Screen.Home

    private fun requestCurrentPageRefresh() {
        refreshAfterConfigFor = currentScreen()
    }

    /** Points the persistent chrome at whichever page is on top. */
    private fun syncChrome(animated: Boolean) {
        val top = pages.peekLast() ?: return
        navBar.setTitle(top.navTitle)
        navBar.setBackVisible(pages.size > 1)
        syncNavScroll(top)
        val onRoot = top.screen == Screen.Home
        if (!onRoot) searchBar.deactivate()
        listOf<View>(searchBar, bottomEdge).forEach { view ->
            view.isEnabled = onRoot
            val offset = if (onRoot) 0f else dp(36f).toFloat()
            if (animated) {
                view.visibility = View.VISIBLE
                view.animate().alpha(if (onRoot) 1f else 0f).translationY(offset)
                    .setDuration(PAGE_NAVIGATION_DURATION_MS).setInterpolator(EASE)
                    .withEndAction { if (pages.peekLast()?.screen != Screen.Home) view.visibility = View.INVISIBLE }
                    .start()
            } else {
                view.animate().cancel()
                view.alpha = if (onRoot) 1f else 0f
                view.translationY = offset
                view.visibility = if (onRoot) View.VISIBLE else View.INVISIBLE
            }
        }
    }

    private fun syncNavScroll(page: Page) {
        val y = page.scroll.scrollY.toFloat()
        val titleProgress = smoothStep(page.titleFromPx, page.titleToPx, y)
        val edgeProgress = (y / dp(8f)).coerceIn(0f, 1f)
        navBar.setScrollState(titleProgress, edgeProgress)
    }

    private fun smoothStep(from: Float, to: Float, value: Float): Float {
        val t = ((value - from) / (to - from)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    // endregion

    // region Pages

    private fun createPage(screen: Screen): Page {
        val scroll = ScrollView(activity).apply {
            tag = "settings-scroll"
            isVerticalScrollBarEnabled = false
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        }
        val column = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(column, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val page = Page(screen, scroll, column)
        page.navTitle = ui(screenTitle(screen))
        applyPagePadding(page)
        refreshRateController.attach(scroll) {
            if (pages.peekLast() === page) syncNavScroll(page)
        }

        when (screen) {
            Screen.Home -> {
                // Root: large title that collapses into the bar, like Settings.
                column.addView(rows.largeTitle(screenTitle(screen)), matchWrap())
                page.titleFromPx = dp(16f).toFloat()
                page.titleToPx = dp(40f).toFloat()
                buildRoot(page)
            }
            else -> {
                // Second level: header card, inline title once it scrolls away.
                page.titleFromPx = dp(84f).toFloat()
                page.titleToPx = dp(124f).toFloat()
                when (screen) {
                    Screen.Overlay -> buildOverlayDetails(page)
                    is Screen.Group -> buildGroupPage(page, screen.group)
                    Screen.About -> buildAbout(page)
                    Screen.Licenses -> buildLicenses(page)
                    Screen.Home -> Unit
                }
            }
        }
        column.addView(View(activity), LinearLayout.LayoutParams(1, dp(28f)))
        return page
    }

    private fun applyPagePadding(page: Page) {
        val bottom = maxOf(insetBottom, insetIme) + if (page.screen == Screen.Home) dp(96f) else dp(28f)
        page.scroll.setPadding(0, navBar.barHeightPx, 0, bottom)
    }

    private fun buildRoot(page: Page) {
        val main = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val search = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        page.mainContent = main
        page.searchContent = search
        page.column.addView(main, matchWrap())
        page.column.addView(search, matchWrap())

        rows.profileCard(main, R.mipmap.ic_launcher, activity.getString(R.string.app_name), "版本、来源与开源许可") { navigate("about") }
        main.section(null) {
            val master = switchRow("显示灵动岛", null, runtimeStatus == OverlayRuntimeStatus.Visible, RowIcon(R.drawable.ic_orb, IosTint.Blue)) { enabled ->
                val action = resolveOverlayAction(enabled, overlayPermission, runtimeStatus)
                when (action) {
                    OverlayAction.RequestPermission -> callbacks.requestOverlayPermission()
                    OverlayAction.Show -> callbacks.showOverlay()
                    OverlayAction.Start -> callbacks.startOverlay()
                    OverlayAction.Hide -> callbacks.hideOverlay()
                    OverlayAction.None -> Unit
                }
                // The switch mirrors real service state: snap back when the
                // request could not complete (permission prompt, failed start).
                val delay = if (action == OverlayAction.RequestPermission) 0L else 900L
                activity.window.decorView.postDelayed({ runRefreshers() }, delay)
            }
            page.refreshers += { master.setChecked(runtimeStatus == OverlayRuntimeStatus.Visible) }
            val runtime = navRow(
                "运行与权限",
                value = nativeStatusLabel(runtimeStatus),
                icon = RowIcon(R.drawable.ic_category_accessibility, IosTint.Gray),
            ) { navigate("overlay") }
            page.refreshers += { runtime.setValue(ui(nativeStatusLabel(runtimeStatus))) }
        }
        main.section(null) {
            groupRow(ConfigGroup.Geometry)
            groupRow(ConfigGroup.Motion)
        }
        main.section(null) {
            groupRow(ConfigGroup.Glass)
            groupRow(ConfigGroup.Container)
            groupRow(ConfigGroup.Wave)
            groupRow(ConfigGroup.Dots)
        }
        main.section(null) {
            groupRow(ConfigGroup.Performance)
        }
        main.section(null) {
            navRow("关于", icon = RowIcon(R.drawable.ic_category_about, IosTint.Gray)) { navigate("about") }
        }
    }

    private fun IosRows.GroupScope.groupRow(group: ConfigGroup) {
        navRow(group.title(), icon = group.icon()) { navigate(group) }
    }

    // region Search

    private fun onSearchActiveChanged(active: Boolean) {
        val page = pages.firstOrNull { it.screen == Screen.Home } ?: return
        page.mainContent?.visibility = if (active) View.GONE else View.VISIBLE
        page.searchContent?.visibility = if (active) View.VISIBLE else View.GONE
        if (active) renderSearch("")
        page.scroll.scrollTo(0, 0)
        onBackAvailabilityChanged?.invoke(canGoBack)
    }

    private fun renderSearch(query: String) {
        val page = pages.firstOrNull { it.screen == Screen.Home } ?: return
        val container = page.searchContent ?: return
        if (!searchBar.active) return
        container.removeAllViews()
        val needle = query.trim()
        if (needle.isEmpty()) {
            container.addView(rows.sectionFooter("输入设置名称，例如“高光”或“模糊”。"), matchWrap())
            return
        }
        val index = searchIndex ?: buildSearchIndex().also { searchIndex = it }
        val matches = index.filter {
            ui(it.title).contains(needle, ignoreCase = true) || it.title.contains(needle, ignoreCase = true)
        }.take(MAX_SEARCH_RESULTS)
        if (matches.isEmpty()) {
            container.addView(rows.sectionFooter(String.format(ui("没有与“%s”相关的设置。"), needle)), matchWrap())
            return
        }
        container.section(null) {
            matches.forEach { entry ->
                val icon = (entry.screen as? Screen.Group)?.group?.icon()
                navRow(entry.title, subtitle = entry.pageTitle.takeIf { it != entry.title }, icon = icon) {
                    openSearchResult(entry)
                }
            }
        }
    }

    /** Builds every second-level page off screen once and records its row titles. */
    private fun buildSearchIndex(): List<SearchEntry> {
        val screens = ConfigGroup.entries.map { Screen.Group(it) } + listOf(Screen.Overlay, Screen.About)
        val entries = ArrayList<SearchEntry>()
        screens.forEach { screen ->
            val titles = ArrayList<String>()
            rows.titleSink = { titles += it }
            try {
                createPage(screen)
            } finally {
                rows.titleSink = null
            }
            val pageTitle = screenTitle(screen)
            entries += SearchEntry(pageTitle, screen, pageTitle)
            titles.distinct().filter { it != pageTitle }.forEach { entries += SearchEntry(it, screen, pageTitle) }
        }
        return entries
    }

    private fun openSearchResult(entry: SearchEntry) {
        searchBar.deactivate()
        val screenKey = when (val screen = entry.screen) {
            is Screen.Group -> screen.group
            Screen.Overlay -> "overlay"
            Screen.About -> "about"
            else -> return
        }
        navigate(screenKey)
        val page = pages.peekLast() ?: return
        val label = ui(entry.title)
        page.scroll.postDelayed({
            val target = findText(page.column, label) ?: return@postDelayed
            var top = 0
            var view: View? = target
            while (view != null && view !== page.column) {
                top += view.top
                view = view.parent as? View
            }
            page.scroll.smoothScrollTo(0, (top - dp(140f)).coerceAtLeast(0))
        }, PAGE_NAVIGATION_DURATION_MS)
    }

    private fun findText(view: View, label: String): TextView? {
        if (view is TextView && view.text?.toString() == label) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) findText(view.getChildAt(index), label)?.let { return it }
        }
        return null
    }

    // endregion

    private fun buildOverlayDetails(page: Page) {
        val content = page.column
        rows.headerCard(content, RowIcon(R.drawable.ic_category_accessibility, IosTint.Gray), "运行与权限", "管理悬浮窗权限、无障碍触控服务与开机自启。")
        content.section("状态") {
            val status = valueRow("运行状态", nativeStatusLabel(runtimeStatus))
            page.refreshers += {
                status.setValue(ui(nativeStatusLabel(runtimeStatus)))
                val error = (runtimeStatus as? OverlayRuntimeStatus.Error)?.message
                status.setSubtitle(error)
                status.title?.setTextColor(if (error != null) palette.red else palette.label)
            }
            val permission = navRow("悬浮窗权限", value = if (overlayPermission) "已允许" else "未允许") {
                callbacks.requestOverlayPermission()
            }
            page.refreshers += { permission.setValue(ui(if (overlayPermission) "已允许" else "未允许")) }
        }
        content.section(
            "状态栏触控",
            footer = "仅用于状态栏触控和识别已知助手窗口；不读取文字、截图、录音或执行手势。",
        ) {
            val accessibility = navRow(
                "无障碍触控服务",
                value = if (accessibilityEnabled) "已开启" else "未开启",
            ) { callbacks.requestAccessibilityPermission() }
            page.refreshers += { accessibility.setValue(ui(if (accessibilityEnabled) "已开启" else "未开启")) }
        }
        content.section(
            "启动",
            footer = "开机后自动显示灵动岛。需要悬浮窗权限；Android 12 及以上还需要开启无障碍触控服务。",
        ) {
            val autoStart = switchRow("开机自动启动", null, autoStartEnabled) { enabled ->
                autoStartEnabled = enabled
                callbacks.setAutoStartEnabled(enabled)
            }
            page.refreshers += { autoStart.setChecked(autoStartEnabled) }
        }
        content.addView(rows.sectionFooter("首页开关可显示或隐藏灵动岛；锁屏、安全页面和系统状态栏不保证显示。"), matchWrap())
        page.refreshers.forEach { it() }
    }

    private fun buildGroupPage(page: Page, group: ConfigGroup) {
        val content = page.column
        rows.headerCard(content, group.icon(), group.title(), group.description())
        when (group) {
            ConfigGroup.Geometry -> buildGeometry(content)
            ConfigGroup.Glass -> buildGlass(content)
            ConfigGroup.Container -> buildContainer(content)
            ConfigGroup.Wave -> buildWave(content)
            ConfigGroup.Dots -> buildDots(content)
            ConfigGroup.Motion -> buildMotion(content)
            ConfigGroup.Performance -> buildPerformance(content)
        }
        content.section("重置本组") {
            actionRow("恢复本组默认值", destructive = true) { confirmResetGroup(group) }
        }
    }

    private fun buildGeometry(content: LinearLayout) {
        content.section("位置") {
            segmentedRow(
                "水平锚点",
                listOf("左侧", "居中", "右侧"),
                when (config.geometry.horizontalAnchor) {
                    HorizontalAnchor.Left -> 0
                    HorizontalAnchor.Center -> 1
                    HorizontalAnchor.Right -> 2
                },
            ) { index ->
                val anchor = when (index) {
                    0 -> HorizontalAnchor.Left
                    2 -> HorizontalAnchor.Right
                    else -> HorizontalAnchor.Center
                }
                updateConfig { it.copy(geometry = it.geometry.copy(horizontalAnchor = anchor)) }
            }
            val verticalDensity = density.coerceAtLeast(0.1f)
            sliderRow(
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
            sliderRow("水平微调", config.geometry.horizontalOffsetDp, OrbConfig.reference().geometry.horizontalOffsetDp, -200f..200f, "dp") { value ->
                updateConfig { current -> current.copy(geometry = current.geometry.copy(horizontalOffsetDp = value)) }
            }
        }
        content.section("灵动岛尺寸") {
            sliderRow("灵动岛宽度", config.geometry.capsuleWidthDp, OrbConfig.reference().geometry.capsuleWidthDp, 0f..220f, "dp") { value ->
                updateConfig { current -> current.copy(geometry = current.geometry.copy(capsuleWidthDp = value)) }
            }
            sliderRow("灵动岛高度", config.geometry.capsuleHeightDp, OrbConfig.reference().geometry.capsuleHeightDp, 0f..64f, "dp") { value ->
                updateConfig { current -> current.copy(geometry = current.geometry.copy(capsuleHeightDp = value)) }
            }
        }
        content.section("玻璃球") {
            switchRow("向下展开", null, config.geometry.expandBelowCapsule) { enabled ->
                updateConfig { it.copy(geometry = it.geometry.copy(expandBelowCapsule = enabled)) }
            }
            switchRow("全屏时隐藏灵动岛", "全屏时隐藏。", config.geometry.hideWhenStatusBarHidden) { enabled ->
                updateConfig { it.copy(geometry = it.geometry.copy(hideWhenStatusBarHidden = enabled)) }
            }
            switchRow("横屏隐藏", "横屏时隐藏。", config.geometry.hideWhenLandscape) { enabled ->
                updateConfig { it.copy(geometry = it.geometry.copy(hideWhenLandscape = enabled)) }
            }
            sliderRow("球体直径", config.geometry.orbDiameterDp, OrbConfig.reference().geometry.orbDiameterDp, 88f..220f, "dp") { value ->
                updateConfig { current ->
                    current.copy(geometry = current.geometry.copy(orbDiameterDp = value, orbWidthDp = value, orbHeightDp = value))
                }
            }
            sliderRow("玻璃球宽度", config.geometry.orbWidthDp, OrbConfig.reference().geometry.orbWidthDp, 88f..260f, "dp") { value ->
                updateConfig { current -> current.copy(geometry = current.geometry.copy(orbWidthDp = value)) }
            }
            sliderRow("玻璃球高度", config.geometry.orbHeightDp, OrbConfig.reference().geometry.orbHeightDp, 88f..260f, "dp") { value ->
                updateConfig { current -> current.copy(geometry = current.geometry.copy(orbHeightDp = value)) }
            }
            sliderRow("外部效果余量", config.geometry.outerMarginDp, OrbConfig.reference().geometry.outerMarginDp, 8f..48f, "dp") { value ->
                updateConfig { current -> current.copy(geometry = current.geometry.copy(outerMarginDp = value)) }
            }
            sliderRow("效果画布比例", config.geometry.effectScale, OrbConfig.reference().geometry.effectScale, 0.9f..1.5f, decimals = 2) { value ->
                updateConfig { current -> current.copy(geometry = current.geometry.copy(effectScale = value)) }
            }
        }
        content.section("灵动岛触控") {
            lateinit var touchScale: RowHandle
            switchRow("扩大灵动岛触摸区域", null, config.geometry.enlargedTouchArea) { enabled ->
                touchScale.row.setRowEnabled(enabled)
                updateConfig { current -> current.copy(geometry = current.geometry.copy(enlargedTouchArea = enabled)) }
            }
            touchScale = sliderRow(
                label = "触摸区域倍率",
                value = config.geometry.touchAreaScale,
                defaultValue = OrbConfig.reference().geometry.touchAreaScale,
                range = 1f..3f,
                suffix = "×",
                decimals = 2,
                // Show the red touch outline on the live overlay while dragging.
                onTrackingChanged = { tracking ->
                    if (OverlayRuntime.status.value == OverlayRuntimeStatus.Visible) {
                        OrbOverlayService.setTouchPreview(activity, tracking)
                    }
                },
            ) { value -> updateConfig { current -> current.copy(geometry = current.geometry.copy(touchAreaScale = value)) } }
            touchScale.row.setRowEnabled(config.geometry.enlargedTouchArea)
        }
    }

    private fun buildContainer(content: LinearLayout) {
        content.section("背景效果") { addBackgroundEffectControls() }
        content.section("球体暗部") {
            sliderRow("强度", config.container.strength, OrbConfig.reference().container.strength, 0f..4f, decimals = 2) { value ->
                updateConfig { current -> current.copy(container = current.container.copy(strength = value)) }
            }
            sliderRow("渐隐跨度", config.container.fade, OrbConfig.reference().container.fade, 0f..2f, decimals = 2) { value ->
                updateConfig { current -> current.copy(container = current.container.copy(fade = value)) }
            }
            sliderRow("暗部衰减", config.container.gaussian, OrbConfig.reference().container.gaussian, 0.5f..16f, decimals = 1) { value ->
                updateConfig { current -> current.copy(container = current.container.copy(gaussian = value)) }
            }
        }
    }

    /**
     * The three background treatments, shared by the Common tab and the
     * Container page. Lower-layer blur and glass-orb blur are mutually
     * exclusive; the row that cannot be used is dimmed instead of rebuilding.
     */
    private fun IosRows.GroupScope.addBackgroundEffectControls() {
        lateinit var lowerBlur: RowHandle
        lateinit var lowerBlurAmount: RowHandle
        lateinit var dimAmount: RowHandle
        lateinit var glassBlur: RowHandle
        lateinit var glassBlurAmount: RowHandle

        lowerBlur = switchRow("下层模糊", null, config.container.backgroundAmbientBlurEnabled) { enabled ->
            glassBlur.row.setRowEnabled(!enabled)
            lowerBlurAmount.row.setRowEnabled(enabled)
            glassBlurAmount.row.setRowEnabled(!enabled && config.container.backgroundBlurEnabled)
            updateConfig { current ->
                current.copy(
                    container = current.container.copy(
                        backgroundAmbientBlurEnabled = enabled,
                        backgroundBlurEnabled = if (enabled) false else current.container.backgroundBlurEnabled,
                    ),
                )
            }
        }
        lowerBlurAmount = sliderRow(
            "下层模糊度",
            config.container.backgroundAmbientBlurPercent,
            OrbConfig.reference().container.backgroundAmbientBlurPercent,
            0f..100f,
            "%",
            decimals = 0,
        ) { value ->
            updateConfig { current -> current.copy(container = current.container.copy(backgroundAmbientBlurPercent = value)) }
        }
        switchRow("下层变暗", null, config.container.backgroundDimEnabled) { enabled ->
            dimAmount.row.setRowEnabled(enabled)
            updateConfig { current -> current.copy(container = current.container.copy(backgroundDimEnabled = enabled)) }
        }
        dimAmount = sliderRow(
            "变暗程度",
            config.container.backgroundDimPercent,
            OrbConfig.reference().container.backgroundDimPercent,
            0f..60f,
            "%",
            decimals = 0,
        ) { value ->
            updateConfig { current -> current.copy(container = current.container.copy(backgroundDimPercent = value)) }
        }
        glassBlur = switchRow("玻璃球模糊", null, config.container.backgroundBlurEnabled) { enabled ->
            lowerBlur.row.setRowEnabled(!enabled)
            lowerBlurAmount.row.setRowEnabled(!enabled && config.container.backgroundAmbientBlurEnabled)
            glassBlurAmount.row.setRowEnabled(enabled)
            updateConfig { current ->
                current.copy(
                    container = current.container.copy(
                        backgroundBlurEnabled = enabled,
                        backgroundAmbientBlurEnabled = if (enabled) false else current.container.backgroundAmbientBlurEnabled,
                    ),
                )
            }
        }
        glassBlurAmount = sliderRow(
            "玻璃球模糊度",
            config.container.backgroundBlurPercent,
            OrbConfig.reference().container.backgroundBlurPercent,
            0f..100f,
            "%",
            decimals = 0,
        ) { value ->
            updateConfig { current -> current.copy(container = current.container.copy(backgroundBlurPercent = value)) }
        }

        lowerBlur.row.setRowEnabled(!config.container.backgroundBlurEnabled)
        lowerBlurAmount.row.setRowEnabled(config.container.backgroundAmbientBlurEnabled)
        dimAmount.row.setRowEnabled(config.container.backgroundDimEnabled)
        glassBlur.row.setRowEnabled(!config.container.backgroundAmbientBlurEnabled)
        glassBlurAmount.row.setRowEnabled(config.container.backgroundBlurEnabled)
    }

    private fun buildGlass(content: LinearLayout) {
        content.section("玻璃形态与光影") {
            sliderRow("内部深度", config.glass.internalDepth, OrbConfig.reference().glass.internalDepth, 0f..40f) { value ->
                updateConfig { current -> current.copy(glass = current.glass.copy(internalDepth = value)) }
            }
            sliderRow("曲率", config.glass.curvature, OrbConfig.reference().glass.curvature, 0f..1f, decimals = 2) { value ->
                updateConfig { current -> current.copy(glass = current.glass.copy(curvature = value)) }
            }
        }
        content.section("高光") {
            sliderRow("高光亮度", config.glass.highlightAmount, OrbConfig.reference().glass.highlightAmount, 0f..2f, decimals = 2) { value ->
                updateConfig { current -> current.copy(glass = current.glass.copy(highlightAmount = value)) }
            }
            sliderRow("高光宽度", config.glass.highlightWidth, OrbConfig.reference().glass.highlightWidth, 0.2f..8f, decimals = 2) { value ->
                updateConfig { current -> current.copy(glass = current.glass.copy(highlightWidth = value)) }
            }
            sliderRow("高光收束", config.glass.highlightCut, OrbConfig.reference().glass.highlightCut, 0f..1f, decimals = 2) { value ->
                updateConfig { current -> current.copy(glass = current.glass.copy(highlightCut = value)) }
            }
        }
        content.section("动态颜色") {
            sliderRow(
                "内部颜色",
                config.glass.internalHueDegrees,
                OrbConfig.reference().glass.internalHueDegrees,
                0f..360f,
                "°",
                decimals = 0,
            ) { value ->
                updateConfig { current -> current.copy(glass = current.glass.copy(internalHueDegrees = value)) }
            }
            sliderRow(
                "上半部高光颜色",
                config.glass.upperHighlightHueDegrees,
                OrbConfig.reference().glass.upperHighlightHueDegrees,
                0f..360f,
                "°",
                decimals = 0,
            ) { value ->
                updateConfig { current -> current.copy(glass = current.glass.copy(upperHighlightHueDegrees = value)) }
            }
            sliderRow(
                "上半部高光强度",
                config.glass.upperHighlightAmount,
                OrbConfig.reference().glass.upperHighlightAmount,
                0f..2f,
                decimals = 2,
            ) { value ->
                updateConfig { current -> current.copy(glass = current.glass.copy(upperHighlightAmount = value)) }
            }
        }
        content.section("阴影与焦散") {
            sliderRow("阴影", config.glass.shadowAmount, OrbConfig.reference().glass.shadowAmount, 0f..1.5f, decimals = 2) { value ->
                updateConfig { current -> current.copy(glass = current.glass.copy(shadowAmount = value)) }
            }
            sliderRow("焦散强度", config.glass.causticAmount, OrbConfig.reference().glass.causticAmount, 0f..3f, decimals = 2) { value ->
                updateConfig { current -> current.copy(glass = current.glass.copy(causticAmount = value)) }
            }
            sliderRow("阴影偏移", config.glass.shadowOffset, OrbConfig.reference().glass.shadowOffset, -3f..3f, decimals = 2) { value ->
                updateConfig { current -> current.copy(glass = current.glass.copy(shadowOffset = value)) }
            }
            sliderRow("焦散偏移", config.glass.causticOffset, OrbConfig.reference().glass.causticOffset, -4f..4f, decimals = 2) { value ->
                updateConfig { current -> current.copy(glass = current.glass.copy(causticOffset = value)) }
            }
            sliderRow("光影柔度", config.glass.lightSoftness, OrbConfig.reference().glass.lightSoftness, 0.5f..10f, decimals = 2) { value ->
                updateConfig { current -> current.copy(glass = current.glass.copy(lightSoftness = value)) }
            }
        }
    }

    private fun buildWave(content: LinearLayout) {
        content.section("波形与色彩") {
            sliderRow("振幅", config.wave.amplitude, OrbConfig.reference().wave.amplitude, 0f..0.6f, decimals = 3) { value ->
                updateConfig { current -> current.copy(wave = current.wave.copy(amplitude = value)) }
            }
            sliderRow("尺度", config.wave.scale, OrbConfig.reference().wave.scale, 0.4f..1.6f, decimals = 2) { value ->
                updateConfig { current -> current.copy(wave = current.wave.copy(scale = value)) }
            }
            sliderRow("色散", config.wave.chromaticAberration, OrbConfig.reference().wave.chromaticAberration, 0f..8f, decimals = 2) { value ->
                updateConfig { current -> current.copy(wave = current.wave.copy(chromaticAberration = value)) }
            }
        }
        content.section("光带") {
            sliderRow("线宽", config.wave.lineWidth, OrbConfig.reference().wave.lineWidth, 0.5f..8f, decimals = 2) { value ->
                updateConfig { current -> current.copy(wave = current.wave.copy(lineWidth = value)) }
            }
            sliderRow("亮度", config.wave.intensity, OrbConfig.reference().wave.intensity, 0f..5f, decimals = 2) { value ->
                updateConfig { current -> current.copy(wave = current.wave.copy(intensity = value)) }
            }
            sliderRow("填光", config.wave.bandFill, OrbConfig.reference().wave.bandFill, 0f..60_000f, decimals = 0) { value ->
                updateConfig { current -> current.copy(wave = current.wave.copy(bandFill = value)) }
            }
            sliderRow("填光厚度", config.wave.bandFillThickness, OrbConfig.reference().wave.bandFillThickness, 0f..0.3f, decimals = 3) { value ->
                updateConfig { current -> current.copy(wave = current.wave.copy(bandFillThickness = value)) }
            }
            sliderRow("柔化", config.wave.softness, OrbConfig.reference().wave.softness, 0.2f..8f, decimals = 2) { value ->
                updateConfig { current -> current.copy(wave = current.wave.copy(softness = value)) }
            }
            sliderRow("白色辉光", config.wave.whiteBloom, OrbConfig.reference().wave.whiteBloom, 0f..3f, decimals = 2) { value ->
                updateConfig { current -> current.copy(wave = current.wave.copy(whiteBloom = value)) }
            }
            sliderRow("色相偏移", config.wave.hueShiftDegrees, OrbConfig.reference().wave.hueShiftDegrees, -180f..180f, "°", 0) { value ->
                updateConfig { current -> current.copy(wave = current.wave.copy(hueShiftDegrees = value)) }
            }
        }
    }

    private fun buildDots(content: LinearLayout) {
        content.section("粒子外观与运动") {
            sliderRow("环半径", config.dots.ringRadius, OrbConfig.reference().dots.ringRadius, 0.15f..0.75f, decimals = 3) { value ->
                updateConfig { current -> current.copy(dots = current.dots.copy(ringRadius = value)) }
            }
            sliderRow("点半径", config.dots.dotRadius, OrbConfig.reference().dots.dotRadius, 0.025f..0.22f, decimals = 3) { value ->
                updateConfig { current -> current.copy(dots = current.dots.copy(dotRadius = value)) }
            }
            sliderRow("辉光", config.dots.glow, OrbConfig.reference().dots.glow, 0f..0.2f, decimals = 3) { value ->
                updateConfig { current -> current.copy(dots = current.dots.copy(glow = value)) }
            }
            sliderRow("转速", config.dots.rotationSpeed, OrbConfig.reference().dots.rotationSpeed, -3f..3f, decimals = 2) { value ->
                updateConfig { current -> current.copy(dots = current.dots.copy(rotationSpeed = value)) }
            }
        }
    }

    private fun buildMotion(content: LinearLayout) {
        content.section("上滑与拖动") {
            sliderRow("收起跟手距离", config.motion.collapseRangeDp, OrbConfig.reference().motion.collapseRangeDp, 24f..120f, "dp") { value ->
                updateConfig { current -> current.copy(motion = current.motion.copy(collapseRangeDp = value)) }
            }
            sliderRow("拖拽弹性范围", config.motion.dragRangeDp, OrbConfig.reference().motion.dragRangeDp, 16f..160f, "dp") { value ->
                updateConfig { current -> current.copy(motion = current.motion.copy(dragRangeDp = value)) }
            }
            sliderRow("拖拽响应系数", config.motion.dragResistance, OrbConfig.reference().motion.dragResistance, 0.05f..2f, decimals = 2) { value ->
                updateConfig { current -> current.copy(motion = current.motion.copy(dragResistance = value)) }
            }
        }
        content.section("形变与回弹") {
            sliderRow("形变位移上限", config.motion.deformLimitDp, OrbConfig.reference().motion.deformLimitDp, 0f..8f, "dp") { value ->
                updateConfig { current -> current.copy(motion = current.motion.copy(deformLimitDp = value)) }
            }
            sliderRow("形变幅度", config.motion.deformScaleDelta, OrbConfig.reference().motion.deformScaleDelta, 0f..0.02f, decimals = 3) { value ->
                updateConfig { current -> current.copy(motion = current.motion.copy(deformScaleDelta = value)) }
            }
            sliderRow("形变回弹反应", config.motion.deformResponse, OrbConfig.reference().motion.deformResponse, 0.08f..1.5f, "s", 2) { value ->
                updateConfig { current -> current.copy(motion = current.motion.copy(deformResponse = value)) }
            }
            sliderRow("形变回弹阻尼", config.motion.deformDamping, OrbConfig.reference().motion.deformDamping, 0.1f..1.5f, decimals = 2) { value ->
                updateConfig { current -> current.copy(motion = current.motion.copy(deformDamping = value)) }
            }
        }
        content.section("展开与收起") {
            sliderRow("展开响应", config.motion.openResponse, OrbConfig.reference().motion.openResponse, 0.12f..1.2f, "s", 2) { value ->
                updateConfig { current -> current.copy(motion = current.motion.copy(openResponse = value)) }
            }
            sliderRow("展开阻尼", config.motion.openDamping, OrbConfig.reference().motion.openDamping, 0.2f..1.5f, decimals = 2) { value ->
                updateConfig { current -> current.copy(motion = current.motion.copy(openDamping = value)) }
            }
            sliderRow("收起响应", config.motion.closeResponse, OrbConfig.reference().motion.closeResponse, 0.12f..1.2f, "s", 2) { value ->
                updateConfig { current -> current.copy(motion = current.motion.copy(closeResponse = value)) }
            }
            sliderRow("收起阻尼", config.motion.closeDamping, OrbConfig.reference().motion.closeDamping, 0.2f..1.5f, decimals = 2) { value ->
                updateConfig { current -> current.copy(motion = current.motion.copy(closeDamping = value)) }
            }
            sliderRow("收起回弹幅度", config.motion.closeBounce, OrbConfig.reference().motion.closeBounce, 0f..0.12f, decimals = 3) { value ->
                updateConfig { current -> current.copy(motion = current.motion.copy(closeBounce = value)) }
            }
            sliderRow("波形渐入延迟", config.motion.waveFadeDelayMs.toFloat(), OrbConfig.reference().motion.waveFadeDelayMs.toFloat(), 0f..500f, "ms", 0) { value ->
                updateConfig { current -> current.copy(motion = current.motion.copy(waveFadeDelayMs = value.toInt())) }
            }
        }
        content.section("呼吸与点击") {
            sliderRow("呼吸幅度", config.motion.breathingAmplitude, OrbConfig.reference().motion.breathingAmplitude, 0f..0.08f, decimals = 3) { value ->
                updateConfig { current -> current.copy(motion = current.motion.copy(breathingAmplitude = value)) }
            }
            sliderRow("呼吸速度", config.motion.breathingSpeed, OrbConfig.reference().motion.breathingSpeed, 0f..4f, decimals = 2) { value ->
                updateConfig { current -> current.copy(motion = current.motion.copy(breathingSpeed = value)) }
            }
            sliderRow("点击放大", config.motion.pressScale, OrbConfig.reference().motion.pressScale, 1f..1.08f, decimals = 3) { value ->
                updateConfig { current -> current.copy(motion = current.motion.copy(pressScale = value)) }
            }
            sliderRow("思考停留", config.motion.thinkingDurationMs.toFloat(), OrbConfig.reference().motion.thinkingDurationMs.toFloat(), 300f..5_000f, "ms", 0) { value ->
                updateConfig { current -> current.copy(motion = current.motion.copy(thinkingDurationMs = value.toInt())) }
            }
        }
        content.section("自动收起") {
            lateinit var countdown: RowHandle
            switchRow("自动收起", null, config.motion.autoCollapseEnabled) { enabled ->
                countdown.row.setRowEnabled(enabled)
                updateConfig { it.copy(motion = it.motion.copy(autoCollapseEnabled = enabled)) }
            }
            countdown = sliderRow("等待时间", config.motion.autoCollapseSeconds, OrbConfig.reference().motion.autoCollapseSeconds, 1f..60f, "s", 1) { value ->
                updateConfig { it.copy(motion = it.motion.copy(autoCollapseSeconds = value)) }
            }
            countdown.row.setRowEnabled(config.motion.autoCollapseEnabled)
        }
    }

    private fun buildPerformance(content: LinearLayout) {
        content.section("帧率与清晰度", footer = "展开、收起与玻璃球按 120 fps 请求刷新。") {
            sliderRow("渲染比例", config.performance.renderScale, OrbConfig.reference().performance.renderScale, 0.5f..1.25f, decimals = 2) { value ->
                updateConfig { current -> current.copy(performance = current.performance.copy(renderScale = value)) }
            }
        }
    }

    private fun buildAbout(page: Page) {
        val content = page.column
        val hero = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(palette.cell)
                cornerRadius = dpF(IosRows.CARD_RADIUS_DP)
            }
            setPadding(dp(24f), dp(24f), dp(24f), dp(22f))
        }
        hero.addView(ImageView(activity).apply {
            setImageResource(R.mipmap.ic_launcher)
            contentDescription = ui("应用图标")
            val radius = dpF(15f)
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: android.graphics.Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, radius)
                }
            }
            clipToOutline = true
        }, LinearLayout.LayoutParams(dp(64f), dp(64f)))
        hero.addView(TextView(activity).apply {
            text = activity.getString(R.string.app_name)
            textSize = 22f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            gravity = Gravity.CENTER
            setTextColor(palette.label)
            setPadding(0, dp(14f), 0, dp(4f))
        }, matchWrap())
        hero.addView(TextView(activity).apply {
            text = ui("灵动岛与液态玻璃球视觉体验，非商业学习演示。")
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(palette.secondaryLabel)
        }, matchWrap())
        content.addView(hero, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(dp(16f), dp(8f), dp(16f), 0)
        })
        content.section(null) {
            valueRow("App 版本", BuildConfig.VERSION_NAME)
            valueRow("获取 App", "", "关注抖音或微信公众号“你好小机”")
            navRow("仓库参考", "玻璃球视觉与动画参考开源学习项目") {
                runCatching {
                    activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(REFERENCE_REPOSITORY_URL)))
                }
            }
            navRow("开源许可与声明") { navigate("licenses") }
        }
    }

    private fun buildLicenses(page: Page) {
        val content = page.column
        rows.headerCard(content, RowIcon(R.drawable.ic_category_about, IosTint.Gray), "开源许可与声明", "本应用参考与使用的开源项目、商标声明及完整许可证。")
        listOf(
            "NOTICE.txt" to "声明与来源",
            "AndroidLiquidGlass-LICENSE.txt" to "Apache License 2.0",
        ).forEach { (file, heading) ->
            content.section(heading) {
                customRow(TextView(activity).apply {
                    text = readAsset(file)
                    textSize = 12f
                    typeface = Typeface.MONOSPACE
                    setTextColor(palette.secondaryLabel)
                    setLineSpacing(dpF(2f), 1f)
                    setTextIsSelectable(true)
                    setPadding(dp(20f), dp(16f), dp(20f), dp(16f))
                })
            }
        }
    }

    private fun readAsset(name: String): String = runCatching {
        activity.assets.open(name).bufferedReader().use { it.readText() }
    }.getOrElse { "—" }

    // endregion

    // region Helpers

    private fun LinearLayout.section(
        header: String?,
        footer: String? = null,
        build: IosRows.GroupScope.() -> Unit,
    ) {
        if (header != null) {
            addView(rows.sectionHeader(header), matchWrap())
        } else {
            addView(View(activity), LinearLayout.LayoutParams(1, dp(22f)))
        }
        rows.group(this, build)
        if (footer != null) addView(rows.sectionFooter(footer), matchWrap())
    }

    private fun screenKey(screen: Screen): String = when (screen) {
        Screen.Home -> "home"
        Screen.Overlay -> "overlay"
        is Screen.Group -> "group:${screen.group.name}"
        Screen.About -> "about"
        Screen.Licenses -> "licenses"
    }

    private fun decodeScreen(value: String?): Screen? = when {
        value.isNullOrBlank() -> null
        value == "home" -> Screen.Home
        value == "overlay" -> Screen.Overlay
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
        GlassAlert.show(
            activity = activity,
            palette = palette,
            title = ui("恢复本组默认参数？"),
            message = String.format(ui("仅恢复“%s”中的参数。"), ui(group.title())),
            cancelLabel = ui("取消"),
            confirmLabel = ui("恢复"),
            destructive = true,
        ) {
            requestCurrentPageRefresh()
            callbacks.resetGroup(group)
        }
    }

    private fun screenTitle(screen: Screen): String = when (screen) {
        Screen.Home -> "设置"
        Screen.Overlay -> "运行与权限"
        is Screen.Group -> screen.group.title()
        Screen.About -> "关于"
        Screen.Licenses -> "开源许可与声明"
    }

    private fun nativeStatusLabel(status: OverlayRuntimeStatus): String = when (status) {
        OverlayRuntimeStatus.Stopped -> "未运行"
        OverlayRuntimeStatus.Visible -> "正在显示"
        OverlayRuntimeStatus.Hidden -> "已隐藏"
        OverlayRuntimeStatus.PermissionRequired -> "需要权限"
        is OverlayRuntimeStatus.Error -> "运行异常"
    }

    private fun ui(source: String): String = text.tr(source)

    private fun dp(value: Float): Int = activity.dp(value)

    private fun dpF(value: Float): Float = activity.dpF(value)

    private fun matchWrap() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

    // endregion

    private companion object {
        const val REFERENCE_REPOSITORY_URL = "https://github.com/cxcboss/glass-voice-orb-study"
        const val PAGE_NAVIGATION_DURATION_MS = 320L
        const val PARALLAX = 0.28f
        const val SEARCH_MARGIN_DP = 6f
        const val BOTTOM_EDGE_DP = 96f
        const val MAX_SEARCH_RESULTS = 40
        const val SAVED_STATE_KEY = "native_settings_state"
        const val KEY_SCREEN = "screen"
        const val KEY_SCROLL_Y = "scroll_y"
        val EASE = PathInterpolator(0.2f, 0f, 0f, 1f)
    }
}
