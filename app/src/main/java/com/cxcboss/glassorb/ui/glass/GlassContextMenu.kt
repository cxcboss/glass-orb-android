package com.cxcboss.glassorb.ui.glass

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.cxcboss.glassorb.R
import com.cxcboss.glassorb.ui.ios.IosPalette
import com.cxcboss.glassorb.ui.ios.dp

/**
 * iOS context menu: a Liquid Glass card that springs out next to the
 * long-pressed row, over a light scrim. It lives in the activity window so the
 * glass can refract the list beneath it.
 */
internal class GlassContextMenu(
    private val container: FrameLayout,
    private val palette: IosPalette,
    private val backdrop: GlassBackdrop,
) {
    private var scrim: View? = null
    private var card: LiquidGlassView? = null
    val isShowing: Boolean get() = card != null

    fun show(anchor: View, title: String, actionLabel: String, actionDetail: String, onAction: () -> Unit) {
        dismiss(animated = false)
        val context = container.context
        val scrimView = View(context).apply {
            setBackgroundColor(if (palette.dark) 0x66000000 else 0x24000000)
            alpha = 0f
            isClickable = true
            setOnClickListener { dismiss() }
        }
        container.addView(scrimView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val inset = context.dp(12f)
        val menu = LiquidGlassView(context, palette).apply {
            this.backdrop = this@GlassContextMenu.backdrop
            cornerRadiusPx = context.dp(22f).toFloat()
            glassInsetPx = inset
            blurDp = 18f
            lensDp = 10f
            bezelDp = 16f
        }
        val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        column.addView(TextView(context).apply {
            text = title
            textSize = 13f
            setTextColor(palette.secondaryLabel)
            setPadding(context.dp(18f), context.dp(12f), context.dp(18f), context.dp(6f))
        })
        column.addView(View(context).apply { setBackgroundColor(palette.separator) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1))
        val item = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = context.dp(52f)
            setPadding(context.dp(18f), context.dp(6f), context.dp(16f), context.dp(6f))
            isClickable = true
            isFocusable = true
            contentDescription = actionLabel
            background = StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_pressed), ColorDrawable(palette.glassSelection))
                addState(intArrayOf(), ColorDrawable(Color.TRANSPARENT))
            }
            setOnClickListener {
                dismiss()
                onAction()
            }
        }
        item.addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(context).apply {
                text = actionLabel
                textSize = 17f
                setTextColor(palette.label)
            })
            addView(TextView(context).apply {
                text = actionDetail
                textSize = 13f
                setTextColor(palette.secondaryLabel)
            })
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        item.addView(ImageView(context).apply {
            setImageResource(R.drawable.ic_reset)
            imageTintList = ColorStateList.valueOf(palette.label)
        }, LinearLayout.LayoutParams(context.dp(22f), context.dp(22f)).apply { marginStart = context.dp(14f) })
        column.addView(item)
        menu.addView(column, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val width = context.dp(250f) + inset * 2
        val anchorLocation = IntArray(2)
        val containerLocation = IntArray(2)
        anchor.getLocationInWindow(anchorLocation)
        container.getLocationInWindow(containerLocation)
        val anchorTop = anchorLocation[1] - containerLocation[1]
        val anchorBottom = anchorTop + anchor.height
        val estimatedHeight = context.dp(110f) + inset * 2
        val below = anchorBottom + estimatedHeight < container.height - context.dp(96f)
        val params = FrameLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.TOP or Gravity.END
            rightMargin = context.dp(16f) - inset
            topMargin = if (below) anchorBottom - inset + context.dp(4f) else anchorTop - estimatedHeight + inset
        }
        menu.pivotX = width.toFloat()
        menu.pivotY = if (below) 0f else estimatedHeight.toFloat()
        menu.alpha = 0f
        menu.scaleX = 0.82f
        menu.scaleY = 0.82f
        container.addView(menu, params)
        scrim = scrimView
        card = menu
        scrimView.animate().alpha(1f).setDuration(180L).start()
        menu.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(260L)
            .setInterpolator(android.view.animation.OvershootInterpolator(1.4f)).start()
    }

    fun dismiss(animated: Boolean = true) {
        val menu = card ?: return
        val scrimView = scrim
        card = null
        scrim = null
        if (!animated) {
            container.removeView(menu)
            scrimView?.let(container::removeView)
            return
        }
        menu.animate().alpha(0f).scaleX(0.9f).scaleY(0.9f).setDuration(150L)
            .withEndAction { container.removeView(menu) }.start()
        scrimView?.animate()?.alpha(0f)?.setDuration(150L)?.withEndAction { container.removeView(scrimView) }?.start()
    }
}
