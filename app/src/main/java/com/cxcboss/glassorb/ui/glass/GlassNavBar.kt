package com.cxcboss.glassorb.ui.glass

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.cxcboss.glassorb.R
import com.cxcboss.glassorb.ui.ios.IosPalette
import com.cxcboss.glassorb.ui.ios.dp

/**
 * Persistent top chrome. The bar material fades in once content scrolls under
 * it (iOS 26 "scroll edge" behaviour), the inline title fades in as the large
 * title leaves, and the back affordance is a circular Liquid Glass button.
 */
internal class GlassNavBar(
    context: Context,
    private val palette: IosPalette,
    backdrop: GlassBackdrop,
    onBack: () -> Unit,
) : FrameLayout(context) {
    // iOS 26 has no bar: content blurs and fades out under a soft scroll edge.
    private val barGlass = LiquidGlassView(context, palette).apply {
        this.backdrop = backdrop
        cornerRadiusPx = 0f
        blurDp = 10f
        lensDp = 0f
        rimEnabled = false
        shadowEnabled = false
        sheenEnabled = false
        edgeFade = LiquidGlassView.EDGE_TOP
        edgeFadeLengthPx = dp(FADE_DP + 6f).toFloat()
        // Page colour over a light blur: text that slides under the title is
        // softened enough to keep the inline title legible, like iOS 26.
        tintOverride = (palette.groupedBackground and 0x00FFFFFF) or (0xC7 shl 24)
        glassAlpha = 0f
    }
    private val titleView = TextView(context).apply {
        textSize = 17f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setTextColor(palette.label)
        maxLines = 1
        gravity = Gravity.CENTER
        alpha = 0f
    }
    private val backButton = LiquidGlassView(context, palette).apply {
        this.backdrop = backdrop
        glassInsetPx = dp(BACK_INSET_DP)
        interactive = true
        blurDp = 8f
        lensDp = 8f
        bezelDp = 10f
        visibility = View.GONE
        isClickable = true
        isFocusable = true
        setOnClickListener { onBack() }
    }
    private var topInsetPx = 0
    private var backVisible = false

    init {
        clipChildren = false
        addView(barGlass, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        addView(titleView, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(CONTENT_HEIGHT_DP)).apply {
            gravity = Gravity.BOTTOM
            bottomMargin = dp(FADE_DP)
        })
        val chevron = ImageView(context).apply {
            setImageResource(R.drawable.ic_chevron_back)
            imageTintList = ColorStateList.valueOf(palette.label)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
        }
        backButton.addView(chevron, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val backSize = dp(BACK_SIZE_DP + BACK_INSET_DP * 2)
        addView(backButton, LayoutParams(backSize, backSize).apply {
            gravity = Gravity.BOTTOM or Gravity.START
            leftMargin = dp(10f - BACK_INSET_DP)
            bottomMargin = (dp(CONTENT_HEIGHT_DP) - dp(BACK_SIZE_DP)) / 2 - dp(BACK_INSET_DP) + dp(FADE_DP)
        })
        applyHeight()
    }

    /** Height of the interactive bar area; content padding starts here. */
    val barHeightPx: Int get() = topInsetPx + dp(CONTENT_HEIGHT_DP)

    fun setTopInset(px: Int) {
        if (topInsetPx == px) return
        topInsetPx = px
        applyHeight()
    }

    fun setTitle(text: CharSequence) {
        titleView.text = text
    }

    fun setBackDescription(text: CharSequence) {
        backButton.contentDescription = text
    }

    fun setBackVisible(visible: Boolean) {
        if (backVisible == visible) return
        backVisible = visible
        backButton.visibility = if (visible) View.VISIBLE else View.GONE
    }

    /** [titleProgress]: 0 = large title visible, 1 = inline title shown. [edgeProgress]: bar material opacity. */
    fun setScrollState(titleProgress: Float, edgeProgress: Float) {
        titleView.alpha = titleProgress.coerceIn(0f, 1f)
        barGlass.glassAlpha = edgeProgress.coerceIn(0f, 1f)
    }

    private fun applyHeight() {
        val params = layoutParams ?: LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, barHeightPx)
        params.height = barHeightPx + dp(FADE_DP)
        layoutParams = params
    }

    companion object {
        const val CONTENT_HEIGHT_DP = 52f
        /** Extra height below the bar where the scroll-edge material fades out. */
        const val FADE_DP = 22f
        const val BACK_SIZE_DP = 40f
        private const val BACK_INSET_DP = 6f
    }
}
