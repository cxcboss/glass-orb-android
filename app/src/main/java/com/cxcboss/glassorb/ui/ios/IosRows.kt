package com.cxcboss.glassorb.ui.ios

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.cxcboss.glassorb.R
import com.cxcboss.glassorb.ui.formatSliderValue
import com.cxcboss.glassorb.ui.isParameterModified
import com.cxcboss.glassorb.ui.snapToSliderStep

internal data class RowIcon(val drawableRes: Int, val tint: IosTint)

/** Mutable handles returned by row builders so state changes never rebuild a page. */
internal class RowHandle(
    val row: View,
    val title: TextView? = null,
    val value: TextView? = null,
    val subtitle: TextView? = null,
    val switch: IosSwitch? = null,
    val slider: IosSlider? = null,
) {
    private var suppress = false
    var onSwitchChanged: ((Boolean) -> Unit)? = null

    fun setValue(text: CharSequence?) {
        value?.text = text
    }

    fun setSubtitle(text: CharSequence?) {
        subtitle?.apply {
            this.text = text
            visibility = if (text.isNullOrBlank()) View.GONE else View.VISIBLE
        }
    }

    /** Updates the switch without invoking the user-facing change listener. */
    fun setChecked(checked: Boolean) {
        val target = switch ?: return
        if (target.isChecked == checked) return
        suppress = true
        target.isChecked = checked
        suppress = false
    }

    internal fun isSuppressed(): Boolean = suppress
}

/**
 * Builds iOS 26 inset-grouped Settings rows. Text passed in is the Chinese
 * source string; [translate] resolves it for the current locale.
 */
