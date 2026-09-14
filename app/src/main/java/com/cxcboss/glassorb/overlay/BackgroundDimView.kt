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
    private var gradient: LinearGradient? = null
    private var displayRotation = 0

    init { alpha = 0f }

    fun update(enabled: Boolean, gradientHeightPx: Int, fadeDurationMs: Long, rotation: Int = 0) {
        val rotationChanged = displayRotation != rotation
        displayRotation = rotation
        if (rotationChanged) gradient = null
        val nextHeight = gradientHeightPx.coerceAtLeast(0)
        if (!rotationChanged && this.enabled == enabled && this.gradientHeightPx == nextHeight) return
        val visibilityChanged = this.enabled != enabled
        val heightChanged = this.gradientHeightPx != nextHeight
        this.enabled = enabled
        this.gradientHeightPx = nextHeight
        if (heightChanged) gradient = null
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

    fun hideImmediately() {
        fadeAnimator?.cancel()
        fadeAnimator = null
        enabled = false
        alpha = 0f
    }

    override fun onDraw(canvas: Canvas) {
        if (gradientHeightPx <= 0 || width <= 0) return
        if (gradient == null) {
            val distance = gradientHeightPx.toFloat()
            gradient = LinearGradient(
                if (displayRotation == 3) width.toFloat() else 0f,
                if (displayRotation == 2) height.toFloat() else 0f,
                when (displayRotation) { 1 -> distance; 3 -> width - distance; else -> 0f },
                when (displayRotation) { 0 -> distance; 2 -> height - distance; else -> 0f },
                0xFF000000.toInt(),
                0x00000000,
                Shader.TileMode.CLAMP,
            )
        }
        paint.shader = gradient
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        gradient = null
    }

    override fun onDetachedFromWindow() {
        fadeAnimator?.cancel()
        fadeAnimator = null
        super.onDetachedFromWindow()
    }
}
