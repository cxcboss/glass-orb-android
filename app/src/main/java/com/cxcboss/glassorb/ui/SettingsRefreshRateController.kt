package com.cxcboss.glassorb.ui

import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Display
import android.view.MotionEvent
import android.widget.ScrollView
import androidx.activity.ComponentActivity

/**
 * Keeps settings-page scrolling on the panel's highest supported refresh rate
 * while the finger or fling is active. OEMs are free to ignore a preference,
 * but this gives MIUI and Android's scheduler an explicit touch-time hint.
 */
internal class SettingsRefreshRateController(
    private val activity: ComponentActivity,
) {
    private val window = activity.window
    private val handler = Handler(Looper.getMainLooper())
    private val originalPreferredModeId = window.attributes.preferredDisplayModeId
    private val originalPreferredRefreshRate = window.attributes.preferredRefreshRate
    private var highestRefreshRate = findHighestRefreshRate()
    private var boosted = false
    private var touching = false
    private var lastScrollActivityUptime = 0L

    private val restoreRunnable = Runnable {
        val elapsed = SystemClock.uptimeMillis() - lastScrollActivityUptime
        if (touching || elapsed < RESTORE_DELAY_MS) {
            scheduleRestore((RESTORE_DELAY_MS - elapsed).coerceAtLeast(1L))
        } else {
            restoreSystemPreference()
        }
    }

    init {
        // Android 15 adds an explicit window-level touch boost switch. Keep it
        // enabled for this settings activity; older releases use the preferred
        // refresh-rate hint below.
        if (Build.VERSION.SDK_INT >= 35) {
            runCatching { window.setFrameRateBoostOnTouchEnabled(true) }
        }
    }

    fun attach(scrollView: ScrollView) {
        scrollView.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    touching = true
                    markScrollActivity()
                    requestHighestRefreshRate()
                }

                MotionEvent.ACTION_MOVE -> {
                    markScrollActivity()
                    requestHighestRefreshRate()
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    touching = false
                    markScrollActivity()
                    scheduleRestore()
                }
            }
            // Do not consume the event; ScrollView still owns scrolling,
            // nested touch dispatch, click cancellation and accessibility.
            false
        }
        scrollView.setOnScrollChangeListener { _, scrollY, oldScrollY, _, _ ->
            if (scrollY == oldScrollY) return@setOnScrollChangeListener
            markScrollActivity()
            requestHighestRefreshRate()
            if (!touching) scheduleRestore()
        }
    }

    fun dispose() {
        handler.removeCallbacks(restoreRunnable)
        touching = false
        restoreSystemPreference()
    }

    private fun markScrollActivity() {
        lastScrollActivityUptime = SystemClock.uptimeMillis()
    }

    private fun requestHighestRefreshRate() {
        if (boosted) return
        highestRefreshRate = findHighestRefreshRate()
        if (highestRefreshRate <= 0f) return
        val attributes = window.attributes
        // Use the refresh-rate preference without selecting a display mode, so
        // Xiaomi can switch refresh rate without an accidental resolution or
        // color-mode change.
        attributes.preferredDisplayModeId = 0
        attributes.preferredRefreshRate = highestRefreshRate
        window.attributes = attributes
        boosted = true
    }

    private fun restoreSystemPreference() {
        if (!boosted) return
        val attributes = window.attributes
        attributes.preferredDisplayModeId = originalPreferredModeId
        attributes.preferredRefreshRate = originalPreferredRefreshRate
        window.attributes = attributes
        boosted = false
    }

    private fun scheduleRestore(delayMs: Long = RESTORE_DELAY_MS) {
        handler.removeCallbacks(restoreRunnable)
        handler.postDelayed(restoreRunnable, delayMs)
    }

    private fun findHighestRefreshRate(): Float {
        val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity.display
        } else {
            @Suppress("DEPRECATION")
            activity.windowManager.defaultDisplay
        } ?: return 0f
        return display.supportedModes
            .asSequence()
            .map(Display.Mode::getRefreshRate)
            .filter { it.isFinite() && it > 0f }
            .maxOrNull()
            ?: display.refreshRate.takeIf { it.isFinite() && it > 0f }
            ?: 0f
    }

    private companion object {
        const val RESTORE_DELAY_MS = 420L
    }
}
