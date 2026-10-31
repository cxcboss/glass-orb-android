package com.cxcboss.glassorb.ui.ios

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityNodeInfo
import com.cxcboss.glassorb.ui.SliderGestureAxis
import com.cxcboss.glassorb.ui.shouldCommitSliderTap
import com.cxcboss.glassorb.ui.sliderGestureAxis
import com.cxcboss.glassorb.ui.sliderValueAt
import com.cxcboss.glassorb.ui.snapToSliderStep
import kotlin.math.abs

/**
 * iOS 26 style slider: a thick rounded track with an accent fill and a capsule
 * thumb that swells while it is held. A tap on the track moves the thumb; a
 * horizontal drag takes ownership of the gesture so the page does not scroll.
 * Vertical movement is left to the parent ScrollView.
 */
internal class IosSlider(context: Context, private val palette: IosPalette) : View(context) {
    var valueFrom = 0f
    var valueTo = 1f
    var decimals = 1

    /** Fires for user gestures only, with the value already snapped to [decimals]. */
    var onUserChange: ((Float) -> Unit)? = null
    var onTrackingChanged: ((Boolean) -> Unit)? = null

    var value = 0f
        private set

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbEdgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dpF(0.5f)
    }
    private val rect = RectF()
    private val slop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private var downX = 0f
    private var downY = 0f
    private var dragging = false
    private var cancelled = false
    private var swell = 0f
    private val swellSpring = UiSpring(0.28f, 0.62f, 0f) { v, _ ->
        swell = v
        invalidate()
    }

    init {
        thumbPaint.color = android.graphics.Color.WHITE
        thumbPaint.setShadowLayer(dpF(3.5f), 0f, dpF(1.5f), 0x40000000)
        thumbEdgePaint.color = if (palette.dark) 0x1AFFFFFF else 0x14000000
        trackPaint.color = palette.track
        fillPaint.color = palette.accent
        isClickable = true
        isFocusable = true
    }

    fun setRange(from: Float, to: Float, decimals: Int) {
        valueFrom = from
        valueTo = to
        this.decimals = decimals
    }

    fun setValue(next: Float) {
        value = snapToSliderStep(next, valueFrom..valueTo, decimals)
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            resolveSize(dp(160f), widthMeasureSpec),
            resolveSize(dp(44f), heightMeasureSpec),
        )
    }

    override fun onDraw(canvas: Canvas) {
        val trackH = dpF(TRACK_HEIGHT_DP)
        val centerY = height / 2f
        val thumbW = thumbWidth()
        val thumbH = dpF(THUMB_HEIGHT_DP) + dpF(THUMB_GROW_DP) * swell
        val startX = thumbHalfIdle()
        val endX = width - thumbHalfIdle()
        val fraction = if (valueTo > valueFrom) ((value - valueFrom) / (valueTo - valueFrom)).coerceIn(0f, 1f) else 0f
        val thumbCenter = startX + (endX - startX) * fraction

        rect.set(startX - dpF(4f), centerY - trackH / 2f, endX + dpF(4f), centerY + trackH / 2f)
        canvas.drawRoundRect(rect, trackH / 2f, trackH / 2f, trackPaint)
        rect.set(startX - dpF(4f), centerY - trackH / 2f, thumbCenter, centerY + trackH / 2f)
        canvas.drawRoundRect(rect, trackH / 2f, trackH / 2f, fillPaint)

        rect.set(thumbCenter - thumbW / 2f, centerY - thumbH / 2f, thumbCenter + thumbW / 2f, centerY + thumbH / 2f)
        canvas.drawRoundRect(rect, thumbH / 2f, thumbH / 2f, thumbPaint)
        canvas.drawRoundRect(rect, thumbH / 2f, thumbH / 2f, thumbEdgePaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                dragging = false
                cancelled = false
                swellSpring.animateTo(1f)
            }

            MotionEvent.ACTION_MOVE -> {
                if (!dragging) {
                    when (sliderGestureAxis(event.x - downX, event.y - downY, slop)) {
                        SliderGestureAxis.Horizontal -> {
                            dragging = true
                            parent?.requestDisallowInterceptTouchEvent(true)
                            onTrackingChanged?.invoke(true)
                        }
                        SliderGestureAxis.Vertical -> {
                            cancelled = true
                            swellSpring.animateTo(0f)
                            return false
                        }
                        SliderGestureAxis.Undecided -> Unit
                    }
                }
                if (dragging) commit(valueAt(event.x))
            }

            MotionEvent.ACTION_UP -> {
                val moved = abs(event.x - downX) > slop || abs(event.y - downY) > slop
                if (shouldCommitSliderTap(dragging = dragging, pressed = moved, consumed = cancelled)) {
                    commit(valueAt(event.x))
                    performClick()
                }
                finishGesture()
            }

            MotionEvent.ACTION_CANCEL -> finishGesture()
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    private fun finishGesture() {
        if (dragging) onTrackingChanged?.invoke(false)
        dragging = false
        swellSpring.animateTo(0f)
    }

    private fun commit(raw: Float) {
        val snapped = snapToSliderStep(raw, valueFrom..valueTo, decimals)
        if (snapped == value) return
        value = snapped
        invalidate()
        onUserChange?.invoke(snapped)
    }

    private fun valueAt(x: Float): Float {
        val half = thumbHalfIdle()
        val rtl = layoutDirection == LAYOUT_DIRECTION_RTL
        return sliderValueAt(x - half, (width - half * 2f).coerceAtLeast(1f), valueFrom..valueTo, rtl)
    }

    private fun thumbWidth(): Float = dpF(THUMB_WIDTH_DP) + dpF(THUMB_GROW_DP) * 1.3f * swell

    private fun thumbHalfIdle(): Float = dpF(THUMB_WIDTH_DP) / 2f + dpF(4f)

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = "android.widget.SeekBar"
        info.rangeInfo = AccessibilityNodeInfo.RangeInfo.obtain(
            AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_FLOAT, valueFrom, valueTo, value,
        )
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS)
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD)
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)
    }

    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean {
        val step = 1f / Math.pow(10.0, decimals.toDouble()).toFloat()
        val coarse = (valueTo - valueFrom) / 20f
        when (action) {
            AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id -> {
                val requested = arguments?.getFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE) ?: return false
                commit(requested)
                return true
            }
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD -> { commit(value + maxOf(step, coarse)); return true }
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD -> { commit(value - maxOf(step, coarse)); return true }
        }
        return super.performAccessibilityAction(action, arguments)
    }

    override fun onDetachedFromWindow() {
        swellSpring.cancel()
        super.onDetachedFromWindow()
    }

    private companion object {
        const val TRACK_HEIGHT_DP = 6f
        const val THUMB_WIDTH_DP = 34f
        const val THUMB_HEIGHT_DP = 22f
        const val THUMB_GROW_DP = 6f
    }
}
