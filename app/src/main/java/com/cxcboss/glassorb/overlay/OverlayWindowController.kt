package com.cxcboss.glassorb.overlay

import android.app.ActivityManager
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.Color
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
        val touchBounds = touchBounds(expanded = false, safe = safe)
        val target = View(context).apply { setOnTouchListener(::onTouch) }
        val targetParams = createLayoutParams(touchBounds, touchable = true)
        val dimView = BackgroundDimView(context)
        val dimParams = createBackgroundDimParams()
        return try {
            // Place the static dim layer below the GL surface and touch proxy.
            windowManager.addView(dimView, dimParams)
            windowManager.addView(textureView, layoutParams)
            windowManager.addView(target, targetParams)
            view = textureView
            params = layoutParams
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
            runCatching { windowManager.removeViewImmediate(target) }
            runCatching { windowManager.removeViewImmediate(textureView) }
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

    /** Entry point used by the optional AccessibilityService touch proxy. */
    internal fun dispatchAccessibilityTouch(host: View, event: MotionEvent): Boolean =
        onTouch(host, event)

    /** Returns the visual hit area so an accessibility overlay can cover the status bar. */
    internal fun currentAccessibilityTouchBounds(): IntRect? {
        if (view == null) return null
        val safe = safeBounds()
        return if (stateMachine.state == OverlayState.Collapsed) {
            OverlayLayout.capsuleTouchBounds(config.geometry, safe, density)
        } else {
            OverlayLayout.orbTouchBounds(config.geometry, safe, density)
        }
    }

    internal fun refreshTouchBoundsForAccessibility() {
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
        if (!trackingSwipe) gestureOffsetDp = gestureReturnSpring.value
        bands = AmbientBands.smooth(bands, AmbientBands.targetsAt(elapsedSeconds))
        wavePhase = AmbientBands.advanceWavePhase(wavePhase, bands, deltaSeconds)

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

            OverlayState.Collapsing -> if (isSettled(morphSpring, 0f) && stateElapsedSeconds > 0.10f) {
                stateMachine.onAnimationSettled()
                morphSpring.snapTo(0f)
                gestureOffsetDp = 0f
                gestureReturnSpring.snapTo(0f)
                windowMotion.onCollapseSettled()
                pressSpring.snapTo(0f)
                updateBackgroundDim()
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
                capsuleOutline = showDarkCapsuleOutline && isSystemDarkMode() &&
                    stateMachine.state == OverlayState.Collapsed,
            ),
            )
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
        mainHandler.removeCallbacks(autoCollapseTimeout)
        stateMachine.onTap()
        updateBackgroundDim()
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
            windowManager.updateViewLayout(textureView, layoutParams)
            updateBackgroundDim()
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
        val bounds = touchBounds(expanded, safe)
        AccessibilityOverlayBridge.updateTouchBounds(
            if (AccessibilityOverlayBridge.isConnected()) {
                if (expanded) OverlayLayout.orbTouchBounds(config.geometry, safe, density)
                else OverlayLayout.capsuleTouchBounds(config.geometry, safe, density)
            } else {
                null
            },
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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
        val orbBounds = OverlayLayout.expandedBounds(config.geometry, safeBounds(), density)
        val gradientHeight = (orbBounds.bottom + (38f * density).roundToInt())
            .coerceIn(1, physical.height)
        val expanded = stateMachine.state != OverlayState.Collapsed && stateMachine.state != OverlayState.Hidden
        dimView.update(config.container.backgroundDimEnabled && expanded, gradientHeight)
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
            windowManager.removeViewImmediate(textureView)
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
        const val PERMISSION_CHECK_INTERVAL_MS = 15_000L
    }
}
