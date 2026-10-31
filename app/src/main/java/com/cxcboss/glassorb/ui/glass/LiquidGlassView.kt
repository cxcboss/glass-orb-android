package com.cxcboss.glassorb.ui.glass

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.os.Build
import android.view.MotionEvent
import android.widget.FrameLayout
import androidx.annotation.RequiresApi
import com.cxcboss.glassorb.ui.ios.IosPalette
import com.cxcboss.glassorb.ui.ios.UiSpring
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A Liquid Glass surface: live backdrop blur + saturation, an optional AGSL
 * rim lens, a translucent tint, a specular rim and a soft drop shadow.
 *
 * The visible glass shape is [glassInsetPx] smaller than the view on every side
 * so the shadow has room to render without clipping. Children are laid out
 * inside the padding, so callers can treat the inset as a margin.
 */
internal class LiquidGlassView(
    context: Context,
    private val palette: IosPalette,
) : FrameLayout(context) {
    var backdrop: GlassBackdrop? = null
        set(value) {
            if (field === value) return
            field?.detach(this)
            field = value
            if (isAttachedToWindow) value?.attach(this)
            invalidate()
        }

    /** Corner radius in px; a negative value means a full pill / circle. */
    var cornerRadiusPx = -1f
        set(value) { field = value; rebuildGeometry(); invalidate() }

    var glassInsetPx = 0
        set(value) {
            field = value
            setPadding(value, value, value, value)
            rebuildGeometry()
            invalidate()
        }

    /** Overall opacity of the glass material (not of its children). */
    var glassAlpha = 1f
        set(value) {
            val next = value.coerceIn(0f, 1f)
            if (field == next) return
            field = next
            invalidate()
        }

    var blurDp = 10f
    var lensDp = 12f
    var bezelDp = 14f
    var dispersion = 0.06f
    var rimEnabled = true
    var shadowEnabled = true
    var sheenEnabled = true

    /** When true the whole surface scales up and glows under the finger. */
    var interactive = false

    /**
     * iOS 26 "scroll edge" treatment: the material fades out toward the content
     * instead of ending in a hard bar edge. [EDGE_TOP] is opaque at the top.
     */
    var edgeFade = EDGE_NONE
        set(value) { field = value; rebuildGeometry(); invalidate() }

    /** Length of the transparent-going ramp for [edgeFade]; the rest stays solid. */
    var edgeFadeLengthPx = 0f
        set(value) { field = value; rebuildGeometry(); invalidate() }

    /** Overrides the material tint (e.g. scroll edges use the page colour). */
    var tintOverride: Int? = null

    private val fadePaint = Paint().apply {
        xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN)
    }

    private val shapeRect = RectF()
    private val shapePath = Path()
    private val tintPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowMatrix = Matrix()
    // One white-to-clear radial gradient, moved under the finger with a matrix.
    private val glowShader = RadialGradient(
        0f, 0f, 96f * context.resources.displayMetrics.density,
        intArrayOf(0x4DFFFFFF, Color.TRANSPARENT),
        null,
        Shader.TileMode.CLAMP,
    )
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val rimRect = RectF()
    private val rimPath = Path()
    private val location = IntArray(2)
    private val sourceLocation = IntArray(2)

    private val node = RenderNode("LiquidGlass")
    private var appliedEffectKey = 0L
    private var lensShader: Any? = null
    private var lensBroken = false

    private var touchX = 0f
    private var touchY = 0f
    private var press = 0f
    private val pressSpring = UiSpring(0.34f, 0.62f, 0f) { value, _ ->
        press = value
        if (interactive) {
            scaleX = 1f + PRESS_SCALE * value
            scaleY = scaleX
        }
        invalidate()
    }

    init {
        setWillNotDraw(false)
        clipChildren = false
        clipToPadding = false
    }

    fun invalidateBackdrop() {
        if (visibility == VISIBLE && glassAlpha > 0.003f) invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        backdrop?.attach(this)
    }

    override fun onDetachedFromWindow() {
        backdrop?.detach(this)
        pressSpring.cancel()
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuildGeometry()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (interactive) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    touchX = event.x
                    touchY = event.y
                    pressSpring.animateTo(1f)
                }
                MotionEvent.ACTION_MOVE -> {
                    touchX = event.x
                    touchY = event.y
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> pressSpring.animateTo(0f)
            }
        }
        return super.dispatchTouchEvent(event)
    }

    override fun draw(canvas: Canvas) {
        if (edgeFade == EDGE_NONE || width == 0 || height == 0) {
            super.draw(canvas)
            return
        }
        val save = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
        super.draw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fadePaint)
        canvas.restoreToCount(save)
    }

    override fun onDraw(canvas: Canvas) {
        val alpha = glassAlpha
        if (alpha <= 0.003f || shapeRect.isEmpty) return
        val radius = effectiveRadius()
        if (shadowEnabled) {
            canvas.save()
            canvas.clipOutPath(shapePath)
            shadowPaint.alpha = (alpha * 255f).roundToInt()
            canvas.drawRoundRect(shapeRect, radius, radius, shadowPaint)
            canvas.restore()
        }
        canvas.save()
        canvas.clipPath(shapePath)
        val capturedBackdrop = drawBackdrop(canvas, alpha, radius)
        tintPaint.color = tintOverride ?: if (capturedBackdrop) palette.glassTint else palette.glassFallback
        tintPaint.alpha = (Color.alpha(tintPaint.color) * alpha).roundToInt()
        canvas.drawRect(shapeRect, tintPaint)
        if (sheenEnabled) {
            sheenPaint.alpha = (255f * alpha).roundToInt()
            canvas.drawRect(shapeRect, sheenPaint)
        }
        if (press > 0.01f && interactive) {
            glowMatrix.setTranslate(touchX, touchY)
            glowShader.setLocalMatrix(glowMatrix)
            glowPaint.shader = glowShader
            glowPaint.alpha = (255f * press).roundToInt()
            canvas.drawRect(shapeRect, glowPaint)
        }
        canvas.restore()
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        val alpha = glassAlpha
        if (!rimEnabled || alpha <= 0.003f || shapeRect.isEmpty) return
        rimPaint.alpha = (255f * alpha).roundToInt()
        edgePaint.alpha = (255f * alpha).roundToInt()
        canvas.drawPath(rimPath, edgePaint)
        canvas.drawPath(rimPath, rimPaint)
    }

    private fun effectiveRadius(): Float {
        val maxRadius = min(shapeRect.width(), shapeRect.height()) * 0.5f
        return if (cornerRadiusPx < 0f) maxRadius else min(cornerRadiusPx, maxRadius)
    }

    private fun rebuildGeometry() {
        val inset = glassInsetPx.toFloat()
        shapeRect.set(inset, inset, width - inset, height - inset)
        if (shapeRect.isEmpty) return
        val radius = effectiveRadius()
        shapePath.rewind()
        shapePath.addRoundRect(shapeRect, radius, radius, Path.Direction.CW)

        val density = resources.displayMetrics.density
        val strokeWidth = 1.2f * density
        rimPaint.strokeWidth = strokeWidth
        edgePaint.strokeWidth = strokeWidth * 0.8f
        rimRect.set(shapeRect)
        rimRect.inset(strokeWidth * 0.5f, strokeWidth * 0.5f)
        val rimRadius = (radius - strokeWidth * 0.5f).coerceAtLeast(0f)
        rimPath.rewind()
        rimPath.addRoundRect(rimRect, rimRadius, rimRadius, Path.Direction.CW)
        rimPaint.shader = LinearGradient(
            shapeRect.left, shapeRect.top, shapeRect.right, shapeRect.bottom,
            intArrayOf(palette.glassRimHigh, palette.glassRimLow, palette.glassRimLow, withAlpha(palette.glassRimHigh, 0.55f)),
            floatArrayOf(0f, 0.34f, 0.66f, 1f),
            Shader.TileMode.CLAMP,
        )
        edgePaint.color = if (palette.dark) 0x33000000 else 0x0D000000
        edgePaint.style = Paint.Style.STROKE
        sheenPaint.shader = LinearGradient(
            0f, shapeRect.top, 0f, shapeRect.bottom,
            intArrayOf(withAlpha(Color.WHITE, if (palette.dark) 0.10f else 0.34f), Color.TRANSPARENT),
            floatArrayOf(0f, 0.62f),
            Shader.TileMode.CLAMP,
        )
        val ramp = if (edgeFadeLengthPx > 0f && height > 0) (edgeFadeLengthPx / height).coerceIn(0.05f, 1f) else 0.5f
        fadePaint.shader = when (edgeFade) {
            EDGE_TOP -> LinearGradient(0f, 0f, 0f, height.toFloat(),
                intArrayOf(Color.BLACK, Color.BLACK, Color.TRANSPARENT), floatArrayOf(0f, 1f - ramp, 1f), Shader.TileMode.CLAMP)
            EDGE_BOTTOM -> LinearGradient(0f, 0f, 0f, height.toFloat(),
                intArrayOf(Color.TRANSPARENT, Color.BLACK, Color.BLACK), floatArrayOf(0f, ramp, 1f), Shader.TileMode.CLAMP)
            else -> null
        }
        shadowPaint.color = Color.BLACK
        // The blur must fit inside the transparent inset or it is cut off at the view bounds.
        val shadowRadius = min(9f * density, glassInsetPx * 0.85f).coerceAtLeast(1f)
        shadowPaint.setShadowLayer(shadowRadius, 0f, min(3.5f * density, glassInsetPx * 0.3f), palette.glassShadow)
        appliedEffectKey = 0L
    }

    /**
     * Records the backdrop under the glass into a RenderNode and lets the
     * compositor blur (and, on API 33+, refract) it. Returns false when the
     * platform cannot do this, in which case the caller paints a flat tint.
     */
    private fun drawBackdrop(canvas: Canvas, alpha: Float, radius: Float): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        val source = backdrop?.source ?: return false
        if (!canvas.isHardwareAccelerated || !source.isAttachedToWindow) return false
        val density = resources.displayMetrics.density
        val blurPx = blurDp * density
        val pad = (blurPx * 2f).roundToInt().coerceAtLeast(1)
        val glassW = shapeRect.width().roundToInt()
        val glassH = shapeRect.height().roundToInt()
        if (glassW <= 0 || glassH <= 0) return false
        val nodeW = glassW + pad * 2
        val nodeH = glassH + pad * 2

        getLocationInWindow(location)
        source.getLocationInWindow(sourceLocation)
        val offsetX = location[0] - sourceLocation[0] + shapeRect.left - pad
        val offsetY = location[1] - sourceLocation[1] + shapeRect.top - pad

        node.setPosition(0, 0, nodeW, nodeH)
        val recording = node.beginRecording(nodeW, nodeH)
        try {
            // An opaque base keeps the blur from fading to transparent where
            // the padded capture area extends past the edge of the screen.
            recording.drawColor(palette.groupedBackground)
            recording.translate(-offsetX, -offsetY)
            source.draw(recording)
        } finally {
            node.endRecording()
        }

        val key = ((nodeW.toLong() * 31 + nodeH) * 31 + (radius * 10f).toInt()) * 31 + (blurPx * 10f).toInt()
        if (key != appliedEffectKey) {
            node.setRenderEffect(buildEffect(blurPx, pad, glassW, glassH, radius, density))
            appliedEffectKey = key
        }
        node.setAlpha(alpha)
        canvas.save()
        canvas.translate(shapeRect.left - pad, shapeRect.top - pad)
        canvas.drawRenderNode(node)
        canvas.restore()
        return true
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun buildEffect(
        blurPx: Float,
        pad: Int,
        glassW: Int,
        glassH: Int,
        radius: Float,
        density: Float,
    ): RenderEffect {
        val base = LiquidGlassEffects.blurSaturation(blurPx)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || lensBroken || lensDp <= 0f) return base
        return try {
            val (effect, shader) = LiquidGlassLens.apply(
                base = base,
                lens = LiquidGlassLens.LensParams(
                    originX = pad.toFloat(),
                    originY = pad.toFloat(),
                    width = glassW.toFloat(),
                    height = glassH.toFloat(),
                    cornerRadius = radius,
                    bezel = bezelDp * density,
                    strength = lensDp * density,
                    dispersion = dispersion,
                ),
                cached = lensShader,
            )
            lensShader = shader
            effect
        } catch (_: Throwable) {
            // A vendor AGSL compiler rejecting the lens must not take the
            // whole settings screen down; keep the blurred glass.
            lensBroken = true
            base
        }
    }

    private fun withAlpha(color: Int, factor: Float): Int =
        Color.argb((Color.alpha(color) * factor).roundToInt().coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))

    companion object {
        const val EDGE_NONE = 0
        const val EDGE_TOP = 1
        const val EDGE_BOTTOM = 2
        private const val PRESS_SCALE = 0.07f
    }
}
