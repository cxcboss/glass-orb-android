package com.cxcboss.glassorb.ui.glass

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import com.cxcboss.glassorb.R
import com.cxcboss.glassorb.ui.ios.IosPalette
import com.cxcboss.glassorb.ui.ios.dp

/**
 * iOS 26 bottom search: a floating Liquid Glass capsule. While active a round
 * glass close button appears beside it and the keyboard lifts the whole bar.
 */
internal class GlassSearchBar(
    context: Context,
    private val palette: IosPalette,
    backdrop: GlassBackdrop,
    hint: String,
    closeDescription: String,
    private val onQueryChanged: (String) -> Unit,
    private val onActiveChanged: (Boolean) -> Unit,
) : LinearLayout(context) {
    private val field = LiquidGlassView(context, palette).apply {
        this.backdrop = backdrop
        glassInsetPx = dp(INSET_DP)
        blurDp = 12f
        lensDp = 10f
        bezelDp = 14f
        interactive = true
    }
    private val input = EditText(context).apply {
        this.hint = hint
        background = null
        textSize = 17f
        isSingleLine = true
        setTextColor(palette.label)
        setHintTextColor(palette.secondaryLabel)
        imeOptions = EditorInfo.IME_ACTION_SEARCH
        setPadding(0, 0, 0, 0)
        contentDescription = hint
    }
    private val close = LiquidGlassView(context, palette).apply {
        this.backdrop = backdrop
        glassInsetPx = dp(INSET_DP)
        blurDp = 12f
        lensDp = 8f
        bezelDp = 10f
        interactive = true
        isClickable = true
        isFocusable = true
        contentDescription = closeDescription
        visibility = View.GONE
    }
    var active = false
        private set

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        clipChildren = false
        val content = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16f), 0, dp(16f), 0)
        }
        content.addView(ImageView(context).apply {
            setImageResource(R.drawable.ic_search)
            imageTintList = ColorStateList.valueOf(palette.secondaryLabel)
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LayoutParams(dp(20f), dp(20f)).apply { marginEnd = dp(10f) })
        content.addView(input, LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        field.addView(content, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val height = dp(BAR_HEIGHT_DP + INSET_DP * 2)
        addView(field, LayoutParams(0, height, 1f))
        close.addView(ImageView(context).apply {
            setImageResource(R.drawable.ic_close)
            imageTintList = ColorStateList.valueOf(palette.label)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
        }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        close.setOnClickListener { deactivate() }
        addView(close, LayoutParams(height, height).apply { marginStart = -dp(INSET_DP) })

        field.setOnClickListener { input.requestFocus(); showKeyboard() }
        input.setOnFocusChangeListener { _, focused -> if (focused) setActive(true) }
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = onQueryChanged(s?.toString().orEmpty())
        })
        input.setOnEditorActionListener { _, actionId, event ->
            val search = actionId == EditorInfo.IME_ACTION_SEARCH ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_UP)
            if (search) hideKeyboard()
            search
        }
        setBackgroundColor(Color.TRANSPARENT)
    }

    /** Leaves search: clears the query, hides the keyboard and the close button. */
    fun deactivate() {
        if (!active) return
        input.text?.clear()
        input.clearFocus()
        hideKeyboard()
        setActive(false)
    }

    private fun setActive(value: Boolean) {
        if (active == value) return
        active = value
        close.visibility = if (value) View.VISIBLE else View.GONE
        onActiveChanged(value)
    }

    private fun showKeyboard() {
        context.getSystemService(InputMethodManager::class.java)?.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideKeyboard() {
        context.getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(input.windowToken, 0)
    }

    companion object {
        const val BAR_HEIGHT_DP = 48f
        const val INSET_DP = 8f
    }
}
