package com.cxcboss.glassorb.overlay

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.WindowManager
import kotlin.math.abs

/**
 * Requests the exact 120 Hz mode for every overlay surface when the panel
 * exposes one at the current resolution. Some OEMs accept a refresh-rate hint
 * from an application overlay but keep composing it at 60 Hz unless a matching
 * display mode is requested as well.
 */
internal object OverlayRefreshRatePolicy {
    const val TARGET_REFRESH_RATE_HZ = 120f

    fun apply(params: WindowManager.LayoutParams, context: Context) {
        val display = context.getSystemService(DisplayManager::class.java)
            ?.getDisplay(Display.DEFAULT_DISPLAY)
        apply(params, display)
    }

    fun apply(params: WindowManager.LayoutParams, display: Display?) {
        val selectedMode = display?.let(::matchingMode)
        // preferredDisplayModeId is deliberately set only for an exact 120 Hz
        // mode at the active resolution. If there is no such mode, leave it at
        // zero so Android can choose the nearest supported mode from the rate
        // request without changing resolution.
        params.preferredDisplayModeId = selectedMode?.modeId ?: 0
        params.preferredRefreshRate = selectedMode?.refreshRate ?: TARGET_REFRESH_RATE_HZ
    }

    private fun matchingMode(display: Display): Display.Mode? {
        val active = display.mode ?: return null
        return display.supportedModes
            .asSequence()
            .filter {
                it.physicalWidth == active.physicalWidth &&
                    it.physicalHeight == active.physicalHeight
            }
            .filter { abs(it.refreshRate - TARGET_REFRESH_RATE_HZ) <= MODE_RATE_EPSILON_HZ }
            .minByOrNull { abs(it.refreshRate - TARGET_REFRESH_RATE_HZ) }
    }

    private const val MODE_RATE_EPSILON_HZ = 0.5f
}
