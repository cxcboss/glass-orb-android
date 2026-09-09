package com.cxcboss.glassorb.render

import android.content.Context
import android.opengl.GLES30
import com.cxcboss.glassorb.overlay.OverlayState
import com.cxcboss.glassorb.overlay.OverlayLayout
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tanh

internal class GlOrbPipeline(
    context: Context,
    private val displayDensity: Float,
) {
    private val vertexSource = context.assets.open("shaders/screen.vert").bufferedReader().use { it.readText() }
    private val effectProgram = GlProgram(
        vertexSource,
        context.assets.open("shaders/effect.frag").bufferedReader().use { it.readText() },
    )
    private val containerProgram = GlProgram(
        vertexSource,
        context.assets.open("shaders/container.frag").bufferedReader().use { it.readText() },
    )
    private val glassProgram = GlProgram(
        vertexSource,
        context.assets.open("shaders/glass.frag").bufferedReader().use { it.readText() },
    )

    private var effectTarget: RenderTarget? = null
    private var sceneTarget: RenderTarget? = null
    private var targetWidth = 0
    private var targetHeight = 0
    private var targetScale = 0f
    private var scenePopulated = false
    private val vertexArray = IntArray(1)
    private val vertexBuffer = IntArray(1)

    init {
        val vertices = floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)
        val buffer = ByteBuffer.allocateDirect(vertices.size * Float.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(vertices)
            .apply { position(0) }
        GLES30.glGenVertexArrays(1, vertexArray, 0)
        GLES30.glGenBuffers(1, vertexBuffer, 0)
        GLES30.glBindVertexArray(vertexArray[0])
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vertexBuffer[0])
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, vertices.size * Float.SIZE_BYTES, buffer, GLES30.GL_STATIC_DRAW)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 0, 0)
        GLES30.glBindVertexArray(0)
        checkGl("create fullscreen quad")
    }

    fun render(snapshot: RenderSnapshot, width: Int, height: Int) {
        ensureTargets(width, height, snapshot.config.performance.renderScale)
        val effect = requireNotNull(effectTarget)
        val scene = requireNotNull(sceneTarget)
        val config = snapshot.config
        val frame = ShapeFrame.from(snapshot, width, height, displayDensity)
        val morph = frame.morph
        val density = frame.density
        val visualScale = frame.visualScale
        val shapeWidth = frame.shapeWidth
        val shapeHeight = frame.shapeHeight
        val topPad = frame.top
        val centerX = frame.centerX
        val centerY = frame.centerY
        val effectSize = config.geometry.orbDiameterDp * config.geometry.effectScale * density * visualScale
        val weights = RenderTransition.weights(snapshot.thinkingProgress)
        val orbVisibility = smoothstep(0.08f, 0.72f, morph)
        val waveDelay = config.motion.waveFadeDelayMs / 1_000f
        val entrance = if (snapshot.state is OverlayState.Expanding) {
            smoothstep(waveDelay, waveDelay + 0.22f, snapshot.stateElapsedSeconds)
        } else {
            1f
        }

        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glBindVertexArray(vertexArray[0])

        // The two off-screen passes are the expensive animated part. During a
        // collapse keep their last image and render only the lightweight shape
        // pass. A resize invalidates scenePopulated and forces one fresh pass.
        val renderAmbientScene = snapshot.state != OverlayState.Collapsed &&
            (!snapshot.collapseEffectsFrozen || !scenePopulated)
        if (renderAmbientScene) {
            effect.bind()
            GLES30.glViewport(0, 0, effect.width, effect.height)
            GLES30.glClearColor(0f, 0f, 0f, 0f)
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
            effectProgram.use()
            effectProgram.vec2("uResolution", effect.width.toFloat(), effect.height.toFloat())
            effectProgram.float("uTime", snapshot.timeSeconds)
            effectProgram.float("uWavePhase", snapshot.wavePhase)
            effectProgram.float("uLayerOpacity", weights.wave * entrance * orbVisibility)
            effectProgram.float("uDotsOpacity", weights.dots * orbVisibility)
            effectProgram.float("uAmplitude", config.wave.amplitude)
            effectProgram.float("uWaveScale", config.wave.scale)
            effectProgram.float("uAberration", config.wave.chromaticAberration)
            effectProgram.float("uThickness", config.wave.lineWidth)
            effectProgram.float("uIntensity", config.wave.intensity)
            effectProgram.float("uBandFill", config.wave.bandFill)
            effectProgram.float("uBandFillThickness", config.wave.bandFillThickness)
            effectProgram.float("uSoftness", config.wave.softness)
            effectProgram.float("uWhiteClip", config.wave.whiteBloom)
            effectProgram.float("uHueShift", config.wave.hueShiftDegrees)
            effectProgram.float("uLow", snapshot.bands.low)
            effectProgram.float("uMid", snapshot.bands.mid)
            effectProgram.float("uHigh", snapshot.bands.high)
            effectProgram.float("uRingRadius", config.dots.ringRadius)
            effectProgram.float("uDotRadius", config.dots.dotRadius)
            effectProgram.float("uGlowIntensity", config.dots.glow)
            effectProgram.float("uRotation", config.dots.rotationSpeed)
            drawQuad()

            scene.bind()
            GLES30.glViewport(0, 0, targetWidth, targetHeight)
            GLES30.glClearColor(0f, 0f, 0f, 0f)
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
            containerProgram.use()
            effect.bindTexture(0)
            containerProgram.int("uEffectTexture", 0)
            containerProgram.vec2("uResolution", targetWidth.toFloat(), targetHeight.toFloat())
            containerProgram.vec2("uEffectOrigin", (centerX - effectSize * 0.5f) * targetScale,
                (centerY - effectSize * 0.5f) * targetScale)
            containerProgram.vec2("uEffectSize", effectSize * targetScale, effectSize * targetScale)
            containerProgram.float("uContainerStrength", config.container.strength * weights.container)
            containerProgram.float("uContainerFade", config.container.fade)
            containerProgram.float("uContainerGauss", config.container.gaussian)
            drawQuad()
            scenePopulated = true
        }

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glViewport(0, 0, width, height)
        GLES30.glClearColor(0f, 0f, 0f, 0f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        glassProgram.use()
        scene.bindTexture(0)
        glassProgram.int("uSceneTexture", 0)
        glassProgram.vec2("uResolution", width.toFloat(), height.toFloat())
        glassProgram.vec2("uCanvasSize", width.toFloat(), height.toFloat())
        glassProgram.vec2("uPanelOrigin", centerX - shapeWidth * 0.5f, topPad)
        glassProgram.vec2("uPanelSize", shapeWidth, shapeHeight)
        glassProgram.float("uMarginPx", 0f)
        glassProgram.float("uCornerRadius", minOf(shapeWidth, shapeHeight) * 0.5f)
        glassProgram.float("uGlassVisibility", orbVisibility)
        glassProgram.float("uCollapsed", if (snapshot.state == OverlayState.Collapsed) 1f else 0f)
        glassProgram.float("uCapsuleOutline", if (snapshot.capsuleOutline) 1f else 0f)
        glassProgram.float("uHeight", config.glass.internalDepth * density)
        glassProgram.float("uCurvature", config.glass.curvature)
        glassProgram.float("uRefractAmount", -56f * density)
        glassProgram.float("uAngle", 0f)
        glassProgram.float("uGradRadialMix", 0.08f)
        glassProgram.float("uKeyAngle", (Math.PI * 0.25).toFloat())
        glassProgram.float("uFillAngle", (Math.PI * 1.25).toFloat())
        glassProgram.float("uHlAmount", config.glass.highlightAmount)
        glassProgram.float("uHlHeight", config.glass.highlightWidth * density)
        glassProgram.float("uHlCut", config.glass.highlightCut)
        glassProgram.float("uHlNorm", 8f)
        glassProgram.float("uHlCurv", 1f)
        glassProgram.float("uShadowAmount", config.glass.shadowAmount)
        glassProgram.float("uCausticAmount", config.glass.causticAmount)
        glassProgram.float("uShadowOffsetY", config.glass.shadowOffset)
        glassProgram.float("uCausticOffsetY", config.glass.causticOffset)
        glassProgram.float("uProjectionSoftness", config.glass.lightSoftness * density)
        drawQuad()

        GLES30.glBindVertexArray(0)
        checkGl("render frame")
    }

    fun invalidateTargets() {
        targetWidth = 0
        targetHeight = 0
        scenePopulated = false
    }

    fun release() {
        effectTarget?.release()
        sceneTarget?.release()
        effectTarget = null
        sceneTarget = null
        effectProgram.release()
        containerProgram.release()
        glassProgram.release()
        GLES30.glDeleteBuffers(1, vertexBuffer, 0)
        GLES30.glDeleteVertexArrays(1, vertexArray, 0)
    }

    private fun ensureTargets(width: Int, height: Int, scale: Float) {
        val safeScale = scale.coerceIn(0.5f, 1.25f)
        val scaledWidth = max(1, (width * safeScale).roundToInt())
        val scaledHeight = max(1, (height * safeScale).roundToInt())
        if (targetWidth == scaledWidth && targetHeight == scaledHeight && targetScale == safeScale) return
        effectTarget?.release()
        sceneTarget?.release()
        targetWidth = scaledWidth
        targetHeight = scaledHeight
        targetScale = safeScale
        scenePopulated = false
        val squareSize = max(scaledWidth, scaledHeight)
        effectTarget = RenderTarget(squareSize, squareSize)
        sceneTarget = RenderTarget(scaledWidth, scaledHeight)
    }

    private fun drawQuad() {
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
    }

    private fun softenNegative(value: Float, bounce: Float): Float {
        if (value >= 0f || bounce <= 0f) return value
        return -bounce * tanh((-value / bounce).toDouble()).toFloat()
    }

    private fun smoothstep(edge0: Float, edge1: Float, value: Float): Float {
        val t = ((value - edge0) / max(edge1 - edge0, 0.0001f)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
}

private class GlProgram(vertexSource: String, fragmentSource: String) {
    private val id: Int
    private val uniformLocations = mutableMapOf<String, Int>()

    init {
        val vertex = compileShader(GLES30.GL_VERTEX_SHADER, vertexSource)
        val fragment = compileShader(GLES30.GL_FRAGMENT_SHADER, fragmentSource)
        id = GLES30.glCreateProgram()
        GLES30.glAttachShader(id, vertex)
        GLES30.glAttachShader(id, fragment)
        GLES30.glLinkProgram(id)
        val status = IntArray(1)
        GLES30.glGetProgramiv(id, GLES30.GL_LINK_STATUS, status, 0)
        val log = GLES30.glGetProgramInfoLog(id)
        GLES30.glDeleteShader(vertex)
        GLES30.glDeleteShader(fragment)
        check(status[0] == GLES30.GL_TRUE) { "Shader link failed: $log" }
    }

    fun use() = GLES30.glUseProgram(id)
    fun float(name: String, value: Float) = GLES30.glUniform1f(location(name), value)
    fun int(name: String, value: Int) = GLES30.glUniform1i(location(name), value)
    fun vec2(name: String, x: Float, y: Float) = GLES30.glUniform2f(location(name), x, y)
    fun release() = GLES30.glDeleteProgram(id)

    private fun location(name: String): Int = uniformLocations.getOrPut(name) {
        GLES30.glGetUniformLocation(id, name)
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, source)
        GLES30.glCompileShader(shader)
        val status = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0)
        val log = GLES30.glGetShaderInfoLog(shader)
        check(status[0] == GLES30.GL_TRUE) { "Shader compile failed: $log" }
        return shader
    }
}

