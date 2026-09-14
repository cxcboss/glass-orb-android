package com.cxcboss.glassorb.overlay

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import com.cxcboss.glassorb.R
import kotlin.math.abs

/**
 * A single, transparent floating Window that uses Android's public
 * cross-window background-blur API. Its GradientDrawable background is
 * deliberately a round-rect: the platform compositor reads round-rect corner
 * radii from this drawable, while an oval Outline is treated as a rectangle.
 */
internal class AndroidBackgroundBlurWindow(
    private val context: Context,
    private val accessibilityWindow: Boolean,
    private val rectangular: Boolean = false,
) : BackgroundBlurWindow {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val backgroundDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        // A minimally translucent color keeps the Window background present on
        // compositor implementations that optimize a fully transparent layer
        // away, without tinting the GL glass orb.
        setColor(Color.argb(BACKGROUND_ALPHA, 255, 255, 255))
    }

    private var dialog: Dialog? = null
    private var appliedBounds: IntRect? = null
    private var pendingBounds: IntRect? = null
    private var lastGeometryApplyUptimeMs = Long.MIN_VALUE
    private var geometryApplyScheduled = false
    private var appliedBlurRadius = Int.MIN_VALUE

    private val applyPendingGeometry = Runnable {
        geometryApplyScheduled = false
        val window = dialog?.window ?: return@Runnable
        val nextBounds = pendingBounds ?: return@Runnable
        applyGeometry(window, nextBounds, force = false)
    }

    override fun show(windowBounds: IntRect, apertureBounds: IntRect, blurRadiusPx: Int): Boolean {
        val targetBounds = blurBounds(windowBounds, apertureBounds)
        dialog?.window?.let { window ->
            return runCatching {
                cancelPendingGeometry()
                applyGeometry(window, targetBounds, force = true)
                applyBlurRadius(window, blurRadiusPx)
                applyCompositorVisibility(window, blurRadiusPx)
                true
            }.getOrElse { error ->
                Log.w(TAG, "Unable to reactivate background blur window", error)
                false
            }
        }

        val candidate = Dialog(context, R.style.Theme_GlassOrb_BackgroundBlur)
        return try {
            candidate.requestWindowFeature(Window.FEATURE_NO_TITLE)
            candidate.setCancelable(false)
            candidate.setCanceledOnTouchOutside(false)
            candidate.setContentView(
                FrameLayout(context).apply { setBackgroundColor(Color.TRANSPARENT) },
            )
            val windowBeforeShow = candidate.window ?: error("blur window unavailable")
            configureWindow(windowBeforeShow)
            applyGeometry(windowBeforeShow, targetBounds, force = true)
            applyBlurRadius(windowBeforeShow, blurRadiusPx)
            candidate.show()

            val window = candidate.window ?: error("blur window unavailable after show")
            configureWindow(window)
            applyGeometry(window, targetBounds, force = true)
            applyBlurRadius(window, blurRadiusPx)
            applyCompositorVisibility(window, blurRadiusPx)
            dialog = candidate
            true
        } catch (error: Exception) {
            Log.w(TAG, "Unable to attach background blur window", error)
            runCatching { candidate.dismiss() }
            false
        }
    }

    override fun update(windowBounds: IntRect, apertureBounds: IntRect, blurRadiusPx: Int): Boolean {
        val window = dialog?.window ?: return false
        return runCatching {
            applyBlurRadius(window, blurRadiusPx)
            scheduleGeometry(window, blurBounds(windowBounds, apertureBounds))
            applyCompositorVisibility(window, blurRadiusPx)
            true
        }.getOrElse { error ->
            Log.w(TAG, "Unable to update background blur window", error)
            false
        }
    }

    override fun hide() {
        // Keep this Window in its original z-order below the GL surface.
        // Recreating it on each expansion can make the foreground orb blur.
        cancelPendingGeometry()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && appliedBlurRadius != 0) {
            dialog?.window?.setBackgroundBlurRadius(0)
            appliedBlurRadius = 0
        }
        dialog?.window?.decorView?.visibility = View.INVISIBLE
    }

    override fun destroy() {
        cancelPendingGeometry()
        dialog?.let { runCatching { it.dismiss() } }
        dialog = null
        appliedBounds = null
        appliedBlurRadius = Int.MIN_VALUE
    }

    private fun configureWindow(window: Window) {
        window.setType(
            BackgroundBlurPolicy.windowType(
                accessibilityWindow = accessibilityWindow,
                apiLevel = Build.VERSION.SDK_INT,
            ),
        )
        window.addFlags(BackgroundBlurPolicy.windowFlags())
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.setDimAmount(0f)
        window.setFormat(PixelFormat.TRANSLUCENT)
        window.attributes = window.attributes.apply {
            OverlayRefreshRatePolicy.apply(this, context)
        }
        // Window#setBackgroundDrawable is intentional. DecorView forwards this
        // rounded rectangle's radius to the public background-blur compositor.
        window.setBackgroundDrawable(backgroundDrawable)
        window.decorView.setPadding(0, 0, 0, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                setFitInsetsTypes(0)
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    private fun scheduleGeometry(window: Window, targetBounds: IntRect) {
        if (sameBounds(appliedBounds, targetBounds)) return
        pendingBounds = targetBounds
        val elapsed = SystemClock.uptimeMillis() - lastGeometryApplyUptimeMs
        if (appliedBounds == null || elapsed >= GEOMETRY_UPDATE_INTERVAL_MS) {
            cancelPendingGeometry()
            applyGeometry(window, targetBounds, force = false)
            return
        }
        if (!geometryApplyScheduled) {
            geometryApplyScheduled = true
            mainHandler.postDelayed(
                applyPendingGeometry,
                (GEOMETRY_UPDATE_INTERVAL_MS - elapsed).coerceAtLeast(1L),
            )
        }
    }

    private fun applyGeometry(window: Window, targetBounds: IntRect, force: Boolean) {
        require(targetBounds.width > 0 && targetBounds.height > 0)
        if (!force && sameBounds(appliedBounds, targetBounds)) return
        val attributes = window.attributes
        attributes.title = "Glass orb background blur"
        attributes.gravity = Gravity.TOP or Gravity.LEFT
        attributes.x = targetBounds.left
        attributes.y = targetBounds.top
        attributes.width = targetBounds.width
        attributes.height = targetBounds.height
        window.attributes = attributes
        window.setLayout(targetBounds.width, targetBounds.height)
        backgroundDrawable.cornerRadius = if (rectangular) {
            0f
        } else {
            minOf(targetBounds.width, targetBounds.height) * 0.5f
        }
        // A square with a radius equal to half its side is a circle. When the
        // glass changes width/height this becomes a rounded pill instead of a
        // square blur, which is the only non-rectangular public shape Android
        // passes through to SurfaceFlinger.
        backgroundDrawable.invalidateSelf()
        appliedBounds = targetBounds
        pendingBounds = null
        lastGeometryApplyUptimeMs = SystemClock.uptimeMillis()
    }

    private fun applyBlurRadius(window: Window, blurRadiusPx: Int) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val nextRadius = blurRadiusPx.coerceIn(0, BackgroundBlurPolicy.MAX_BLUR_RADIUS_PX)
        if (nextRadius == appliedBlurRadius) return
        window.setBackgroundBlurRadius(nextRadius)
        appliedBlurRadius = nextRadius
    }

    /**
     * Keep the layer in its original z-order but take it out of composition
     * when its radius is zero. On Xiaomi, several transparent overlay layers
     * are enough to make SurfaceFlinger classify the animation as 60 Hz.
     */
    private fun applyCompositorVisibility(window: Window, blurRadiusPx: Int) {
        window.decorView.visibility = if (blurRadiusPx > 0) View.VISIBLE else View.INVISIBLE
    }

    private fun blurBounds(windowBounds: IntRect, apertureBounds: IntRect): IntRect {
        // With Android's public API the Window background itself defines the
        // blur bounds. Using the live aperture keeps the system-owned round
        // rect aligned to breathing, press, drag and expansion geometry.
        return apertureBounds.takeIf { it.width > 0 && it.height > 0 } ?: windowBounds
    }

    private fun cancelPendingGeometry() {
        mainHandler.removeCallbacks(applyPendingGeometry)
        geometryApplyScheduled = false
        pendingBounds = null
    }

    private fun sameBounds(first: IntRect?, second: IntRect): Boolean =
        first != null &&
            abs(first.left - second.left) <= BOUNDS_EPSILON_PX &&
            abs(first.top - second.top) <= BOUNDS_EPSILON_PX &&
            abs(first.right - second.right) <= BOUNDS_EPSILON_PX &&
            abs(first.bottom - second.bottom) <= BOUNDS_EPSILON_PX

    private companion object {
        const val TAG = "GlassOrbBlur"
        const val BACKGROUND_ALPHA = 1
        const val BOUNDS_EPSILON_PX = 2
        // The 30 Hz gate was only a conservative workaround for the former
        // multi-window mask. The official single-window path can track a
        // 120 Hz animation; transition states disable blur altogether so the
        // expensive layout path is not exercised while the orb is morphing.
        const val GEOMETRY_UPDATE_INTERVAL_MS = 8L
    }
}
