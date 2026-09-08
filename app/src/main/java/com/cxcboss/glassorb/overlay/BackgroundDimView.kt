package com.cxcboss.glassorb.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.view.View

/**
 * A static, non-touchable screen-top dim layer shown only while the orb is
 * expanded. It lives in its own overlay window below the GL surface so the
 * glass shader never has to carry a full-screen background texture.
 */
internal class BackgroundDimView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var gradientHeightPx = 0
    private var enabled = false

    fun update(enabled: Boolean, gradientHeightPx: Int) {
        val nextHeight = gradientHeightPx.coerceAtLeast(0)
        if (this.enabled == enabled && this.gradientHeightPx == nextHeight) return
        this.enabled = enabled
        this.gradientHeightPx = nextHeight
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (!enabled || gradientHeightPx <= 0 || width <= 0) return
        paint.shader = LinearGradient(
            0f,
            0f,
            0f,
            gradientHeightPx.toFloat(),
            0x33000000,
            0x00000000,
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, width.toFloat(), gradientHeightPx.toFloat(), paint)
    }
}