private class RenderTarget(val width: Int, val height: Int) {
    private val framebuffer = IntArray(1)
    private val texture = IntArray(1)

    init {
        GLES30.glGenTextures(1, texture, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture[0])
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D,
            0,
            GLES30.GL_RGBA8,
            width,
            height,
            0,
            GLES30.GL_RGBA,
            GLES30.GL_UNSIGNED_BYTE,
            null,
        )
        GLES30.glGenFramebuffers(1, framebuffer, 0)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, framebuffer[0])
        GLES30.glFramebufferTexture2D(
            GLES30.GL_FRAMEBUFFER,
            GLES30.GL_COLOR_ATTACHMENT0,
            GLES30.GL_TEXTURE_2D,
            texture[0],
            0,
        )
        check(GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER) == GLES30.GL_FRAMEBUFFER_COMPLETE) {
            "RGBA8 render target is incomplete"
        }
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
    }

    fun bind() = GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, framebuffer[0])

    fun bindTexture(unit: Int) {
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0 + unit)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture[0])
    }

    fun release() {
        GLES30.glDeleteFramebuffers(1, framebuffer, 0)
        GLES30.glDeleteTextures(1, texture, 0)
    }
}

private fun checkGl(operation: String) {
    val error = GLES30.glGetError()
    check(error == GLES30.GL_NO_ERROR) { "$operation failed: GL error 0x${error.toString(16)}" }
}
