package com.cxcboss.glassorb.overlay

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout

/**
 * The public background-blur API belongs to Window, so this optional layer is
 * a real transparent Dialog window. It never receives input and has no dim.
 */
internal class AndroidBackgroundBlurWindow(
    private val context: Context,
    private val accessibilityWindow: Boolean,
) : BackgroundBlurWindow {
    private var dialog: Dialog? = null

    override fun show(bounds: IntRect, blurRadiusPx: Int): Boolean {
        dialog?.let { return update(bounds, blurRadiusPx) }
        val candidate = Dialog(context, android.R.style.Theme_Translucent_NoTitleBar)
        return try {
            candidate.requestWindowFeature(Window.FEATURE_NO_TITLE)
            candidate.setCancelable(false)
            candidate.setCanceledOnTouchOutside(false)
            candidate.setContentView(
                FrameLayout(context).apply { setBackgroundColor(Color.TRANSPARENT) },
            )
            candidate.window?.let(::configureWindow) ?: error("blur window unavailable")
            candidate.show()
            val window = candidate.window ?: error("blur window unavailable after show")
            configureWindow(window)
            applyWindow(window, bounds, blurRadiusPx)
            dialog = candidate
            true
        } catch (_: Throwable) {
            runCatching { candidate.dismiss() }
            false
        }
    }

    override fun update(bounds: IntRect, blurRadiusPx: Int): Boolean {
        val window = dialog?.window ?: return false
        return try {
            applyWindow(window, bounds, blurRadiusPx)
            true
        } catch (_: Throwable) {
            false
        }
    }

    override fun hide() {
        dialog?.let { runCatching { it.hide() } }
    }

    override fun destroy() {
        dialog?.let { runCatching { it.dismiss() } }
        dialog = null
    }

    private fun configureWindow(window: Window) {
        window.setType(
            BackgroundBlurPolicy.windowType(
                accessibilityWindow = accessibilityWindow,
                apiLevel = android.os.Build.VERSION.SDK_INT,
            ),
        )
        window.addFlags(BackgroundBlurPolicy.windowFlags())
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.setDimAmount(0f)
        window.setFormat(PixelFormat.TRANSLUCENT)
        window.setBackgroundDrawable(transparentCircle(1, 1))
    }

    private fun applyWindow(window: Window, bounds: IntRect, blurRadiusPx: Int) {
        require(bounds.width > 0 && bounds.height > 0)
        val attributes = window.attributes
        attributes.gravity = Gravity.TOP or Gravity.LEFT
        attributes.x = bounds.left
        attributes.y = bounds.top
        attributes.width = bounds.width
        attributes.height = bounds.height
        window.attributes = attributes
        window.setBackgroundDrawable(transparentCircle(bounds.width, bounds.height))
        window.setLayout(bounds.width, bounds.height)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            window.setBackgroundBlurRadius(blurRadiusPx.coerceAtLeast(0))
        }
    }

    private fun transparentCircle(width: Int, height: Int): GradientDrawable = GradientDrawable().apply {
        setColor(Color.TRANSPARENT)
        cornerRadius = minOf(width, height) * 0.5f
    }
}
