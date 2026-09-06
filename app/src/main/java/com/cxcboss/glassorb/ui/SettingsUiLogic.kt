package com.cxcboss.glassorb.ui

import com.cxcboss.glassorb.data.ConfigGroup
import com.cxcboss.glassorb.overlay.OverlayRuntimeStatus
import kotlin.math.abs
import kotlin.math.pow

sealed interface SettingsRoute {
    data object Home : SettingsRoute
    data object OverlayDetails : SettingsRoute
    data class ConfigGroupDetail(val group: ConfigGroup) : SettingsRoute
    data object Presets : SettingsRoute
    data object DataManagement : SettingsRoute
    data object About : SettingsRoute
}

data class SettingsNavigator(private val routes: List<SettingsRoute> = listOf(SettingsRoute.Home)) {
    val current: SettingsRoute get() = routes.last()
    val depth: Int get() = routes.size
    fun push(route: SettingsRoute) = if (route == current || route == SettingsRoute.Home) this else copy(routes = routes + route)
    fun pop() = if (depth > 1) copy(routes = routes.dropLast(1)) else this
    fun save(): List<String> = routes.map {
        when (it) {
            SettingsRoute.Home -> "home"
            SettingsRoute.OverlayDetails -> "overlay"
            is SettingsRoute.ConfigGroupDetail -> "group:${it.group.name}"
            SettingsRoute.Presets -> "presets"
            SettingsRoute.DataManagement -> "data"
            SettingsRoute.About -> "about"
        }
    }
    companion object {
        fun restore(saved: List<String>): SettingsNavigator {
            var result = SettingsNavigator()
            saved.forEach { key ->
                val route = when (key) {
                    "overlay" -> SettingsRoute.OverlayDetails
                    "presets" -> SettingsRoute.Presets
                    "data" -> SettingsRoute.DataManagement
                    "about" -> SettingsRoute.About
                    else -> ConfigGroup.entries.firstOrNull { key == "group:${it.name}" }?.let(SettingsRoute::ConfigGroupDetail)
                }
                if (route != null) result = result.push(route)
            }
            return result
        }
    }
}

enum class OverlayAction { RequestPermission, Show, Start, Hide, None }

fun resolveOverlayAction(enabled: Boolean, permission: Boolean, status: OverlayRuntimeStatus): OverlayAction = when {
    !enabled -> OverlayAction.Hide
    !permission -> OverlayAction.RequestPermission
    status == OverlayRuntimeStatus.Hidden -> OverlayAction.Show
    status == OverlayRuntimeStatus.Visible -> OverlayAction.None
    else -> OverlayAction.Start
}

fun sliderValueAt(x: Float, width: Float, range: ClosedFloatingPointRange<Float>, rtl: Boolean): Float {
    if (width <= 0f || !x.isFinite()) return range.start
    val fraction = (x / width).coerceIn(0f, 1f)
    return (range.start + (if (rtl) 1f - fraction else fraction) * (range.endInclusive - range.start)).coerceIn(range)
}

enum class SliderGestureAxis {
    Undecided,
    Horizontal,
    Vertical,
}

/** Locks a slider to the axis that wins touch slop. */
fun sliderGestureAxis(deltaX: Float, deltaY: Float, touchSlop: Float): SliderGestureAxis {
    val x = abs(deltaX)
    val y = abs(deltaY)
    val slop = touchSlop.coerceAtLeast(0f)
    if (!x.isFinite() || !y.isFinite() || maxOf(x, y) < slop) return SliderGestureAxis.Undecided
    if (x == y) return SliderGestureAxis.Undecided
    return if (x > y) SliderGestureAxis.Horizontal else SliderGestureAxis.Vertical
}

fun shouldCommitSliderTap(dragging: Boolean, pressed: Boolean, consumed: Boolean): Boolean =
    !dragging && !pressed && !consumed

fun isParameterModified(value: Float, defaultValue: Float, decimals: Int): Boolean =
    abs(value - defaultValue) >= 0.5f * 10f.pow(-decimals)

fun restoreParameter(defaultValue: Float): Float = defaultValue
