package com.cxcboss.glassorb.ui.ios

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.widget.CompoundButton
import android.widget.Switch
import kotlin.math.roundToInt

/**
 * iOS 26 style toggle. The thumb is a wide capsule that stretches while the
 * finger is down and travels on a spring; the track blends gray to green.
 * Drawing is custom, but semantics come from [CompoundButton], so TalkBack,
 * state restoration and click handling behave like a stock switch.
 */
internal class IosSwitch(context: Context, private val palette: IosPalette) : CompoundButton(context) {
    private val trackRect = RectF()
    private val thumbRect = RectF()
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbEdgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dpF(0.5f)
    }
    private var ready = false
    private var progress = 0f
    private var pressAmount = 0f
    private val travel = UiSpring(0.34f, 0.72f, 0f) { value, _ ->
        progress = value
        invalidate()
    }
    private val press = UiSpring(0.26f, 0.8f, 0f) { value, _ ->
        pressAmount = value
        invalidate()
    }

    init {
        buttonDrawable = null
        background = null
        isClickable = true
        isFocusable = true
        thumbPaint.setShadowLayer(dpF(3f), 0f, dpF(1.5f), 0x38000000)
        thumbEdgePaint.color = if (palette.dark) 0x1AFFFFFF else 0x14000000
        ready = true
    }

    override fun setChecked(checked: Boolean) {
        val changed = isChecked != checked
        super.setChecked(checked)
        // CompoundButton's own constructor calls this before our fields exist.
        if (!ready) return
        val target = if (checked) 1f else 0f
        if (changed && isLaidOut) travel.animateTo(target) else travel.snapTo(target)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            resolveSize(dp(TRACK_WIDTH_DP + 4f), widthMeasureSpec),
            resolveSize(dp(TRACK_HEIGHT_DP + 8f), heightMeasureSpec),
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isEnabled) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> press.animateTo(1f)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> press.animateTo(0f)
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        val trackW = dpF(TRACK_WIDTH_DP)
        val trackH = dpF(TRACK_HEIGHT_DP)
        val left = (width - trackW) / 2f
        val top = (height - trackH) / 2f
        trackRect.set(left, top, left + trackW, top + trackH)
        trackPaint.color = blend(palette.track, palette.green, progress.coerceIn(0f, 1f))
        canvas.drawRoundRect(trackRect, trackH / 2f, trackH / 2f, trackPaint)

        val margin = dpF(2f)
        val thumbH = trackH - margin * 2f
        val thumbW = dpF(THUMB_WIDTH_DP) + dpF(STRETCH_DP) * pressAmount
        val minX = trackRect.left + margin
        val maxX = trackRect.right - margin - thumbW
        val x = minX + (maxX - minX) * progress
        thumbRect.set(x, trackRect.top + margin, x + thumbW, trackRect.top + margin + thumbH)
        thumbPaint.color = Color.WHITE
        // A pressed glass thumb lets a little of the track show through.
        thumbPaint.alpha = (255f - 28f * pressAmount).roundToInt()
        canvas.drawRoundRect(thumbRect, thumbH / 2f, thumbH / 2f, thumbPaint)
        canvas.drawRoundRect(thumbRect, thumbH / 2f, thumbH / 2f, thumbEdgePaint)
    }

    override fun performClick(): Boolean = super.performClick()

    override fun getAccessibilityClassName(): CharSequence = Switch::class.java.name

    override fun onDetachedFromWindow() {
        travel.cancel()
        press.cancel()
        super.onDetachedFromWindow()
    }

    private fun blend(from: Int, to: Int, t: Float): Int = Color.argb(
        lerp(Color.alpha(from), Color.alpha(to), t),
        lerp(Color.red(from), Color.red(to), t),
        lerp(Color.green(from), Color.green(to), t),
        lerp(Color.blue(from), Color.blue(to), t),
    )

    private fun lerp(a: Int, b: Int, t: Float): Int = (a + (b - a) * t).roundToInt().coerceIn(0, 255)

    private companion object {
        const val TRACK_WIDTH_DP = 60f
        const val TRACK_HEIGHT_DP = 30f
        const val THUMB_WIDTH_DP = 36f
        const val STRETCH_DP = 6f
    }
}

/** Disables a row and everything inside it while dimming it exactly once. */
internal fun View.setRowEnabled(enabled: Boolean) {
    setEnabledDeep(enabled)
    alpha = if (enabled) 1f else 0.4f
}

private fun View.setEnabledDeep(enabled: Boolean) {
    isEnabled = enabled
    if (this is android.view.ViewGroup) {
        for (index in 0 until childCount) getChildAt(index).setEnabledDeep(enabled)
    }
}