internal class IosRows(
    private val context: Context,
    val palette: IosPalette,
    private val translate: (String) -> String,
) {
    private val cardRadius get() = context.dpF(CARD_RADIUS_DP)

    /** Receives every row title as it is built; used to index settings for search. */
    var titleSink: ((String) -> Unit)? = null

    /**
     * Long-press on a modified slider row asks the host to present a reset menu
     * (anchor row, translated label, formatted default, apply action).
     */
    var sliderResetPresenter: ((View, String, String, () -> Unit) -> Unit)? = null

    fun largeTitle(text: String): TextView = TextView(context).apply {
        this.text = translate(text)
        textSize = 34f
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
        setTextColor(palette.label)
        includeFontPadding = false
        setPadding(context.dp(20f), context.dp(8f), context.dp(20f), context.dp(10f))
    }

    fun sectionHeader(text: String): TextView = TextView(context).apply {
        this.text = translate(text)
        textSize = 13f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setTextColor(palette.secondaryLabel)
        setPadding(context.dp(32f), context.dp(22f), context.dp(32f), context.dp(8f))
    }

    fun sectionFooter(text: String): TextView = TextView(context).apply {
        this.text = translate(text)
        textSize = 13f
        setLineSpacing(context.dpF(2f), 1f)
        setTextColor(palette.secondaryLabel)
        setPadding(context.dp(32f), context.dp(8f), context.dp(32f), context.dp(4f))
    }

    /** Adds a card with [build] rows to [parent] and returns the card. */
    fun group(parent: LinearLayout, build: GroupScope.() -> Unit): LinearLayout {
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(palette.cell)
                cornerRadius = cardRadius
            }
            clipToOutline = true
            outlineProvider = ViewOutlineProvider.BACKGROUND
        }
        GroupScope(card).build()
        parent.addView(card, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { setMargins(context.dp(16f), 0, context.dp(16f), 0) })
        return card
    }

    fun iconTile(icon: RowIcon, sizeDp: Float = ICON_TILE_DP): View {
        val base = palette.tint(icon.tint)
        val top = blend(base, Color.WHITE, 0.14f)
        val tile = FrameLayout(context).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(top, base)).apply {
                cornerRadius = context.dpF(sizeDp * 0.25f)
            }
        }
        tile.addView(ImageView(context).apply {
            setImageResource(icon.drawableRes)
            imageTintList = ColorStateList.valueOf(Color.WHITE)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, FrameLayout.LayoutParams(context.dp(sizeDp * 0.62f), context.dp(sizeDp * 0.62f), Gravity.CENTER))
        return tile
    }

    /** iOS 18/26 page header: large icon tile, bold title and a short description, centred in a card. */
    fun headerCard(parent: LinearLayout, icon: RowIcon, title: String, description: String) {
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = GradientDrawable().apply { setColor(palette.cell); cornerRadius = cardRadius }
            setPadding(context.dp(24f), context.dp(24f), context.dp(24f), context.dp(22f))
        }
        card.addView(iconTile(icon, 60f), LinearLayout.LayoutParams(context.dp(60f), context.dp(60f)))
        card.addView(TextView(context).apply {
            text = translate(title)
            textSize = 22f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            gravity = Gravity.CENTER
            setTextColor(palette.label)
            setPadding(0, context.dp(14f), 0, context.dp(6f))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        card.addView(TextView(context).apply {
            text = translate(description)
            textSize = 15f
            gravity = Gravity.CENTER
            setLineSpacing(context.dpF(2f), 1f)
            setTextColor(palette.secondaryLabel)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        parent.addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(context.dp(16f), context.dp(8f), context.dp(16f), 0)
        })
    }

    /** The account-style card at the top of the root page. */
    fun profileCard(parent: LinearLayout, iconRes: Int, title: String, subtitle: String, onClick: () -> Unit): TextView {
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = context.dp(84f)
            setPadding(context.dp(16f), context.dp(12f), context.dp(16f), context.dp(12f))
            background = GradientDrawable().apply { setColor(palette.cell); cornerRadius = cardRadius }
            clipToOutline = true
            outlineProvider = ViewOutlineProvider.BACKGROUND
            foreground = StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_pressed), ColorDrawable(if (palette.dark) 0x1FFFFFFF else 0x14000000))
                addState(intArrayOf(), ColorDrawable(Color.TRANSPARENT))
            }
            isClickable = true
            isFocusable = true
            contentDescription = translate(title)
            setOnClickListener { onClick() }
        }
        card.addView(ImageView(context).apply {
            setImageResource(iconRes)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: android.graphics.Outline) {
                    outline.setOval(0, 0, view.width, view.height)
                }
            }
            clipToOutline = true
        }, LinearLayout.LayoutParams(context.dp(60f), context.dp(60f)).apply { marginEnd = context.dp(14f) })
        val subtitleView = TextView(context).apply {
            text = translate(subtitle)
            textSize = 13f
            setTextColor(palette.secondaryLabel)
            setPadding(0, context.dp(2f), 0, 0)
        }
        card.addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(context).apply {
                text = translate(title)
                textSize = 20f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setTextColor(palette.label)
            })
            addView(subtitleView)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        card.addView(ImageView(context).apply {
            setImageResource(R.drawable.ic_chevron_forward)
            imageTintList = ColorStateList.valueOf(palette.tertiaryLabel)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(context.dp(14f), context.dp(18f)))
        parent.addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(context.dp(16f), context.dp(6f), context.dp(16f), 0)
        })
        return subtitleView
    }

    inner class GroupScope(private val card: LinearLayout) {
        private var rows = 0

        private fun add(row: View, separatorInsetDp: Float) {
            if (rows > 0) {
                card.addView(View(context).apply { setBackgroundColor(palette.separator) },
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1).apply {
                        marginStart = context.dp(separatorInsetDp)
                    })
            }
            card.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            rows++
        }

        private fun insetFor(icon: RowIcon?) = if (icon == null) 20f else 20f + ICON_TILE_DP + 14f

        private fun baseRow(icon: RowIcon?, minHeightDp: Float = ROW_HEIGHT_DP): LinearLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = context.dp(minHeightDp)
            setPadding(context.dp(20f), context.dp(8f), context.dp(16f), context.dp(8f))
            if (icon != null) {
                addView(iconTile(icon), LinearLayout.LayoutParams(context.dp(ICON_TILE_DP), context.dp(ICON_TILE_DP)).apply {
                    marginEnd = context.dp(14f)
                })
            }
        }

        private fun titleColumn(title: String, subtitle: String?, titleColor: Int = palette.label): Triple<LinearLayout, TextView, TextView> {
            val titleView = TextView(context).apply {
                text = translate(title)
                textSize = 17f
                setTextColor(titleColor)
            }
            val subtitleView = TextView(context).apply {
                textSize = 13f
                setLineSpacing(context.dpF(1.5f), 1f)
                setTextColor(palette.secondaryLabel)
                setPadding(0, context.dp(2f), 0, 0)
                if (subtitle.isNullOrBlank()) visibility = View.GONE else text = translate(subtitle)
            }
            val column = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(titleView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                addView(subtitleView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }
            return Triple(column, titleView, subtitleView)
        }

        fun switchRow(
            title: String,
            subtitle: String?,
            checked: Boolean,
            icon: RowIcon? = null,
            onChanged: (Boolean) -> Unit,
        ): RowHandle {
            titleSink?.invoke(title)
            val row = baseRow(icon)
            val (column, titleView, subtitleView) = titleColumn(title, subtitle)
            row.addView(column, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            val toggle = IosSwitch(context, palette).apply {
                isChecked = checked
                contentDescription = translate(title)
            }
            row.addView(toggle, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginStart = context.dp(12f)
            })
            val handle = RowHandle(row, titleView, null, subtitleView, toggle)
            toggle.setOnCheckedChangeListener { _, value -> if (!handle.isSuppressed()) onChanged(value) }
            makePressable(row)
            row.setOnClickListener { if (toggle.isEnabled) toggle.performClick() }
            // The switch carries the checkable semantics; the row is a bigger touch target only.
            row.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            add(row, insetFor(icon))
            return handle
        }

        fun navRow(
            title: String,
            subtitle: String? = null,
            value: String? = null,
            icon: RowIcon? = null,
            onClick: () -> Unit,
        ): RowHandle {
            titleSink?.invoke(title)
            val row = baseRow(icon)
            val (column, titleView, subtitleView) = titleColumn(title, subtitle)
            row.addView(column, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            val valueView = TextView(context).apply {
                textSize = 17f
                setTextColor(palette.secondaryLabel)
                gravity = Gravity.END
                maxLines = 2
                if (!value.isNullOrBlank()) text = translate(value)
            }
            row.addView(valueView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginStart = context.dp(8f)
            })
            row.addView(ImageView(context).apply {
                setImageResource(R.drawable.ic_chevron_forward)
                imageTintList = ColorStateList.valueOf(palette.tertiaryLabel)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(context.dp(14f), context.dp(18f)).apply { marginStart = context.dp(6f) })
            makePressable(row)
            row.contentDescription = translate(title)
            row.setOnClickListener { onClick() }
            add(row, insetFor(icon))
            return RowHandle(row, titleView, valueView, subtitleView)
        }

        /** Read-only title + trailing value, optionally with a wrapped subtitle. */
        fun valueRow(title: String, value: String, subtitle: String? = null, destructive: Boolean = false): RowHandle {
            titleSink?.invoke(title)
            val row = baseRow(null)
            val (column, titleView, subtitleView) = titleColumn(title, subtitle, if (destructive) palette.red else palette.label)
            row.addView(column, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            val valueView = TextView(context).apply {
                text = translate(value)
                textSize = 17f
                gravity = Gravity.END
                setTextColor(palette.secondaryLabel)
            }
            row.addView(valueView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginStart = context.dp(12f)
            })
            row.isFocusable = true
            add(row, insetFor(null))
            return RowHandle(row, titleView, valueView, subtitleView)
        }

        fun actionRow(title: String, destructive: Boolean = false, onClick: () -> Unit): RowHandle {
            val row = baseRow(null)
            val label = TextView(context).apply {
                text = translate(title)
                textSize = 17f
                gravity = Gravity.CENTER
                setTextColor(if (destructive) palette.red else palette.accent)
            }
            row.addView(label, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            makePressable(row)
            row.contentDescription = translate(title)
            row.setOnClickListener { onClick() }
            add(row, insetFor(null))
            return RowHandle(row, label)
        }

        fun segmentedRow(title: String, labels: List<String>, selected: Int, onSelected: (Int) -> Unit): RowHandle {
            titleSink?.invoke(title)
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(context.dp(20f), context.dp(12f), context.dp(16f), context.dp(14f))
            }
            val titleView = TextView(context).apply {
                text = translate(title)
                textSize = 17f
                setTextColor(palette.label)
            }
            row.addView(titleView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            val segmented = IosSegmentedControl(context, palette, labels.map(translate), selected, onSelected)
            row.addView(segmented, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, context.dp(34f)).apply {
                topMargin = context.dp(10f)
            })
            add(row, insetFor(null))
            return RowHandle(row, titleView)
        }

        fun sliderRow(
            label: String,
            value: Float,
            defaultValue: Float,
            range: ClosedFloatingPointRange<Float>,
            suffix: String = "",
            decimals: Int = 1,
            enabled: Boolean = true,
            onTrackingChanged: ((Boolean) -> Unit)? = null,
            onValueChange: (Float) -> Unit,
        ): RowHandle {
            titleSink?.invoke(label)
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(context.dp(20f), context.dp(12f), context.dp(16f), context.dp(4f))
            }
            val header = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val titleView = TextView(context).apply {
                text = translate(label)
                textSize = 17f
                setTextColor(palette.label)
            }
            header.addView(titleView, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            val valueView = TextView(context).apply {
                textSize = 17f
                gravity = Gravity.END
            }
            header.addView(valueView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginStart = context.dp(8f)
            })
            row.addView(header, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

            val slider = IosSlider(context, palette).apply {
                setRange(range.start, range.endInclusive, decimals)
                setValue(value)
                contentDescription = "${translate(label)} ${translate("调节")}"
            }
            row.addView(slider, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, context.dp(40f)))

            val restored = snapToSliderStep(defaultValue, range, decimals)
            fun refreshLabels(current: Float) {
                valueView.text = formatSliderValue(current, suffix, decimals)
                // A tinted value marks a parameter that differs from its default
                // (long-press the row to restore it), like iOS edited values.
                valueView.setTextColor(
                    if (isParameterModified(current, defaultValue, decimals)) palette.accent else palette.secondaryLabel,
                )
            }
            refreshLabels(slider.value)
            slider.onUserChange = { next ->
                refreshLabels(next)
                onValueChange(next)
            }
            slider.onTrackingChanged = onTrackingChanged
            val reset = {
                slider.setValue(restored)
                refreshLabels(restored)
                onValueChange(restored)
            }
            row.isLongClickable = true
            row.setOnLongClickListener { anchor ->
                val presenter = sliderResetPresenter ?: return@setOnLongClickListener false
                anchor.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                presenter(anchor, translate(label), formatSliderValue(restored, suffix, decimals), reset)
                true
            }
            add(row, insetFor(null))
            val handle = RowHandle(row, titleView, valueView, null, null, slider)
            if (!enabled) row.setRowEnabled(false)
            return handle
        }

        fun customRow(view: View) = add(view, insetFor(null))
    }

    private fun makePressable(row: View) {
        row.isClickable = true
        row.isFocusable = true
        row.background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), ColorDrawable(palette.cellPressed))
            addState(intArrayOf(), ColorDrawable(Color.TRANSPARENT))
        }
    }

    private fun blend(from: Int, to: Int, t: Float): Int = Color.rgb(
        (Color.red(from) + (Color.red(to) - Color.red(from)) * t).toInt(),
        (Color.green(from) + (Color.green(to) - Color.green(from)) * t).toInt(),
        (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * t).toInt(),
    )

    companion object {
        const val CARD_RADIUS_DP = 26f
        const val ROW_HEIGHT_DP = 52f
        const val ICON_TILE_DP = 30f
    }
}
