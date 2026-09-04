package com.cxcboss.glassorb.overlay

import android.app.ActivityManager
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Point
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
import com.cxcboss.glassorb.model.OrbConfig
import com.cxcboss.glassorb.motion.AmbientBands
import com.cxcboss.glassorb.motion.AnalyticSpring
import com.cxcboss.glassorb.motion.FrequencyBands
import com.cxcboss.glassorb.render.OrbTextureView
import com.cxcboss.glassorb.render.RenderSnapshot
import com.cxcboss.glassorb.render.ShapeMetrics
import kotlin.math.abs
import kotlin.math.roundToInt

class OverlayWindowController(
    private val context: Context,
    private val onFatalError: (String) -> Unit,
) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val density = context.resources.displayMetrics.density
    private val mainHandler = Handler(Looper.getMainLooper())
    private val choreographer = Choreographer.getInstance()
    private val stateMachine = OverlayStateMachine()
    private val morphSpring = AnalyticSpring(0f, 0.42f, 0.72f)
    private val thinkingSpring = AnalyticSpring(0f, 0.34f, 0.82f)
    private val pressSpring = AnalyticSpring(0f, 0.18f, 1f)
    private val gestureReturnSpring = AnalyticSpring(0f, 0.34f, 0.82f)
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private var config = OrbConfig.reference()
    private val windowMotion = OverlayWindowMotion(config.motion)
    private var view: OrbTextureView? = null
    private var params: WindowManager.LayoutParams? = null
    private var touchView: View? = null
    private var touchParams: WindowManager.LayoutParams? = null
    private var framePosted = false
    private var screenOn = true
    private var lastFrameNanos = 0L
    private var originNanos = 0L
    private var stateStartNanos = 0L
    private var lastPermissionCheckNanos = 0L
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
    private var showDarkCapsuleOutline = false

    private val thinkingTimeout = Runnable {
        stateMachine.onThinkingTimeout()
        thinkingSpring.target = 0f
        markStateStart()
        scheduleAutoCollapse()
    }

    private val autoCollapseTimeout = Runnable { beginAutomaticCollapse() }

    private val frameCallback = Choreographer.FrameCallback(::onFrame)

    fun show(): Boolean {
        if (view != null) return true
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
        thinkingSpring.snapTo(0f)
        pressSpring.snapTo(0f)
        gestureReturnSpring.snapTo(0f)
        gestureOffsetDp = 0f
        wavePhase = 0f
        capsuleCenterOffsetDp = 0f
        capsuleTopOffsetDp = 0f
        presentationToken += 1
        val textureView = OrbTextureView(context).apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            onRenderFailure = { error -> onFatalError(error.message ?: "OpenGL 渲染初始化失败") }
        }
        val safe = safeBounds()
        val bounds = OverlayLayout.expandedBounds(config.geometry, safe, density)
        val collapsed = OverlayLayout.collapsedBounds(config.geometry, safe, density)
        windowMotion.setExpandedWindow(OverlayLayout.anchorOffsets(bounds, collapsed, density))
        val layoutParams = createLayoutParams(bounds, touchable = false)
        val touchBounds = OverlayLayout.capsuleTouchBounds(config.geometry, safe, density)
        val target = View(context).apply { setOnTouchListener(::onTouch) }
        val targetParams = createLayoutParams(touchBounds, touchable = true)
        return try {
            windowManager.addView(textureView, layoutParams)
            windowManager.addView(target, targetParams)
            view = textureView
            params = layoutParams
            touchView = target
            touchParams = targetParams
            val now = System.nanoTime()
            originNanos = now
            stateStartNanos = now
            lastFrameNanos = 0L
            postFrame()
            true
        } catch (error: Throwable) {
            runCatching { windowManager.removeViewImmediate(target) }
            runCatching { windowManager.removeViewImmediate(textureView) }
            textureView.release()
            onFatalError(error.message ?: "无法创建悬浮窗")
            false
        }
    }

    fun hide() {
        mainHandler.removeCallbacks(thinkingTimeout)
        mainHandler.removeCallbacks(autoCollapseTimeout)
        stateMachine.hide()
        removeView()
    }

    fun destroy() {
        hide()
    }

    fun setScreenOn(screenOn: Boolean) {
        this.screenOn = screenOn
        view?.setPaused(!screenOn)
        if (screenOn) {
            lastFrameNanos = 0L
            postFrame()
        } else {
            removeFrame()
        }
    }

    fun updateConfig(config: OrbConfig) {
        this.config = config.normalized()
        windowMotion.updateMotion(this.config.motion)
        updateBounds()
        updateTouchBounds(stateMachine.state != OverlayState.Collapsed)
        if (stateMachine.state == OverlayState.Wave) scheduleAutoCollapse()
        postFrame()
    }

    fun onConfigurationChanged() {
        updateBounds()
        updateTouchBounds(stateMachine.state != OverlayState.Collapsed)
    }

    fun setAppAppearance(active: Boolean, dark: Boolean) {
        showDarkCapsuleOutline = active && dark
        postFrame()
    }

    private fun onFrame(frameTimeNanos: Long) {
        framePosted = false
        val textureView = view ?: return
        if (!screenOn) return

        if (frameTimeNanos - lastPermissionCheckNanos > 1_000_000_000L) {
            lastPermissionCheckNanos = frameTimeNanos
            if (!Settings.canDrawOverlays(context)) {
                onFatalError("悬浮窗权限已被撤销")
                return
            }
        }

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
        if (!trackingSwipe) gestureOffsetDp = gestureReturnSpring.value
        bands = AmbientBands.smooth(bands, AmbientBands.targetsAt(elapsedSeconds))
        wavePhase = AmbientBands.advanceWavePhase(wavePhase, bands, deltaSeconds)

        when (stateMachine.state) {
            OverlayState.Expanding -> if (isSettled(morphSpring, 1f) && stateElapsedSeconds > 0.12f) {
                stateMachine.onAnimationSettled()
                markStateStart(frameTimeNanos)
                scheduleAutoCollapse()
                updateTouchBounds(expanded = true)
            }

            OverlayState.Collapsing -> if (isSettled(morphSpring, 0f) && stateElapsedSeconds > 0.10f) {
                stateMachine.onAnimationSettled()
                morphSpring.snapTo(0f)
                gestureOffsetDp = 0f
                gestureReturnSpring.snapTo(0f)
                windowMotion.onCollapseSettled()
                pressSpring.snapTo(0f)
                markStateStart(frameTimeNanos)
                updateTouchBounds(expanded = false)
            }

            else -> Unit
        }

        val springProgress = if (stateMachine.state is OverlayState.SwipeTracking) 1f else morphSpring.value
        updateRenderAnchors(springProgress)

        val targetFps = if (stateMachine.state == OverlayState.Collapsed) config.performance.collapsedFps else config.performance.expandedFps
        val renderDue = lastRenderSubmitNanos == 0L || frameTimeNanos - lastRenderSubmitNanos >= 1_000_000_000L / targetFps
        if (renderDue) {
            lastRenderSubmitNanos = frameTimeNanos
            textureView.submit(
            RenderSnapshot(
                config = config,
                state = stateMachine.state,
                springProgress = springProgress,
                gestureOffsetDp = gestureOffsetDp,
                collapsePull = windowMotion.collapsePull,
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
                capsuleOutline = showDarkCapsuleOutline && stateMachine.state == OverlayState.Collapsed,
            ),
            )
        }
        postFrame()
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
                downRawX = event.rawX
                downRawY = event.rawY
                velocityTracker?.recycle()
                velocityTracker = VelocityTracker.obtain().also { it.addMovement(event) }
                pressSpring.target = 1f
                windowMotion.cancelGesture()
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
                    velocityTracker?.computeCurrentVelocity(1_000)
                    val velocityXDp = (velocityTracker?.xVelocity ?: 0f) / density
                    val velocityDp = (velocityTracker?.yVelocity ?: 0f) / density
                    val decision = if (windowMotion.collapsePull > 0f) {
                        SwipeDecision.decide(gestureOffsetDp, velocityDp)
                    } else SwipeDecision.Restore
                    stateMachine.onSwipeEnd(decision)
                    val release = windowMotion.release(
                        decision = decision,
                        velocityXDpPerSecond = velocityXDp,
                        velocityYDpPerSecond = velocityDp,
                    )
                    morphSpring.configure(
                        if (decision == SwipeDecision.Collapse) config.motion.closeResponse else config.motion.openResponse,
                        if (decision == SwipeDecision.Collapse) config.motion.closeDamping else config.motion.openDamping,
                    )
                    morphSpring.seed(release.value, release.velocity, release.target)
                    gestureReturnSpring.configure(0.34f, 0.82f)
                    gestureReturnSpring.seed(gestureOffsetDp, velocityDp, 0f)
                    if (decision == SwipeDecision.Collapse) {
                        mainHandler.removeCallbacks(thinkingTimeout)
                        thinkingSpring.target = 0f
                    } else {
                        scheduleAutoCollapse()
                    }
                    markStateStart()
                } else if (!cancelledByMultitouch) {
                    view.performClick()
                    handleTap()
                }
                finishTouch()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                cancelGesture()
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
        mainHandler.removeCallbacks(autoCollapseTimeout)
        stateMachine.onTap()
        updateTouchBounds(expanded = true)
        morphSpring.configure(config.motion.openResponse, config.motion.openDamping)
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
            morphSpring.configure(config.motion.openResponse, config.motion.openDamping)
            morphSpring.seed(release.value, 0f, 1f)
            gestureReturnSpring.seed(gestureOffsetDp, 0f, 0f)
        }
        finishTouch()
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
            windowManager.updateViewLayout(textureView, layoutParams)
        } catch (error: Throwable) {
            onFatalError(error.message ?: "无法更新悬浮窗位置")
        }
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
        thinkingSpring.target = 0f
        morphSpring.configure(config.motion.closeResponse, config.motion.closeDamping)
        morphSpring.seed(morphSpring.value, 0f, 0f)
        markStateStart()
    }

    private fun updateTouchBounds(expanded: Boolean) {
        val target = touchView ?: return
        val layoutParams = touchParams ?: return
        val safe = safeBounds()
        val bounds = if (expanded) OverlayLayout.orbTouchBounds(config.geometry, safe, density)
        else OverlayLayout.capsuleTouchBounds(config.geometry, safe, density)
        if (layoutParams.width == bounds.width && layoutParams.height == bounds.height &&
            layoutParams.x == bounds.left && layoutParams.y == bounds.top) return
        layoutParams.width = bounds.width
        layoutParams.height = bounds.height
        layoutParams.x = bounds.left
        layoutParams.y = bounds.top
        runCatching { windowManager.updateViewLayout(target, layoutParams) }
            .onFailure { onFatalError(it.message ?: "无法更新触摸区域") }
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

    private fun createLayoutParams(bounds: IntRect, touchable: Boolean) = WindowManager.LayoutParams(
        bounds.width,
        bounds.height,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        },
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            (if (touchable) 0 else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = bounds.left
        y = bounds.top
        title = "Glass orb overlay"
    }

    private fun removeView() {
        removeFrame()
        val textureView = view ?: return
        view = null
        params = null
        touchView?.let { runCatching { windowManager.removeViewImmediate(it) } }
        touchView = null
        touchParams = null
        try {
            windowManager.removeViewImmediate(textureView)
        } catch (_: Throwable) {
            textureView.release()
        }
    }

    private fun postFrame() {
        if (framePosted || !screenOn || view == null) return
        framePosted = true
        choreographer.postFrameCallback(frameCallback)
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
}
