package com.cxcboss.glassorb.ui.ios

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.util.TypedValue
import android.view.View

internal fun Context.dpF(value: Float): Float = value * resources.displayMetrics.density

internal fun Context.dp(value: Float): Int = (dpF(value) + 0.5f).toInt()

internal fun View.dpF(value: Float): Float = context.dpF(value)

internal fun View.dp(value: Float): Int = context.dp(value)

internal fun Context.spF(value: Float): Float =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)

/** Tile hues used by iOS Settings category icons, as (light, dark) system colors. */
internal enum class IosTint(val light: Int, val dark: Int) {
    Blue(0xFF007AFF.toInt(), 0xFF0A84FF.toInt()),
    Green(0xFF34C759.toInt(), 0xFF30D158.toInt()),
    Indigo(0xFF5856D6.toInt(), 0xFF5E5CE6.toInt()),
    Orange(0xFFFF9500.toInt(), 0xFFFF9F0A.toInt()),
    Pink(0xFFFF2D55.toInt(), 0xFFFF375F.toInt()),
    Purple(0xFFAF52DE.toInt(), 0xFFBF5AF2.toInt()),
    Red(0xFFFF3B30.toInt(), 0xFFFF453A.toInt()),
    Teal(0xFF30B0C7.toInt(), 0xFF40C8E0.toInt()),
    Gray(0xFF8E8E93.toInt(), 0xFF98989D.toInt()),
}

/**
 * iOS 26 semantic colors. Android has no system equivalent, so the values are
 * the published system palette resolved once for the current night mode.
 */
internal class IosPalette(val dark: Boolean) {
    val groupedBackground = if (dark) 0xFF000000.toInt() else 0xFFF2F2F7.toInt()
    val cell = if (dark) 0xFF1C1C1E.toInt() else Color.WHITE
    val cellPressed = if (dark) 0xFF2C2C2E.toInt() else 0xFFE5E5EA.toInt()
    val label = if (dark) Color.WHITE else Color.BLACK
    val secondaryLabel = if (dark) 0x99EBEBF5.toInt() else 0x993C3C43.toInt()
    val tertiaryLabel = if (dark) 0x4DEBEBF5 else 0x4D3C3C43
    val separator = if (dark) 0xFF38383A.toInt() else 0xFFD1D1D6.toInt()
    val track = if (dark) 0xFF39393D.toInt() else 0xFFE9E9EA.toInt()
    val segmentTrack = if (dark) 0x3D767680 else 0x1F767680
    /** tertiarySystemFill: search field and neutral capsule buttons. */
    val fill = if (dark) 0x3D767680 else 0x1F767680
    val accent = tint(IosTint.Blue)
    val green = tint(IosTint.Green)
    val red = tint(IosTint.Red)

    // Liquid Glass material.
    val glassTint = if (dark) 0x5C1C1C1E else 0x59FFFFFF
    val glassFallback = if (dark) 0xF5262628.toInt() else 0xF7FAFAFC.toInt()
    val glassRimHigh = if (dark) 0x66FFFFFF else 0xFFFFFFFF.toInt()
    val glassRimLow = if (dark) 0x1FFFFFFF else 0x4DFFFFFF
    val glassSelection = if (dark) 0x33FFFFFF else 0x1F000000
    val glassShadow = if (dark) 0x66000000 else 0x26000000

    fun tint(tint: IosTint): Int = if (dark) tint.dark else tint.light

    companion object {
        fun of(context: Context): IosPalette = IosPalette(
            context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                Configuration.UI_MODE_NIGHT_YES,
        )
    }
}
