package com.cxcboss.glassorb.ui.ios

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import kotlin.math.abs

/** iOS segmented control with a sliding white pill. */
internal class IosSegmentedControl(
    context: Context,
    private val palette: IosPalette,
    labels: List<String>,
    selected: Int,
    private val onSelected: (Int) -> Unit,
) : FrameLayout(context) {
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.segmentTrack }
    private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (palette.dark) 0xFF636366.toInt() else Color.WHITE
        setShadowLayer(dpF(3f), 0f, dpF(1.5f), 0x33000000)
    }
    private val rect = RectF()
    private val labelViews = ArrayList<TextView>()
    private var position = selected.toFloat()
    private var selectedIndex = selected
    private val spring = UiSpring(0.3f, 0.78f, selected.toFloat()) { value, _ ->
        position = value
        invalidate()
    }

    init {
        setWillNotDraw(false)
        val inset = dp(2f)
        setPadding(inset, inset, inset, inset)
        labels.forEachIndexed { index, text ->
            val label = TextView(context).apply {
                this.text = text
                textSize = 14f
                gravity = Gravity.CENTER
                maxLines = 1
                setTextColor(palette.label)
                isClickable = true
                setOnClickListener { select(index, notify = true) }
                contentDescription = text
            }
            labelViews.add(label)
            addView(label, LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        styleLabels()
    }

    fun select(index: Int, notify: Boolean) {
        if (index == selectedIndex) return
        selectedIndex = index
        spring.animateTo(index.toFloat())
        styleLabels()
        if (notify) onSelected(index)
    }

    private fun styleLabels() {
        labelViews.forEachIndexed { index, label ->
            label.typeface = android.graphics.Typeface.create(
                "sans-serif-medium".takeIf { index == selectedIndex } ?: "sans-serif",
                android.graphics.Typeface.NORMAL,
            )
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = dp(34f)
        val inner = (width - paddingLeft - paddingRight).coerceAtLeast(0)
        val each = if (labelViews.isEmpty()) 0 else inner / labelViews.size
        labelViews.forEach { child ->
            child.measure(
                MeasureSpec.makeMeasureSpec(each, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height - paddingTop - paddingBottom, MeasureSpec.EXACTLY),
            )
        }
        setMeasuredDimension(width, height)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val each = if (labelViews.isEmpty()) 0 else (r - l - paddingLeft - paddingRight) / labelViews.size
        labelViews.forEachIndexed { index, child ->
            val left = paddingLeft + each * index
            child.layout(left, paddingTop, left + each, b - t - paddingBottom)
        }
    }

    override fun onDraw(canvas: Canvas) {
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(rect, height / 2f, height / 2f, trackPaint)
        if (labelViews.isEmpty()) return
        val each = (width - paddingLeft - paddingRight).toFloat() / labelViews.size
        val stretch = abs(spring.velocity) * dpF(1.2f)
        val left = paddingLeft + each * position - stretch / 2f
        rect.set(left, paddingTop.toFloat(), left + each + stretch, (height - paddingBottom).toFloat())
        canvas.drawRoundRect(rect, rect.height() / 2f, rect.height() / 2f, pillPaint)
    }

    override fun onDetachedFromWindow() {
        spring.cancel()
        super.onDetachedFromWindow()
    }
}
