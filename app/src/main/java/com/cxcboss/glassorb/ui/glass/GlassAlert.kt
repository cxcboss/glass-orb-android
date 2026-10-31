package com.cxcboss.glassorb.ui.glass

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.cxcboss.glassorb.R
import com.cxcboss.glassorb.ui.ios.IosPalette
import com.cxcboss.glassorb.ui.ios.dp
import com.cxcboss.glassorb.ui.ios.dpF

/**
 * iOS 26 alert: a 270 dp rounded glass card. The dialog window itself is the
 * glass (round-rect background + Android's cross-window background blur), so
 * it blurs the settings screen behind it. Without cross-window blur support
 * the card falls back to a more opaque tint.
 */
internal object GlassAlert {
    fun show(
        activity: Activity,
        palette: IosPalette,
        title: String,
        message: String,
        cancelLabel: String,
        confirmLabel: String,
        destructive: Boolean,
        onConfirm: () -> Unit,
    ) {
        val dialog = Dialog(activity, R.style.Theme_GlassOrb_GlassDialog)
        val radius = activity.dpF(34f)
        val blurAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            activity.getSystemService(WindowManager::class.java)?.isCrossWindowBlurEnabled == true

        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(activity.dp(22f), activity.dp(22f), activity.dp(22f), activity.dp(18f))
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: android.graphics.Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, radius)
                }
            }
        }
        // iOS 26 alerts: leading-aligned text and capsule buttons.
        card.addView(TextView(activity).apply {
            text = title
            textSize = 17f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(palette.label)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        card.addView(TextView(activity).apply {
            text = message
            textSize = 15f
            setLineSpacing(activity.dpF(2f), 1f)
            setTextColor(palette.label)
            alpha = 0.78f
            setPadding(0, activity.dp(6f), 0, activity.dp(20f))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val buttons = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        buttons.addView(
            button(activity, palette, cancelLabel, palette.label, palette.fill, bold = false) { dialog.dismiss() },
            LinearLayout.LayoutParams(0, activity.dp(48f), 1f).apply { marginEnd = activity.dp(5f) },
        )
        buttons.addView(
            button(
                activity, palette, confirmLabel,
                if (destructive) palette.red else Color.WHITE,
                if (destructive) palette.fill else palette.accent,
                bold = true,
            ) {
                dialog.dismiss()
                onConfirm()
            },
            LinearLayout.LayoutParams(0, activity.dp(48f), 1f).apply { marginStart = activity.dp(5f) },
        )
        card.addView(buttons, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        dialog.setContentView(card)

        dialog.window?.let { window ->
            window.setBackgroundDrawable(GradientDrawable().apply {
                cornerRadius = radius
                setColor(
                    when {
                        blurAvailable -> if (palette.dark) 0x99262628.toInt() else 0x99F6F6F8.toInt()
                        else -> palette.glassFallback
                    },
                )
                setStroke(1.coerceAtLeast(activity.dp(0.6f)), if (palette.dark) 0x40FFFFFF else 0xB3FFFFFF.toInt())
            })
            window.setLayout(activity.dp(300f), WindowManager.LayoutParams.WRAP_CONTENT)
            window.setDimAmount(if (palette.dark) 0.5f else 0.28f)
            if (blurAvailable) window.setBackgroundBlurRadius(activity.dp(48f))
        }
        dialog.show()
    }

    private fun button(
        activity: Activity,
        palette: IosPalette,
        label: String,
        textColor: Int,
        fillColor: Int,
        bold: Boolean,
        onClick: () -> Unit,
    ) = TextView(activity).apply {
        text = label
        textSize = 17f
        gravity = Gravity.CENTER
        setTextColor(textColor)
        typeface = Typeface.create(if (bold) "sans-serif-medium" else "sans-serif", Typeface.NORMAL)
        isClickable = true
        isFocusable = true
        val capsule = activity.dpF(24f)
        background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), GradientDrawable().apply {
                cornerRadius = capsule
                setColor(fillColor)
                alpha = 150
            })
            addState(intArrayOf(), GradientDrawable().apply { cornerRadius = capsule; setColor(fillColor) })
        }
        setOnClickListener { onClick() }
    }
}
