package com.cxcboss.glassorb.ui

import com.cxcboss.glassorb.data.ConfigGroup
import com.cxcboss.glassorb.overlay.OverlayRuntimeStatus
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

sealed interface SettingsRoute {
    data object Home : SettingsRoute
    data object OverlayDetails : SettingsRoute
    data class ConfigGroupDetail(val group: ConfigGroup) : SettingsRoute
    data object Presets : SettingsRoute
    data object DataManagement : SettingsRoute
    data object About : SettingsRoute
}

fun routeFor(group: ConfigGroup): String = "group/${group.name}"

fun parseSettingsRoute(route: String): SettingsRoute = when (route) {
    "overlay" -> SettingsRoute.OverlayDetails
    "presets" -> SettingsRoute.Presets
    "data" -> SettingsRoute.DataManagement
    "about" -> SettingsRoute.About
    else -> ConfigGroup.entries.firstOrNull { route == routeFor(it) }
        ?.let(SettingsRoute::ConfigGroupDetail)
        ?: SettingsRoute.Home
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

/**
 * Material Slider validates its current value against its visual step. Values
 * restored from JSON may contain binary floating-point residue (for example
 * 99.99999 instead of 100), so normalize before attaching a control.
 */
fun snapToSliderStep(value: Float, range: ClosedFloatingPointRange<Float>, decimals: Int): Float {
    if (!value.isFinite()) return range.start
    val multiplier = 10f.pow(decimals.coerceIn(0, 3))
    val steps = ((value.coerceIn(range) - range.start) * multiplier).roundToInt()
    return (range.start + steps / multiplier).coerceIn(range)
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
