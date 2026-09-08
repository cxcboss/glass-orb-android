package com.cxcboss.glassorb.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.view.View
import android.animation.ValueAnimator
import android.view.animation.DecelerateInterpolator

/**
 * A static, non-touchable screen-top dim layer shown only while the orb is
 * expanded. It lives in its own overlay window below the GL surface so the
 * glass shader never has to carry a full-screen background texture.
 */
internal class BackgroundDimView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var gradientHeightPx = 0
    private var enabled = false
    private var fadeAnimator: ValueAnimator? = null

    init { alpha = 0f }

    fun update(enabled: Boolean, gradientHeightPx: Int, fadeDurationMs: Long) {
        val nextHeight = gradientHeightPx.coerceAtLeast(0)
        if (this.enabled == enabled && this.gradientHeightPx == nextHeight) return
        val visibilityChanged = this.enabled != enabled
        this.enabled = enabled
        this.gradientHeightPx = nextHeight
        if (!visibilityChanged) {
            invalidate()
            return
        }
        fadeAnimator?.cancel()
        val target = if (enabled) 1f else 0f
        fadeAnimator = ValueAnimator.ofFloat(alpha, target).apply {
            duration = fadeDurationMs.coerceIn(300L, 900L)
            interpolator = DecelerateInterpolator()
            addUpdateListener { alpha = it.animatedValue as Float }
            start()
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (gradientHeightPx <= 0 || width <= 0) return
        paint.shader = LinearGradient(
            0f,
            0f,
            0f,
            gradientHeightPx.toFloat(),
            0xFF000000.toInt(),
            0x00000000,
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, width.toFloat(), gradientHeightPx.toFloat(), paint)
    }

    override fun onDetachedFromWindow() {
        fadeAnimator?.cancel()
        fadeAnimator = null
        super.onDetachedFromWindow()
    }
}
