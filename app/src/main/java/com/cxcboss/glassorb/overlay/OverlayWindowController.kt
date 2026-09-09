package com.cxcboss.glassorb.overlay

import android.app.ActivityManager
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.Color
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Region
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Choreographer
import android.view.Gravity
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.FrameLayout
import com.cxcboss.glassorb.model.OrbConfig
import com.cxcboss.glassorb.motion.AmbientBands
import com.cxcboss.glassorb.motion.AnalyticSpring
import com.cxcboss.glassorb.motion.FrequencyBands
import com.cxcboss.glassorb.render.OrbTextureView
import com.cxcboss.glassorb.render.RenderSnapshot
import com.cxcboss.glassorb.render.ShapeMetrics
import com.cxcboss.glassorb.render.ShapeFrame
import kotlin.math.abs
import kotlin.math.roundToInt

class OverlayWindowController(
    private val context: Context,
    private val onFatalError: (String) -> Unit,
) {
    private var windowManager = context.getSystemService(WindowManager::class.java)
    private var accessibilityWindow = false
    private val density = context.resources.displayMetrics.density
    private val mainHandler = Handler(Looper.getMainLooper())
    private val choreographer = Choreographer.getInstance()
    private val stateMachine = OverlayStateMachine()
    private val morphSpring = AnalyticSpring(0f, 0.42f, 0.72f)
    private val collapseReboundSpring = AnalyticSpring(0f, 0.5f, 0.72f)
    private val collapseSecondaryReboundSpring = AnalyticSpring(0f, 0.28f, 0.8f)
    private val thinkingSpring = AnalyticSpring(0f, 0.34f, 0.82f)
    private val pressSpring = AnalyticSpring(0f, 0.18f, 1f)
    private val gestureReturnSpring = AnalyticSpring(0f, 0.34f, 0.82f)
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private var config = OrbConfig.reference()
    private val windowMotion = OverlayWindowMotion(config.motion)
    private var view: OrbTextureView? = null
    private var params: WindowManager.LayoutParams? = null
    private var renderHost: FrameLayout? = null
    private var hostParams: WindowManager.LayoutParams? = null
    private var inputRegion: WindowInputRegion? = null
    private var backgroundDimView: BackgroundDimView? = null
    private var backgroundDimParams: WindowManager.LayoutParams? = null
    private var touchView: View? = null
    private var touchParams: WindowManager.LayoutParams? = null
    private var framePosted = false
    private var screenOn = true
    private var lastFrameNanos = 0L
    private var originNanos = 0L
    private var stateStartNanos = 0L
    private var bands = FrequencyBands(0.35f, 0.4f, 0.3f)
    private var wavePhase = 0f
    private var capsuleCenterOffsetDp = 0f
    private var capsuleTopOffsetDp = 0f
    private var gestureOffsetDp = 0f
    private var trackingSwipe = false
    private var downRawX = 0f
    private var downRawY = 0f
    private var cancelledByMultitouch = false
    private var velocityTracker: VelocityTracker? = null
    private var presentationToken = 0L
    private var lastRenderSubmitNanos = 0L
    private var forceRender = false
    private var showDarkCapsuleOutline = false
    private var collapseReleaseVelocityDpPerSecond = 0f
    private var collapseDirection = 0
    private val thinkingTimeout = Runnable {
        stateMachine.onThinkingTimeout()
        thinkingSpring.target = 0f
        markStateStart()
        scheduleAutoCollapse()
    }

    private val autoCollapseTimeout = Runnable { beginAutomaticCollapse() }

    private val permissionCheck = object : Runnable {
        override fun run() {
            if (view == null || !screenOn) return
            if (!Settings.canDrawOverlays(context)) {
                onFatalError("悬浮窗权限已被撤销")
                return
            }
            mainHandler.postDelayed(this, PERMISSION_CHECK_INTERVAL_MS)
        }
    }

    private val frameCallback = Choreographer.FrameCallback(::onFrame)

    fun show(): Boolean {
        if (view != null) return true
        val windowContext = AccessibilityOverlayBridge.windowContext()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && windowContext == null) {
            onFatalError("请先在运行与权限中开启无障碍触控服务")
            return false
        }
        accessibilityWindow = windowContext != null
        windowManager = (windowContext ?: context).getSystemService(WindowManager::class.java)
        if (!Settings.canDrawOverlays(context)) {
            onFatalError("悬浮窗权限已被撤销")
            return false
        }
        if (!supportsGles30()) {
            onFatalError("设备未报告 OpenGL ES 3.0 支持")
            return false
        }

        stateMachine.showCollapsed()
        morphSpring.snapTo(0f)
        resetCollapseRebound()
        thinkingSpring.snapTo(0f)
        pressSpring.snapTo(0f)
        gestureReturnSpring.snapTo(0f)
        gestureOffsetDp = 0f
        wavePhase = 0f
        capsuleCenterOffsetDp = 0f
        capsuleTopOffsetDp = 0f
        presentationToken += 1
        val textureView = OrbTextureView(context).apply {
            setOnTouchListener(::onTouch)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            onRenderFailure = { error -> onFatalError(error.message ?: "OpenGL 渲染初始化失败") }
        }
        val safe = safeBounds()
        val bounds = OverlayLayout.expandedBounds(config.geometry, safe, density)
        val collapsed = OverlayLayout.collapsedBounds(config.geometry, safe, density)
        windowMotion.setExpandedWindow(OverlayLayout.anchorOffsets(bounds, collapsed, density))
        val layoutParams = createLayoutParams(bounds, touchable = true)
        val host = FrameLayout(context).apply {
            clipChildren = true
            addView(textureView, FrameLayout.LayoutParams(bounds.width, bounds.height))
        }
        // Keep the host origin and canvas fixed for the entire morph. Moving
        // the window and translating its child are separate compositor updates
        // and can expose one frame with mismatched coordinates.
        val initialHostParams = createLayoutParams(bounds, touchable = true)
        val touchBounds = touchBounds(expanded = false, safe = safe)
        val target = View(context).apply { setOnTouchListener(::onTouch) }
        val targetParams = createLayoutParams(touchBounds, touchable = true)
        val dimView = BackgroundDimView(context)
        val dimParams = createBackgroundDimParams()
        return try {
            // Place the static dim layer below the GL surface and touch proxy.
            windowManager.addView(dimView, dimParams)
            windowManager.addView(host, initialHostParams)
            inputRegion = WindowInputRegion(host)
            windowManager.addView(target, targetParams)
            view = textureView
            params = layoutParams
            renderHost = host
            hostParams = initialHostParams
            backgroundDimView = dimView
            backgroundDimParams = dimParams
            touchView = target
            touchParams = targetParams
            AccessibilityOverlayBridge.updateTouchBounds(
                if (AccessibilityOverlayBridge.isConnected()) currentAccessibilityTouchBounds() else null,
            )
            val now = System.nanoTime()
            originNanos = now
            stateStartNanos = now
            lastFrameNanos = 0L
            forceRender = true
            updateBackgroundDim()
            postFrame()
            schedulePermissionCheck()
            true
        } catch (error: Throwable) {
            inputRegion?.close()
            inputRegion = null
            runCatching { windowManager.removeViewImmediate(target) }
            runCatching { windowManager.removeViewImmediate(host) }
            runCatching { windowManager.removeViewImmediate(dimView) }
            textureView.release()
            onFatalError(error.message ?: "无法创建悬浮窗")
            false
        }
    }

    fun hide() {
        mainHandler.removeCallbacks(thinkingTimeout)
        mainHandler.removeCallbacks(autoCollapseTimeout)
        mainHandler.removeCallbacks(permissionCheck)
        stateMachine.hide()
        removeView()
    }

    fun destroy() {
        hide()
    }

    /** Receives touch events from the trusted accessibility proxy when needed. */
    internal fun dispatchAccessibilityTouch(host: View, event: MotionEvent): Boolean =
        onTouch(host, event)

    /** Returns the capsule proxy bounds for the status-bar edge case. */
    internal fun currentAccessibilityTouchBounds(): IntRect? {
        if (accessibilityWindow || view == null) return null
        val safe = safeBounds()
        return if (stateMachine.state == OverlayState.Collapsed) {
            OverlayLayout.capsuleTouchBounds(config.geometry, safe, density)
        } else null
    }

    internal fun refreshTouchBoundsForAccessibility() {
        if (view != null && accessibilityWindow && !AccessibilityOverlayBridge.isConnected()) {
            hide()
            onFatalError("无障碍触控服务已关闭，请重新授权后启动")
            return
        }
        if (view != null) updateTouchBounds(stateMachine.state != OverlayState.Collapsed)
    }

    fun setScreenOn(screenOn: Boolean) {
        this.screenOn = screenOn
        view?.setPaused(!screenOn)
        if (screenOn) {
            lastFrameNanos = 0L
            postFrame()
            schedulePermissionCheck()
        } else {
            removeFrame()
            mainHandler.removeCallbacks(permissionCheck)
        }
    }

    fun updateConfig(config: OrbConfig) {
        this.config = config.normalized()
        windowMotion.updateMotion(this.config.motion)
        updateBounds()
        updateTouchBounds(stateMachine.state != OverlayState.Collapsed)
        updateBackgroundDim()
        if (stateMachine.state == OverlayState.Wave) scheduleAutoCollapse()
        forceRender = true
        postFrame()
    }

    fun onConfigurationChanged() {
        updateBounds()
        updateTouchBounds(stateMachine.state != OverlayState.Collapsed)
        updateBackgroundDim()
        forceRender = true
        postFrame()
    }

    fun setAppAppearance(active: Boolean, dark: Boolean) {
        showDarkCapsuleOutline = active && dark
        forceRender = true
        postFrame()
    }

    fun setTouchPreview(visible: Boolean) {
        touchView?.background = if (visible) GradientDrawable().apply {
            setColor(Color.TRANSPARENT)
            setStroke((2f * density).roundToInt().coerceAtLeast(2), Color.RED)
            cornerRadius = 12f * density
        } else null
    }

    private fun onFrame(frameTimeNanos: Long) {
        framePosted = false
        val textureView = view ?: return
        if (!screenOn) return

        val deltaSeconds = if (lastFrameNanos == 0L) 0f else {
            ((frameTimeNanos - lastFrameNanos) / 1_000_000_000.0).toFloat().coerceIn(0f, 0.05f)
        }
        lastFrameNanos = frameTimeNanos
        val elapsedSeconds = ((frameTimeNanos - originNanos) / 1_000_000_000.0).toFloat()

        val stateElapsedSeconds = ((frameTimeNanos - stateStartNanos) / 1_000_000_000.0).toFloat()

        morphSpring.step(deltaSeconds)
        thinkingSpring.step(deltaSeconds)
        pressSpring.step(deltaSeconds)
        gestureReturnSpring.step(deltaSeconds)
        windowMotion.step(deltaSeconds)
        collapseReboundSpring.step(deltaSeconds)
        collapseSecondaryReboundSpring.step(deltaSeconds)
        if (!trackingSwipe) gestureOffsetDp = gestureReturnSpring.value
        val collapseEffectsFrozen = stateMachine.state == OverlayState.Collapsing &&
            morphSpring.value <= COLLAPSE_EFFECT_FREEZE_MORPH
        if (!collapseEffectsFrozen && stateMachine.state != OverlayState.Collapsed) {
            bands = AmbientBands.smooth(bands, AmbientBands.targetsAt(elapsedSeconds))
            wavePhase = AmbientBands.advanceWavePhase(wavePhase, bands, deltaSeconds)
        }

        val stateBeforeAnimationSettle = stateMachine.state
        when (stateMachine.state) {
            OverlayState.Expanding -> if (isSettled(morphSpring, 1f) && stateElapsedSeconds > 0.12f) {
                stateMachine.onAnimationSettled()
                // The first Wave frame must not be delayed behind the previous
                // expanding snapshot when the producer is frame-throttled.
                forceRender = true
                markStateStart(frameTimeNanos)
                scheduleAutoCollapse()
                updateTouchBounds(expanded = true)
            }

            OverlayState.Collapsing -> if (
                isSettled(morphSpring, 0f) &&
                    isSettled(collapseReboundSpring, 0f) &&
                    isSettled(collapseSecondaryReboundSpring, 0f) &&
                    stateElapsedSeconds > 0.10f
            ) {
                stateMachine.onAnimationSettled()
                morphSpring.snapTo(0f)
                gestureOffsetDp = 0f
                gestureReturnSpring.snapTo(0f)
                windowMotion.onCollapseSettled()
                pressSpring.snapTo(0f)
                collapseReleaseVelocityDpPerSecond = 0f
                collapseDirection = 0
                resetCollapseRebound()
                updateBackgroundDim()
                presentationToken += 1
                // A settled capsule is rendered by a separate shader branch
                // with alpha=1. Always submit that final frame; otherwise the
                // idle optimization can leave the last translucent morph frame
                // on the TextureView indefinitely.
                forceRender = true
                lastRenderSubmitNanos = 0L
                markStateStart(frameTimeNanos)
                updateTouchBounds(expanded = false)
            }

            else -> Unit
        }

        // Keep this guard for any future state transition added above. A state
        // change must publish at least one snapshot even when cadence throttling
        // says the next regular frame is not due yet.
        if (shouldForceRenderAfterStateChange(stateBeforeAnimationSettle, stateMachine.state)) {
            forceRender = true
        }

        val springProgress = if (stateMachine.state is OverlayState.SwipeTracking) 1f else morphSpring.value
        updateRenderAnchors(springProgress)

        val targetFps = if (stateMachine.state == OverlayState.Collapsed) {
            config.performance.collapsedFps
        } else {
            // Interactive morphing must use every display vsync. A persisted
            // low idle FPS setting must never make a fast finger jump between
            // capsule/orb states.
            maxOf(config.performance.expandedFps, 120)
        }
        val renderDue = forceRender || lastRenderSubmitNanos == 0L ||
            frameTimeNanos - lastRenderSubmitNanos >= 1_000_000_000L / targetFps
        if (renderDue) {
            forceRender = false
            lastRenderSubmitNanos = frameTimeNanos
            val snapshot = RenderSnapshot(
                config = config,
                state = stateMachine.state,
                springProgress = springProgress,
                gestureOffsetDp = gestureOffsetDp,
                collapsePull = windowMotion.collapsePull,
                collapseVelocityDpPerSecond = collapseReleaseVelocityDpPerSecond,
                collapseDirection = collapseDirection,
                collapseRebound = collapseReboundSpring.value,
                collapseSecondaryRebound = collapseSecondaryReboundSpring.value,
                collapseEffectsFrozen = collapseEffectsFrozen,
                deformation = windowMotion.deformation,
                capsuleCenterOffsetDp = capsuleCenterOffsetDp,
                capsuleTopOffsetDp = capsuleTopOffsetDp,
                pressProgress = pressSpring.value,
                thinkingProgress = thinkingSpring.value,
                timeSeconds = elapsedSeconds,
                wavePhase = wavePhase,
                bands = bands,
                stateElapsedSeconds = stateElapsedSeconds,
                viewportWidth = params?.width ?: 0,
                viewportHeight = params?.height ?: 0,
                presentationToken = presentationToken,
                capsuleOutline = showDarkCapsuleOutline && isSystemDarkMode() &&
                    stateMachine.state == OverlayState.Collapsed,
            )
            updateShapeInputRegion(snapshot)
            textureView.submit(snapshot)
        }
        val pressIsSettled = isSettled(pressSpring, pressSpring.target)
        if (shouldContinueOverlayFrames(stateMachine.state, pressIsSettled)) {
            postFrame()
        } else {
            // Keep the last opaque capsule buffer on screen without holding a
            // Choreographer loop. GlRenderLoop is submit-driven, so it becomes
            // idle after this final submitted frame without a racing pause.
            lastFrameNanos = 0L
        }
    }

    private fun onTouch(view: View, event: MotionEvent): Boolean {
        if (cancelledByMultitouch && event.actionMasked != MotionEvent.ACTION_DOWN) {
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                finishTouch()
            }
            return true
        }
        if (event.pointerCount > 1) {
            cancelledByMultitouch = true
            cancelGesture()
            return true
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                cancelledByMultitouch = false
                trackingSwipe = false
                collapseDirection = 0
                downRawX = event.rawX
                downRawY = event.rawY
                velocityTracker?.recycle()
                velocityTracker = VelocityTracker.obtain().also { it.addMovement(event) }
                pressSpring.target = 1f
                windowMotion.cancelGesture()
                postFrame()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                velocityTracker?.addMovement(event)
                val deltaXPx = event.rawX - downRawX
                val deltaPx = event.rawY - downRawY
                val canSwipe = stateMachine.state == OverlayState.Wave ||
                    stateMachine.state == OverlayState.Thinking ||
                    stateMachine.state is OverlayState.SwipeTracking
                if (canSwipe && (trackingSwipe || maxOf(abs(deltaPx), abs(deltaXPx)) > touchSlop)) {
                    if (!trackingSwipe) {
                        trackingSwipe = true
                        stateMachine.onSwipeStart()
                        markStateStart()
                    }
                    val deltaXDp = (deltaXPx / density).coerceIn(-180f, 180f)
                    gestureOffsetDp = (deltaPx / density).coerceIn(-180f, 24f)
                    gestureReturnSpring.snapTo(gestureOffsetDp)
                    windowMotion.onSwipeMove(deltaXDp = deltaXDp, deltaYDp = gestureOffsetDp)
                    stateMachine.onSwipe(gestureOffsetDp)
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                velocityTracker?.addMovement(event)
                pressSpring.target = 0f
                if (!cancelledByMultitouch && trackingSwipe) {
                    val deltaXPx = event.rawX - downRawX
                    velocityTracker?.computeCurrentVelocity(1_000)
                    val velocityXDp = (velocityTracker?.xVelocity ?: 0f) / density
                    val velocityDp = (velocityTracker?.yVelocity ?: 0f) / density
                    val decision = if (windowMotion.collapsePull > 0f) {
                        SwipeDecision.decide(gestureOffsetDp, velocityDp)
                    } else SwipeDecision.Restore
                    collapseReleaseVelocityDpPerSecond = if (decision == SwipeDecision.Collapse) {
                        kotlin.math.hypot(velocityXDp.toDouble(), velocityDp.toDouble()).toFloat()
                    } else {
                        0f
                    }
                    collapseDirection = if (decision == SwipeDecision.Collapse) {
                        when {
                            deltaXPx < -touchSlop -> -1
                            deltaXPx > touchSlop -> 1
                            velocityXDp < -120f -> -1
                            velocityXDp > 120f -> 1
                            else -> 0
                        }
                    } else {
                        0
                    }
                    if (decision == SwipeDecision.Collapse) {
                        startCollapseRebound()
                    } else {
                        resetCollapseRebound()
                    }
                    stateMachine.onSwipeEnd(decision)
                    updateBackgroundDim()
                    val release = windowMotion.release(
                        decision = decision,
                        velocityXDpPerSecond = velocityXDp,
                        velocityYDpPerSecond = velocityDp,
                    )
                    if (decision == SwipeDecision.Collapse) configureCollapseSpring() else configureExpandSpring()
                    morphSpring.seed(release.value, release.velocity, release.target)
                    gestureReturnSpring.configure(0.34f, 0.82f)
                    gestureReturnSpring.seed(gestureOffsetDp, velocityDp, 0f)
                    if (decision == SwipeDecision.Collapse) {
                        mainHandler.removeCallbacks(thinkingTimeout)
                        thinkingSpring.target = 0f
                    } else {
                        collapseReleaseVelocityDpPerSecond = 0f
                        collapseDirection = 0
                        scheduleAutoCollapse()
                    }
                    markStateStart()
                } else if (!cancelledByMultitouch) {
                    view.performClick()
                    handleTap()
                }
                finishTouch()
                postFrame()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                cancelGesture()
                postFrame()
                return true
            }
        }
        return false
    }

    private fun handleTap() {
        when (stateMachine.state) {
            OverlayState.Collapsed -> beginExpand()
            OverlayState.Wave, OverlayState.Thinking -> beginThinking()
            else -> Unit
        }
    }

    private fun beginExpand() {
        collapseReleaseVelocityDpPerSecond = 0f
        collapseDirection = 0
        resetCollapseRebound()
        mainHandler.removeCallbacks(autoCollapseTimeout)
        stateMachine.onTap()
        updateBackgroundDim()
        updateTouchBounds(expanded = true)
        configureExpandSpring()
        morphSpring.snapTo(0f)
        morphSpring.target = 1f
        markStateStart()
    }

    private fun beginThinking() {
        mainHandler.removeCallbacks(autoCollapseTimeout)
        stateMachine.onTap()
        thinkingSpring.configure(0.34f, 0.82f)
        thinkingSpring.target = 1f
        mainHandler.removeCallbacks(thinkingTimeout)
        mainHandler.postDelayed(thinkingTimeout, config.motion.thinkingDurationMs.toLong())
        markStateStart()
    }

    private fun cancelGesture() {
        pressSpring.target = 0f
        if (trackingSwipe) {
            val release = windowMotion.release(SwipeDecision.Restore, 0f, 0f)
            stateMachine.onSwipeEnd(SwipeDecision.Restore)
            configureExpandSpring()
            morphSpring.seed(release.value, 0f, 1f)
            gestureReturnSpring.seed(gestureOffsetDp, 0f, 0f)
        }
        collapseReleaseVelocityDpPerSecond = 0f
        collapseDirection = 0
        resetCollapseRebound()
        finishTouch()
    }

    /** Seeds the two-stage capsule overshoot when a close gesture is released. */
    private fun startCollapseRebound() {
        val speedFactor = (collapseReleaseVelocityDpPerSecond / 1_400f).coerceIn(0f, 1f)
        val impulse = 0.65f + speedFactor * 0.95f
        val directionalBoost = if (collapseDirection == 0) 1f else 1.08f
        val response = config.motion.deformResponse.coerceIn(0.08f, 1.5f)
        val primaryResponse = response * if (config.geometry.expandBelowCapsule) 0.625f else 0.7f
        val secondaryResponse = response * if (config.geometry.expandBelowCapsule) 0.35f else 0.4f
        collapseReboundSpring.configure(
            primaryResponse.coerceIn(0.18f, 1.2f),
            if (config.geometry.expandBelowCapsule) 0.7f else 0.76f,
        )
        collapseSecondaryReboundSpring.configure(
            secondaryResponse.coerceIn(0.12f, 0.9f),
            if (config.geometry.expandBelowCapsule) 0.78f else 0.82f,
        )
        collapseReboundSpring.seed(0f, impulse * directionalBoost, 0f)
        collapseSecondaryReboundSpring.seed(0f, -impulse * 0.42f, 0f)
    }

    /** Uses a slightly longer response so the expand motion reads as one continuous reveal. */
    private fun configureExpandSpring() {
        morphSpring.configure(config.motion.openResponse * 1.18f, config.motion.openDamping)
    }

    /** Keeps the capsule close from snapping ahead of its layered rebound. */
    private fun configureCollapseSpring() {
        morphSpring.configure(
            config.motion.closeResponse * 1.16f,
            (config.motion.closeDamping + 0.05f).coerceAtMost(0.95f),
        )
    }

    private fun resetCollapseRebound() {
        collapseReboundSpring.snapTo(0f)
        collapseSecondaryReboundSpring.snapTo(0f)
    }

    private fun finishTouch() {
        trackingSwipe = false
        velocityTracker?.recycle()
        velocityTracker = null
    }

    private fun updateBounds() {
        val textureView = view ?: return
        val layoutParams = params ?: return
        val safe = safeBounds()
        val bounds = OverlayLayout.expandedBounds(config.geometry, safe, density)
        val collapsed = OverlayLayout.collapsedBounds(config.geometry, safe, density)
        val layoutChanged = layoutParams.width != bounds.width || layoutParams.height != bounds.height ||
            layoutParams.x != bounds.left || layoutParams.y != bounds.top
        if (!layoutChanged) {
            windowMotion.setExpandedWindow(OverlayLayout.anchorOffsets(bounds, collapsed, density))
            updateHostBounds()
            updateBackgroundDim()
            return
        }
        windowMotion.setExpandedWindow(OverlayLayout.anchorOffsets(bounds, collapsed, density))
        layoutParams.width = bounds.width
        layoutParams.height = bounds.height
        layoutParams.x = bounds.left
        layoutParams.y = bounds.top
        presentationToken += 1
        updateRenderAnchors(if (stateMachine.state is OverlayState.SwipeTracking) 1f else morphSpring.value)
        try {
            textureView.layoutParams = FrameLayout.LayoutParams(bounds.width, bounds.height)
            updateHostBounds()
            updateBackgroundDim()
        } catch (error: Throwable) {
            onFatalError(error.message ?: "无法更新悬浮窗位置")
        }
    }

    /** Only configuration/display changes may move the host, never a morph. */
    private fun updateHostBounds() {
        val host = renderHost ?: return
        val hostLayout = hostParams ?: return
        val canvas = params ?: return
        val bounds = IntRect(canvas.x, canvas.y, canvas.x + canvas.width, canvas.y + canvas.height)
        if (hostLayout.x == bounds.left && hostLayout.y == bounds.top &&
            hostLayout.width == bounds.width && hostLayout.height == bounds.height) return
        hostLayout.x = bounds.left
        hostLayout.y = bounds.top
        hostLayout.width = bounds.width
        hostLayout.height = bounds.height
        runCatching { windowManager.updateViewLayout(host, hostLayout) }
            .onFailure { onFatalError(it.message ?: "无法更新渲染窗口") }
    }

    private fun scheduleAutoCollapse() {
        mainHandler.removeCallbacks(autoCollapseTimeout)
        if (config.motion.autoCollapseEnabled && stateMachine.state == OverlayState.Wave) {
            mainHandler.postDelayed(autoCollapseTimeout, (config.motion.autoCollapseSeconds * 1_000f).roundToInt().toLong())
        }
    }

    private fun beginAutomaticCollapse() {
        if (stateMachine.state != OverlayState.Wave && stateMachine.state != OverlayState.Thinking) return
        mainHandler.removeCallbacks(thinkingTimeout)
        stateMachine.beginAutomaticCollapse()
        collapseReleaseVelocityDpPerSecond = 0f
        collapseDirection = 0
        startCollapseRebound()
        updateBackgroundDim()
        thinkingSpring.target = 0f
        configureCollapseSpring()
        morphSpring.seed(morphSpring.value, 0f, 0f)
        markStateStart()
    }

    private fun updateTouchBounds(expanded: Boolean) {
        val target = touchView ?: return
        // The GL host supplies the exact animated shape region while expanded.
        // A rectangular proxy would intercept the transparent circle corners.
        target.visibility = if (expanded) View.INVISIBLE else View.VISIBLE
        val layoutParams = touchParams ?: return
        val safe = safeBounds()
        val bounds = touchBounds(expanded, safe)
        AccessibilityOverlayBridge.updateTouchBounds(
            if (AccessibilityOverlayBridge.isConnected() && !accessibilityWindow) {
                if (expanded) null else OverlayLayout.capsuleTouchBounds(config.geometry, safe, density)
            } else null,
        )
        if (layoutParams.width == bounds.width && layoutParams.height == bounds.height &&
            layoutParams.x == bounds.left && layoutParams.y == bounds.top) return
        layoutParams.width = bounds.width
        layoutParams.height = bounds.height
        layoutParams.x = bounds.left
        layoutParams.y = bounds.top
        runCatching { windowManager.updateViewLayout(target, layoutParams) }
            .onFailure { onFatalError(it.message ?: "无法更新触摸区域") }
    }

    private fun updateShapeInputRegion(snapshot: RenderSnapshot) {
        val canvas = params ?: return
        val region = Region()
        if (snapshot.state == OverlayState.Collapsed ||
            (snapshot.state == OverlayState.Collapsing && snapshot.collapseEffectsFrozen)
        ) {
            val bounds = OverlayLayout.capsuleTouchBounds(config.geometry, safeBounds(), density)
            region.set(bounds.left - canvas.x, bounds.top - canvas.y,
                bounds.right - canvas.x, bounds.bottom - canvas.y)
        } else {
            val shape = ShapeFrame.from(snapshot, canvas.width, canvas.height, density)
            val rect = RectF(shape.centerX - shape.shapeWidth / 2f, shape.top,
                shape.centerX + shape.shapeWidth / 2f, shape.top + shape.shapeHeight)
            val path = Path().apply { addOval(rect, Path.Direction.CW) }
            region.setPath(path, Region(0, 0, canvas.width, canvas.height))
        }
        inputRegion?.update(region)
    }

    private fun safeBounds(): IntRect {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = windowManager.currentWindowMetrics
            val bounds = metrics.bounds
            val insets = metrics.windowInsets.getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout(),
            )
            IntRect(
                left = bounds.left + insets.left,
                top = bounds.top,
                right = bounds.right - insets.right,
                bottom = bounds.bottom - insets.bottom,
            )
        } else {
            @Suppress("DEPRECATION")
            val point = Point().also(windowManager.defaultDisplay::getRealSize)
            val navigationBar = systemDimension("navigation_bar_height")
            IntRect(0, 0, point.x, (point.y - navigationBar).coerceAtLeast(1))
        }
    }

    private fun touchBounds(expanded: Boolean, safe: IntRect): IntRect {
        val visualBounds = if (expanded) OverlayLayout.orbTouchBounds(config.geometry, safe, density)
        else OverlayLayout.capsuleTouchBounds(config.geometry, safe, density)
        if (AccessibilityOverlayBridge.isConnected()) return visualBounds
        return OverlayLayout.moveTouchBelowProtectedTop(
            bounds = visualBounds,
            blockedBottomPx = protectedTopInsetPx(),
            limitBottomPx = safe.bottom,
        )
    }

    private fun protectedTopInsetPx(): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = windowManager.currentWindowMetrics
            val inset = metrics.windowInsets.getInsetsIgnoringVisibility(
                WindowInsets.Type.statusBars() or WindowInsets.Type.displayCutout(),
            ).top
            return metrics.bounds.top + inset
        }
        return systemDimension("status_bar_height")
    }

    private fun isSystemDarkMode(): Boolean =
        context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES

    private fun createLayoutParams(bounds: IntRect, touchable: Boolean) = WindowManager.LayoutParams(
        bounds.width,
        bounds.height,
        if (accessibilityWindow) {
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        },
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            (if (touchable) 0 else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = bounds.left
        y = bounds.top
        title = "Glass orb overlay"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) setFitInsetsTypes(0)
    }

    private fun createBackgroundDimParams(): WindowManager.LayoutParams {
        val physical = physicalBounds()
        return WindowManager.LayoutParams(
            physical.width,
            physical.height,
            if (accessibilityWindow) {
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = physical.left
            y = physical.top
            // Keep the separate non-touchable dim window under Android's
            // untrusted-touch opacity threshold. The view shader itself uses
            // solid black at the gradient start, so the visible maximum stays
            // exactly 28% while pass-through remains allowed on Android 12+.
            alpha = 0.28f
            title = "Glass orb background dim"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                setFitInsetsTypes(0)
            }
        }
    }

    private fun updateBackgroundDim() {
        val dimView = backgroundDimView ?: return
        val dimParams = backgroundDimParams ?: return
        val physical = physicalBounds()
        val gradientBottom = OverlayLayout.expandedVisualBottomPx(config.geometry, safeBounds(), density)
        val gradientHeight = (gradientBottom - physical.top).coerceIn(1, physical.height)
        val expanded = stateMachine.state != OverlayState.Collapsed &&
            stateMachine.state != OverlayState.Hidden && stateMachine.state != OverlayState.Collapsing
        dimView.update(
            config.container.backgroundDimEnabled && expanded,
            gradientHeight,
            dimTransitionDurationMs(),
        )
        val changed = dimParams.width != physical.width || dimParams.height != physical.height ||
            dimParams.x != physical.left || dimParams.y != physical.top
        if (changed) {
            dimParams.width = physical.width
            dimParams.height = physical.height
            dimParams.x = physical.left
            dimParams.y = physical.top
            runCatching { windowManager.updateViewLayout(dimView, dimParams) }
                .onFailure { onFatalError(it.message ?: "无法更新背景遮罩") }
        }
    }

    /** Keeps the dim layer's alpha transition on the same slower timing as the shape spring. */
    private fun dimTransitionDurationMs(): Long {
        val response = when (stateMachine.state) {
            OverlayState.Expanding -> config.motion.openResponse * 1.18f
            OverlayState.Collapsing, OverlayState.Collapsed -> config.motion.closeResponse * 1.16f
            else -> config.motion.openResponse * 1.18f
        }
        return (response * 1_000f * 1.05f).roundToInt().toLong().coerceIn(300L, 900L)
    }

    private fun physicalBounds(): IntRect {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            return IntRect(bounds.left, bounds.top, bounds.right, bounds.bottom)
        }
        @Suppress("DEPRECATION")
        val point = Point().also(windowManager.defaultDisplay::getRealSize)
        return IntRect(0, 0, point.x, point.y)
    }

    private fun removeView() {
        removeFrame()
        mainHandler.removeCallbacks(permissionCheck)
        val textureView = view
        inputRegion?.close()
        inputRegion = null
        val host = renderHost
        renderHost = null
        hostParams = null
        view = null
        params = null
        touchView?.let { runCatching { windowManager.removeViewImmediate(it) } }
        touchView = null
        touchParams = null
        backgroundDimView?.let { runCatching { windowManager.removeViewImmediate(it) } }
        backgroundDimView = null
        backgroundDimParams = null
        AccessibilityOverlayBridge.updateTouchBounds(null)
        if (textureView == null) return
        try {
            windowManager.removeViewImmediate(host ?: textureView)
        } catch (_: Throwable) {
            textureView.release()
        }
    }

    private fun postFrame() {
        if (framePosted || !screenOn || view == null) return
        view?.setPaused(false)
        framePosted = true
        choreographer.postFrameCallback(frameCallback)
    }

    private fun schedulePermissionCheck() {
        mainHandler.removeCallbacks(permissionCheck)
        if (view != null && screenOn) {
            mainHandler.postDelayed(permissionCheck, PERMISSION_CHECK_INTERVAL_MS)
        }
    }

    private fun removeFrame() {
        if (framePosted) choreographer.removeFrameCallback(frameCallback)
        framePosted = false
    }

    private fun markStateStart(timeNanos: Long = System.nanoTime()) {
        stateStartNanos = timeNanos
    }

    private fun updateRenderAnchors(springProgress: Float) {
        val currentParams = params
        if (!windowMotion.windowExpanded || currentParams == null) {
            capsuleCenterOffsetDp = 0f
            capsuleTopOffsetDp = 0f
            return
        }

        val centerXDp = currentParams.width / (2f * density)
        capsuleCenterOffsetDp = windowMotion.anchorCenterXDp - centerXDp
        capsuleTopOffsetDp = ShapeMetrics.interpolate(
            geometry = config.geometry,
            anchorTopDp = windowMotion.anchorTopDp,
            anchorCenterXDp = windowMotion.anchorCenterXDp,
            morph = springProgress,
            deformation = windowMotion.deformation.copy(topOffsetDp = 0f),
        ).topDp - OverlayLayout.WINDOW_TOP_PADDING_DP
    }

    private fun isSettled(spring: AnalyticSpring, target: Float): Boolean =
        abs(spring.value - target) < 0.001f && abs(spring.velocity) < 0.02f

    private fun supportsGles30(): Boolean {
        val manager = context.getSystemService(ActivityManager::class.java)
        return manager.deviceConfigurationInfo.reqGlEsVersion >= 0x00030000
    }

    private fun systemDimension(name: String): Int {
        val id = context.resources.getIdentifier(name, "dimen", "android")
        return if (id == 0) 0 else context.resources.getDimensionPixelSize(id)
    }

    private companion object {
        // Freeze the expensive wave/dots passes after the orb has mostly
        // collapsed. The shape pass continues at display cadence for a smooth
        // final morph and rebound.
        const val COLLAPSE_EFFECT_FREEZE_MORPH = 0.36f
        const val PERMISSION_CHECK_INTERVAL_MS = 15_000L
    }
}
