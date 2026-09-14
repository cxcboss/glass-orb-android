package com.cxcboss.glassorb.overlay

import android.app.ActivityManager
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.Color
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Region
import android.graphics.Matrix
import android.hardware.display.DisplayManager
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
import com.cxcboss.glassorb.render.RenderPresentationFrame
import com.cxcboss.glassorb.render.RenderSnapshot
import com.cxcboss.glassorb.render.ShapeFrame
import com.cxcboss.glassorb.render.ShapeMetrics
import kotlin.math.abs
import kotlin.math.roundToInt
import java.util.function.Consumer

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
    private val collapseFlightSpring = AnalyticSpring(0f, 0.30f, 0.76f)
    private val fullscreenCapsuleSpring = AnalyticSpring(1f, 0.34f, 1f)
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
    // Reuse the input geometry objects across vsyncs. WindowInputRegion copies
    // the values synchronously, so these scratch objects are safe to mutate on
    // the main thread and avoid a Path/Region allocation during every morph.
    private val shapeInputRegion = Region()
    private val shapeInputPath = Path()
    private val shapeInputRect = RectF()
    private val shapeInputClip = Region()
    private val contentMatrix = Matrix()
    private var contentRotation = 0
    private val displayManager = context.getSystemService(DisplayManager::class.java)
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) {
            if (view != null) onConfigurationChanged()
        }
    }
    private var backgroundBlurController: BackgroundBlurController? = null
    private var backgroundAmbientBlurController: BackgroundAmbientBlurController? = null
    private var crossWindowBlurListenerRegistered = false
    private var crossWindowBlurEnabled = false
    private var backgroundBlurRevealStartNanos = Long.MIN_VALUE
    private val crossWindowBlurListener = Consumer<Boolean> { enabled ->
        mainHandler.post {
            crossWindowBlurEnabled = enabled
            updateBackgroundBlur()
        }
    }
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
    private var forceRender = false
    /**
     * WindowManager layout work is intentionally applied on the next display
     * frame. Settings sliders can emit many samples between two vsyncs; doing
     * a full host/touch-window update for every sample makes the compositor
     * compete with the renderer and produces visible judder. The config field
     * remains live immediately, while this latest-value queue coalesces the
     * expensive part.
     */
    private val pendingLayoutUpdates = LatestFrameUpdate<OrbConfig>()
    private var showDarkCapsuleOutline = false
    private var lastStableDisplayGeometry: RotationDisplayGeometry? = null
    private var collapseReleaseVelocityDpPerSecond = 0f
    private var collapseDirection = 0
    private var systemAssistantWindowVisible = false
    private var assistantTriggeredExpansion = false
    private var statusBarHidden = false
    private var fullscreenAutoHidden = false
    private var fullscreenTransition = FullscreenCapsulePhase.None
    private val thinkingTimeout = Runnable {
        stateMachine.onThinkingTimeout()
        thinkingSpring.target = 0f
        markStateStart()
        scheduleAutoCollapse()
    }

    private val autoCollapseTimeout = Runnable { beginAutomaticCollapse() }

    private val assistantCollapseTimeout = object : Runnable {
        override fun run() {
            if (systemAssistantWindowVisible) return
            if (view == null) {
                assistantTriggeredExpansion = false
                return
            }
            when (stateMachine.state) {
                OverlayState.Expanding -> mainHandler.postDelayed(this, ASSISTANT_COLLAPSE_DELAY_MS)
                OverlayState.Wave, OverlayState.Thinking -> {
                    assistantTriggeredExpansion = false
                    beginAutomaticCollapse()
                }
                else -> assistantTriggeredExpansion = false
            }
        }
    }

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

    private val statusBarVisibilityCheck = object : Runnable {
        override fun run() {
            if (view == null || !screenOn || !config.geometry.hideWhenStatusBarHidden) return
            updateStatusBarHidden(!isStatusBarVisible())
            mainHandler.postDelayed(this, STATUS_BAR_VISIBILITY_CHECK_MS)
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
        backgroundBlurRevealStartNanos = Long.MIN_VALUE
        morphSpring.snapTo(0f)
        resetCollapseRebound()
        fullscreenCapsuleSpring.snapTo(1f)
        fullscreenTransition = FullscreenCapsulePhase.None
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
        val display = displayGeometry()
        val portrait = RotationCoordinateSpace.portraitDisplay(display)
        val expandedFrame = OverlayLayout.expandedFrame(config.geometry, portrait, density)
        val bounds = expandedFrame.bounds
        val collapsed = OverlayLayout.collapsedBounds(config.geometry, portrait, density)
        windowMotion.setExpandedWindow(OverlayLayout.anchorOffsets(bounds, collapsed, density))
        val layoutParams = createLayoutParams(bounds, touchable = true)
        val host = FrameLayout(context).apply {
            clipChildren = true
            addView(textureView, FrameLayout.LayoutParams(bounds.width, bounds.height))
        }
        // Keep the host origin and canvas fixed for the entire morph. Moving
        // the window and translating its child are separate compositor updates
        // and can expose one frame with mismatched coordinates.
        val initialHostBounds = RotationCoordinateSpace.mapPortraitRect(
            bounds,
            display.physicalBounds,
            display.rotation,
        )
        val initialHostParams = createLayoutParams(initialHostBounds, touchable = true)
        configureContentTransform(textureView, bounds.width, bounds.height, display.rotation)
        val touchBounds = touchBounds(expanded = false, display = display)
        val target = View(context).apply { setOnTouchListener(::onTouch) }
        val targetParams = createLayoutParams(touchBounds, touchable = true)
        val dimView = BackgroundDimView(context)
        val dimParams = createBackgroundDimParams(display.physicalBounds)
        // Android's official background-blur pipeline reads round-rect corner
        // radii from this floating Window's background. A single, persistent
        // window keeps the blur behind GL without per-frame window creation.
        val blurWindow = AndroidBackgroundBlurWindow(windowContext ?: context, accessibilityWindow)
        val blurController = BackgroundBlurController(blurWindow)
        val ambientBlurController = BackgroundAmbientBlurController(windowContext ?: context, accessibilityWindow)
        return try {
            // Place the static dim layer below the GL surface and touch proxy.
            windowManager.addView(dimView, dimParams)
            ambientBlurController.update(
                enabled = false,
                crossWindowBlurEnabled = false,
                display = display,
                gradientHeightPx = expandedFrame.visualBottomPx,
                blurPercent = 0f,
            )
            // Reserve the compositor layer BEFORE GL; enabling it later must
            // not insert a new window above (and blur) the orb itself.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                blurWindow.show(initialHostBounds, initialHostBounds, 0)
            }
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
            backgroundBlurController = blurController
            backgroundAmbientBlurController = ambientBlurController
            pendingLayoutUpdates.clear()
            registerCrossWindowBlurCapabilityListener()
            displayManager.registerDisplayListener(displayListener, mainHandler)
            AccessibilityOverlayBridge.updateTouchBounds(
                if (AccessibilityOverlayBridge.isConnected()) currentAccessibilityTouchBounds(display) else null,
            )
            val now = System.nanoTime()
            originNanos = now
            stateStartNanos = now
            lastFrameNanos = 0L
            forceRender = true
            updateBackgroundDim(display, expandedFrame)
            postFrame()
            // The accessibility service can observe the assistant before the
            // overlay view is created. Replay that state now so the first
            // visible assistant window is not lost during startup.
            AccessibilityOverlayBridge.replayAssistantWindowState()
            updateStatusBarVisibilityMonitor(display)
            schedulePermissionCheck()
            true
        } catch (error: Throwable) {
            inputRegion?.close()
            inputRegion = null
            runCatching { windowManager.removeViewImmediate(target) }
            runCatching { windowManager.removeViewImmediate(host) }
            runCatching { windowManager.removeViewImmediate(dimView) }
            blurController.destroy()
            ambientBlurController.destroy()
            textureView.release()
            onFatalError(error.message ?: "无法创建悬浮窗")
            false
        }
    }

    fun hide() {
        mainHandler.removeCallbacks(thinkingTimeout)
        mainHandler.removeCallbacks(autoCollapseTimeout)
        mainHandler.removeCallbacks(assistantCollapseTimeout)
        mainHandler.removeCallbacks(statusBarVisibilityCheck)
        systemAssistantWindowVisible = false
        assistantTriggeredExpansion = false
        statusBarHidden = false
        fullscreenAutoHidden = false
        fullscreenTransition = FullscreenCapsulePhase.None
        fullscreenCapsuleSpring.snapTo(1f)
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

    /**
     * Receives only package-filtered assistant window lifecycle signals from
     * the accessibility service. The assistant remains system-owned; the
     * existing accessibility overlay visually covers it while the orb is up.
     */
    internal fun onSystemAssistantWindowChanged(event: SystemAssistantWindowEvent) {
        if (!SystemAssistantPackages.isSupported(event.packageName)) return
        mainHandler.post {
            if (event.visible) {
                systemAssistantWindowVisible = true
                updateFullscreenVisibility()
                mainHandler.removeCallbacks(assistantCollapseTimeout)
                if (view != null && stateMachine.state == OverlayState.Collapsed) {
                    beginExpand(triggeredBySystemAssistant = true)
                }
            } else {
                systemAssistantWindowVisible = false
                updateFullscreenVisibility()
                if (assistantTriggeredExpansion) {
                    mainHandler.removeCallbacks(assistantCollapseTimeout)
                    mainHandler.postDelayed(assistantCollapseTimeout, ASSISTANT_COLLAPSE_DELAY_MS)
                }
            }
        }
    }

    /** Returns the capsule proxy bounds for the status-bar edge case. */
    internal fun currentAccessibilityTouchBounds(
        display: RotationDisplayGeometry = displayGeometry(),
    ): IntRect? {
        if (accessibilityWindow || view == null || fullscreenAutoHidden ||
            fullscreenTransition != FullscreenCapsulePhase.None
        ) return null
        return if (stateMachine.state == OverlayState.Collapsed) {
            OverlayLayout.capsuleTouchBounds(config.geometry, display, density)
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
            updateStatusBarVisibilityMonitor()
            schedulePermissionCheck()
        } else {
            removeFrame()
            mainHandler.removeCallbacks(permissionCheck)
            mainHandler.removeCallbacks(statusBarVisibilityCheck)
        }
        updateBackgroundBlur()
    }

    /**
     * Releases idle producer work when the system reports memory pressure.
     * The last submitted TextureView buffer remains visible, so this does not
     * create a blank-frame flash. A later interaction calls postFrame(), which
     * resumes the submit-driven GL loop before the next visual transition.
     */
    internal fun onMemoryPressure() {
        if (view == null) return
        if (stateMachine.state == OverlayState.Collapsed ||
            stateMachine.state == OverlayState.Hidden ||
            fullscreenAutoHidden
        ) {
            removeFrame()
            view?.setPaused(true)
        }
        forceRender = false
    }

    fun updateConfig(config: OrbConfig) {
        this.config = config.normalized()
        windowMotion.updateMotion(this.config.motion)
        // Keep the latest config available to the render snapshot now, but
        // batch WindowManager mutations until the next choreographer callback.
        // This is especially important while dragging a geometry slider.
        pendingLayoutUpdates.offer(this.config)
        if (stateMachine.state == OverlayState.Wave) scheduleAutoCollapse()
        forceRender = true
        postFrame()
    }

    fun onConfigurationChanged() {
        if (trackingSwipe) cancelGesture()
        pendingLayoutUpdates.offer(config)
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
        applyPendingLayoutUpdate()
        val framePresentation: PresentationGeometry by lazy {
            OverlayLayout.presentationSnapshot(config.geometry, displayGeometry(), density)
        }

        val deltaSeconds = if (lastFrameNanos == 0L) 0f else {
            ((frameTimeNanos - lastFrameNanos) / 1_000_000_000.0).toFloat().coerceIn(0f, 0.05f)
        }
        lastFrameNanos = frameTimeNanos
        val elapsedSeconds = ((frameTimeNanos - originNanos) / 1_000_000_000.0).toFloat()

        var stateElapsedSeconds = ((frameTimeNanos - stateStartNanos) / 1_000_000_000.0).toFloat()

        morphSpring.step(deltaSeconds)
        thinkingSpring.step(deltaSeconds)
        pressSpring.step(deltaSeconds)
        gestureReturnSpring.step(deltaSeconds)
        windowMotion.step(deltaSeconds)
        collapseReboundSpring.step(deltaSeconds)
        collapseSecondaryReboundSpring.step(deltaSeconds)
        collapseFlightSpring.step(deltaSeconds)
        fullscreenCapsuleSpring.step(deltaSeconds)
        if (!trackingSwipe) gestureOffsetDp = gestureReturnSpring.value
        // Keep the wave/dots and off-screen glass scene live throughout the
        // transition. The transition is intentionally a full-rate visual
        // animation; custom idle FPS must never make it jump between frames.
        val collapseEffectsFrozen = shouldFreezeCollapseEffects(
            state = stateMachine.state,
            collapsePull = windowMotion.collapsePull,
        )
        if (!collapseEffectsFrozen && stateMachine.state != OverlayState.Collapsed) {
            bands = AmbientBands.smooth(bands, AmbientBands.targetsAt(elapsedSeconds))
            wavePhase = AmbientBands.advanceWavePhase(wavePhase, bands, deltaSeconds)
        }

        when (fullscreenTransition) {
            FullscreenCapsulePhase.Hiding -> if (isSettled(fullscreenCapsuleSpring, 0f)) {
                finishFullscreenHide(framePresentation)
                return
            }

            FullscreenCapsulePhase.Showing -> if (isSettled(fullscreenCapsuleSpring, 1f)) {
                finishFullscreenShow(framePresentation)
            }

            FullscreenCapsulePhase.None -> Unit
        }

        val stateBeforeAnimationSettle = stateMachine.state
        when (stateMachine.state) {
            OverlayState.Expanding -> if (isSettled(morphSpring, 1f) && stateElapsedSeconds > 0.12f) {
                stateMachine.onAnimationSettled()
                // The expensive background-blur window is enabled only after
                // the morph has settled. Its radius then fades in while the
                // newly populated GL scene is already available.
                backgroundBlurRevealStartNanos = frameTimeNanos
                // The first Wave frame must not be delayed behind the previous
                // expanding snapshot when the producer is frame-throttled.
                forceRender = true
                markStateStart(frameTimeNanos)
                scheduleAutoCollapse()
                updateTouchBounds(expanded = true, display = framePresentation.display)
            }

            OverlayState.Collapsing -> if (
                    isSettled(morphSpring, 0f) &&
                    isSettled(collapseReboundSpring, 0f) &&
                    isSettled(collapseSecondaryReboundSpring, 0f) &&
                    isSettled(collapseFlightSpring, 0f) &&
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
                updateBackgroundDim(framePresentation)
                presentationToken += 1
                // A settled capsule is rendered by a separate shader branch
                // with alpha=1. Always submit that final frame; otherwise the
                // idle optimization can leave the last translucent morph frame
                // on the TextureView indefinitely.
                forceRender = true
                markStateStart(frameTimeNanos)
                updateTouchBounds(expanded = false, display = framePresentation.display)
            }

            else -> Unit
        }

        // Keep this guard for any future state transition added above. A state
        // change must publish at least one snapshot, including the final opaque
        // collapsed capsule.
        if (shouldForceRenderAfterStateChange(stateBeforeAnimationSettle, stateMachine.state)) {
            forceRender = true
        }
        if (stateMachine.state != stateBeforeAnimationSettle) {
            // The settled Wave snapshot is the first frame of the reveal. Do
            // not carry the old Expanding elapsed time into the fade curve.
            stateElapsedSeconds = 0f
        }

        val springProgress = if (stateMachine.state is OverlayState.SwipeTracking) 1f else morphSpring.value
        updateRenderAnchors(springProgress)

        // An active orb must render on every Choreographer callback. Do not
        // derive a target interval here: panel vsyncs can be a few microseconds
        // shorter than the nominal 120 Hz interval, which would skip alternating
        // frames and turn the producer into a 60 Hz stream. The state machine
        // below stops this callback loop only after the capsule is settled.
        // Fullscreen hide/show keeps the logical orb state collapsed, but its
        // capsule visibility is still animated by fullscreenCapsuleSpring.
        // The policy keeps those spring frames flowing and returns to zero
        // producer work once the transition settles.
        val renderDue = shouldSubmitOverlayFrame(
            state = stateMachine.state,
            fullscreenPhase = fullscreenTransition,
            forceRender = forceRender,
        )
        if (renderDue) {
            forceRender = false
            val fullscreenFrame = FullscreenCapsuleMotion.frame(
                phase = fullscreenTransition,
                springValue = fullscreenCapsuleSpring.value,
            )
            val snapshotWithoutPresentation = RenderSnapshot(
                config = config,
                state = stateMachine.state,
                springProgress = springProgress,
                gestureOffsetDp = gestureOffsetDp,
                collapsePull = windowMotion.collapsePull,
                collapseVelocityDpPerSecond = collapseReleaseVelocityDpPerSecond,
                collapseDirection = collapseDirection,
                collapseFlightOffsetPx = collapseFlightSpring.value,
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
                fullscreenCapsuleScale = fullscreenFrame.scale,
                fullscreenCapsuleSquareProgress = fullscreenFrame.squareProgress,
                fullscreenCapsuleOpacity = fullscreenFrame.opacity,
            )
            val renderPresentation = RenderPresentationFrame.from(
                snapshot = snapshotWithoutPresentation,
                presentation = framePresentation,
                density = density,
            )
            val snapshot = snapshotWithoutPresentation.apply { presentationFrame = renderPresentation }
            updateShapeInputRegion(snapshot)
            updateBackgroundDim(renderPresentation)
            textureView.submit(snapshot)
        }
        val pressIsSettled = isSettled(pressSpring, pressSpring.target)
        if (shouldContinueOverlayFrames(stateMachine.state, pressIsSettled) ||
            fullscreenTransition != FullscreenCapsulePhase.None
        ) {
            postFrame()
        } else {
            // Keep the last opaque capsule buffer on screen without holding a
            // Choreographer loop. GlRenderLoop is submit-driven, so it becomes
            // idle after this final submitted frame without a racing pause.
            lastFrameNanos = 0L
        }
    }

    private fun applyPendingLayoutUpdate() {
        val latestConfig = pendingLayoutUpdates.take() ?: return
        if (view == null) return
        // A newer value can replace several slider samples before this point;
        // publish the exact same value to every dependent geometry calculation.
        config = latestConfig
        val display = displayGeometry()
        val expandedFrame = OverlayLayout.expandedFrame(config.geometry, display, density)
        updateBounds(display, expandedFrame)
        updateTouchBounds(stateMachine.state != OverlayState.Collapsed, display)
        updateStatusBarVisibilityMonitor(display)
        updateFullscreenVisibility(display, expandedFrame)
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
                assistantTriggeredExpansion = false
                mainHandler.removeCallbacks(assistantCollapseTimeout)
                downRawX = event.rawX
                downRawY = event.rawY
                velocityTracker?.recycle()
                velocityTracker = VelocityTracker.obtain()
                trackScreenVelocity(event)
                pressSpring.target = 1f
                windowMotion.cancelGesture()
                // Do not wait for the previous cadence interval when a new
                // interaction starts. The next Choreographer callback must
                // publish the pressed frame immediately.
                forceRender = true
                postFrame()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                trackScreenVelocity(event)
                val delta = RotationCoordinateSpace.currentVectorToPortrait(
                    event.rawX - downRawX, event.rawY - downRawY, contentRotation,
                )
                val deltaXPx = delta.x
                val deltaPx = delta.y
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
                trackScreenVelocity(event)
                pressSpring.target = 0f
                if (!cancelledByMultitouch && trackingSwipe) {
                    val portraitDelta = RotationCoordinateSpace.currentVectorToPortrait(
                        event.rawX - downRawX, event.rawY - downRawY, contentRotation,
                    )
                    val deltaXPx = portraitDelta.x
                    velocityTracker?.computeCurrentVelocity(1_000)
                    val velocity = RotationCoordinateSpace.currentVectorToPortrait(
                        velocityTracker?.xVelocity ?: 0f, velocityTracker?.yVelocity ?: 0f, contentRotation,
                    )
                    val velocityXDp = velocity.x / density
                    val velocityDp = velocity.y / density
                    // collapsePull is smoothed on the render clock. A quick
                    // swipe can release before a render frame observes it,
                    // so base the release decision on the immediate physical
                    // gesture as well. This is especially visible after a
                    // rotation, where the touch proxy has just been rebuilt.
                    val closingGesture = portraitDelta.y < 0f &&
                        -portraitDelta.y >= kotlin.math.abs(portraitDelta.x) * 0.35f
                    val decision = if (closingGesture || windowMotion.collapsePull > 0f) {
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
            OverlayState.Wave, OverlayState.Thinking -> {
                assistantTriggeredExpansion = false
                beginThinking()
            }
            else -> Unit
        }
    }

    private fun beginExpand(triggeredBySystemAssistant: Boolean = false) {
        assistantTriggeredExpansion = triggeredBySystemAssistant
        collapseReleaseVelocityDpPerSecond = 0f
        collapseDirection = 0
        resetCollapseRebound()
        mainHandler.removeCallbacks(autoCollapseTimeout)
        stateMachine.onTap()
        backgroundBlurRevealStartNanos = Long.MIN_VALUE
        updateBackgroundDim()
        updateTouchBounds(expanded = true)
        configureExpandSpring()
        morphSpring.snapTo(0f)
        morphSpring.target = 1f
        markStateStart()
        forceRender = true
        // Assistant callbacks enter directly from the accessibility service,
        // outside the touch path that normally schedules the next vsync.
        // Always kick the frame loop here so the expansion cannot remain
        // stuck in Expanding with a stale capsule buffer.
        postFrame()
    }

    private fun beginThinking() {
        mainHandler.removeCallbacks(autoCollapseTimeout)
        stateMachine.onTap()
        thinkingSpring.configure(0.34f, 0.82f)
        thinkingSpring.target = 1f
        mainHandler.removeCallbacks(thinkingTimeout)
        mainHandler.postDelayed(thinkingTimeout, config.motion.thinkingDurationMs.toLong())
        markStateStart()
        forceRender = true
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
        collapseFlightSpring.configure(
            if (config.geometry.expandBelowCapsule) 0.34f else 0.30f,
            if (config.geometry.expandBelowCapsule) 0.72f else 0.76f,
        )
        collapseReboundSpring.seed(0f, impulse * directionalBoost, 0f)
        collapseSecondaryReboundSpring.seed(0f, -impulse * 0.42f, 0f)
        val flightVelocityPxPerSecond = (65f + speedFactor * 180f) *
            if (config.geometry.expandBelowCapsule) 1.10f else 0.95f
        collapseFlightSpring.seed(0f, -flightVelocityPxPerSecond, 0f)
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
        collapseFlightSpring.snapTo(0f)
    }

    private fun finishTouch() {
        trackingSwipe = false
        velocityTracker?.recycle()
        velocityTracker = null
    }

    private fun trackScreenVelocity(event: MotionEvent) {
        // TextureView dispatch already transforms local coordinates. Feed
        // raw screen coordinates so the inverse rotation is applied once.
        val screenEvent = MotionEvent.obtain(event)
        screenEvent.setLocation(event.rawX, event.rawY)
        velocityTracker?.addMovement(screenEvent)
        screenEvent.recycle()
    }

    private fun updateBounds(
        display: RotationDisplayGeometry,
        expandedFrame: ExpandedFrameGeometry,
    ) {
        val textureView = view ?: return
        val layoutParams = params ?: return
        val portrait = RotationCoordinateSpace.portraitDisplay(display)
        val bounds = OverlayLayout.expandedFrame(config.geometry, portrait, density).bounds
        val collapsed = OverlayLayout.collapsedBounds(config.geometry, portrait, density)
        configureContentTransform(textureView, bounds.width, bounds.height, display.rotation)
        val layoutChanged = layoutParams.width != bounds.width || layoutParams.height != bounds.height ||
            layoutParams.x != bounds.left || layoutParams.y != bounds.top
        if (!layoutChanged) {
            windowMotion.setExpandedWindow(OverlayLayout.anchorOffsets(bounds, collapsed, density))
            updateHostBounds(display)
            updateBackgroundDim(display, expandedFrame)
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
            updateHostBounds(display)
            updateBackgroundDim(display, expandedFrame)
        } catch (error: Throwable) {
            onFatalError(error.message ?: "无法更新悬浮窗位置")
        }
    }

    /** Only configuration/display changes may move the host, never a morph. */
    private fun updateHostBounds(display: RotationDisplayGeometry) {
        val host = renderHost ?: return
        val hostLayout = hostParams ?: return
        val canvas = params ?: return
        val bounds = RotationCoordinateSpace.mapPortraitRect(
            IntRect(canvas.x, canvas.y, canvas.x + canvas.width, canvas.y + canvas.height),
            display.physicalBounds, display.rotation,
        )
        if (hostLayout.x == bounds.left && hostLayout.y == bounds.top &&
            hostLayout.width == bounds.width && hostLayout.height == bounds.height) return
        hostLayout.x = bounds.left
        hostLayout.y = bounds.top
        hostLayout.width = bounds.width
        hostLayout.height = bounds.height
        runCatching { windowManager.updateViewLayout(host, hostLayout) }
            .onFailure { onFatalError(it.message ?: "无法更新渲染窗口") }
    }

    private fun configureContentTransform(texture: View, width: Int, height: Int, rotation: Int) {
        contentRotation = rotation
        val tx = if (rotation == 2) width.toFloat() else if (rotation == 3) height.toFloat() else 0f
        val ty = if (rotation == 1) width.toFloat() else if (rotation == 2) height.toFloat() else 0f
        val degrees = RotationCoordinateSpace.contentRotationDegrees(rotation)
        texture.pivotX = 0f
        texture.pivotY = 0f
        texture.rotation = degrees
        texture.translationX = tx
        texture.translationY = ty
        contentMatrix.setRotate(degrees)
        contentMatrix.postTranslate(tx, ty)
    }

    private fun scheduleAutoCollapse() {
        mainHandler.removeCallbacks(autoCollapseTimeout)
        // An expansion caused by the system assistant belongs to the
        // assistant conversation, not to the user's normal orb timeout. If
        // the regular 4-second timer is armed here, it collapses the orb while
        // Xiaomi/other OEM assistants are still visible; their next window
        // update then expands it again, producing a close/reopen loop.
        if (config.motion.autoCollapseEnabled &&
            stateMachine.state == OverlayState.Wave &&
            !(assistantTriggeredExpansion && systemAssistantWindowVisible)
        ) {
            mainHandler.postDelayed(autoCollapseTimeout, (config.motion.autoCollapseSeconds * 1_000f).roundToInt().toLong())
        }
    }

    private fun beginAutomaticCollapse() {
        if (stateMachine.state != OverlayState.Wave && stateMachine.state != OverlayState.Thinking) return
        assistantTriggeredExpansion = false
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

    private fun updateTouchBounds(
        expanded: Boolean,
        display: RotationDisplayGeometry = displayGeometry(),
    ) {
        val target = touchView ?: return
        // The GL host supplies the exact animated shape region while expanded.
        // A rectangular proxy would intercept the transparent circle corners.
        target.visibility = if (expanded || fullscreenAutoHidden ||
            fullscreenTransition != FullscreenCapsulePhase.None
        ) View.INVISIBLE else View.VISIBLE
        val layoutParams = touchParams ?: return
        val bounds = touchBounds(expanded, display)
        AccessibilityOverlayBridge.updateTouchBounds(
            if (AccessibilityOverlayBridge.isConnected() && !accessibilityWindow) {
                if (expanded || fullscreenAutoHidden ||
                    fullscreenTransition != FullscreenCapsulePhase.None
                ) null else OverlayLayout.capsuleTouchBounds(config.geometry, display, density)
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

    private fun updateStatusBarVisibilityMonitor(display: RotationDisplayGeometry? = null) {
        mainHandler.removeCallbacks(statusBarVisibilityCheck)
        if (view == null || !screenOn) return
        if (!config.geometry.hideWhenStatusBarHidden) {
            updateStatusBarHidden(false, display)
            return
        }
        updateStatusBarHidden(!isStatusBarVisible(), display)
        mainHandler.postDelayed(statusBarVisibilityCheck, STATUS_BAR_VISIBILITY_CHECK_MS)
    }

    private fun updateStatusBarHidden(
        hidden: Boolean,
        display: RotationDisplayGeometry? = null,
    ) {
        if (statusBarHidden == hidden) return
        statusBarHidden = hidden
        updateFullscreenVisibility(display ?: displayGeometry())
    }

    /**
     * Auto-hiding is a visibility/input policy, not a service lifecycle change.
     * Keep the overlay service alive so a system assistant can immediately
     * reveal the orb even while the foreground app is immersive fullscreen.
     */
    private fun updateFullscreenVisibility(
        display: RotationDisplayGeometry = displayGeometry(),
        expandedFrame: ExpandedFrameGeometry? = null,
    ) {
        val hiddenByFullscreen = config.geometry.hideWhenStatusBarHidden && statusBarHidden
        val hiddenByLandscape = config.geometry.hideWhenLandscape &&
            display.physicalBounds.width > display.physicalBounds.height
        val shouldHide = (hiddenByFullscreen || hiddenByLandscape) && !systemAssistantWindowVisible
        val targetPhase = if (shouldHide) {
            FullscreenCapsulePhase.Hiding
        } else {
            FullscreenCapsulePhase.Showing
        }
        if (fullscreenAutoHidden == shouldHide && fullscreenTransition == FullscreenCapsulePhase.None) return
        if (fullscreenAutoHidden == shouldHide && fullscreenTransition == targetPhase) return
        fullscreenAutoHidden = shouldHide
        fullscreenTransition = targetPhase

        val currentVisibility = fullscreenCapsuleSpring.value.coerceIn(0f, 1f)

        if (shouldHide) {
            fullscreenCapsuleSpring.configure(FULLSCREEN_HIDE_RESPONSE, FULLSCREEN_HIDE_DAMPING)
            fullscreenCapsuleSpring.seed(
                currentVisibility,
                fullscreenCapsuleSpring.velocity.coerceAtMost(0f),
                0f,
            )
            mainHandler.removeCallbacks(thinkingTimeout)
            mainHandler.removeCallbacks(autoCollapseTimeout)
            mainHandler.removeCallbacks(assistantCollapseTimeout)
            assistantTriggeredExpansion = false
            trackingSwipe = false
            velocityTracker?.recycle()
            velocityTracker = null
            windowMotion.cancelGesture()
            stateMachine.showCollapsed()
            morphSpring.snapTo(0f)
            thinkingSpring.snapTo(0f)
            pressSpring.snapTo(0f)
            gestureReturnSpring.snapTo(0f)
            gestureOffsetDp = 0f
            capsuleCenterOffsetDp = 0f
            capsuleTopOffsetDp = 0f
            resetCollapseRebound()
            inputRegion?.update(Region())
            renderHost?.visibility = View.VISIBLE
            backgroundDimView?.visibility = View.VISIBLE
            updateBackgroundDim(display, expandedFrame)
        } else {
            fullscreenCapsuleSpring.configure(FULLSCREEN_SHOW_RESPONSE, FULLSCREEN_SHOW_DAMPING)
            fullscreenCapsuleSpring.seed(
                currentVisibility,
                fullscreenCapsuleSpring.velocity.coerceAtLeast(0f),
                1f,
            )
            renderHost?.visibility = View.VISIBLE
            backgroundDimView?.visibility = View.VISIBLE
            updateBackgroundDim(display, expandedFrame)
        }
        inputRegion?.update(Region())
        updateTouchBounds(stateMachine.state != OverlayState.Collapsed, display)
        forceRender = true
        postFrame()
    }

    private fun finishFullscreenHide(presentation: PresentationGeometry) {
        fullscreenCapsuleSpring.snapTo(0f)
        fullscreenTransition = FullscreenCapsulePhase.None
        renderHost?.visibility = View.INVISIBLE
        backgroundDimView?.hideImmediately()
        backgroundDimView?.visibility = View.INVISIBLE
        inputRegion?.update(Region())
        updateTouchBounds(expanded = false, display = presentation.display)
        lastFrameNanos = 0L
        removeFrame()
    }

    private fun finishFullscreenShow(presentation: PresentationGeometry) {
        fullscreenCapsuleSpring.snapTo(1f)
        fullscreenTransition = FullscreenCapsulePhase.None
        renderHost?.visibility = View.VISIBLE
        backgroundDimView?.visibility = View.VISIBLE
        updateBackgroundDim(presentation)
        updateTouchBounds(stateMachine.state != OverlayState.Collapsed, presentation.display)
        forceRender = true
    }

    private fun isStatusBarVisible(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return windowManager.currentWindowMetrics.windowInsets.isVisible(
                WindowInsets.Type.statusBars(),
            )
        }
        @Suppress("DEPRECATION")
        return view?.rootWindowInsets?.systemWindowInsetTop?.let { it > 0 } ?: true
    }

    private fun updateShapeInputRegion(
        snapshot: RenderSnapshot,
    ) {
        val canvas = params ?: return
        val frame = snapshot.presentationFrame ?: return
        val display = frame.presentation.display
        if (fullscreenAutoHidden || fullscreenTransition != FullscreenCapsulePhase.None) {
            shapeInputRegion.setEmpty()
            inputRegion?.update(shapeInputRegion)
            return
        }
        if (snapshot.state == OverlayState.Collapsing && snapshot.collapseEffectsFrozen) {
            // The user cannot meaningfully interact with the orb while it is
            // closing. Keep the last region and avoid rebuilding a Path/Region
            // on every frame; the settled capsule frame updates it below.
            return
        }
        val region = shapeInputRegion
        region.setEmpty()
        if (snapshot.state == OverlayState.Collapsed) {
            val bounds = OverlayLayout.capsuleTouchBounds(config.geometry, display, density)
            val host = hostParams ?: return
            region.set(bounds.left - host.x, bounds.top - host.y,
                bounds.right - host.x, bounds.bottom - host.y)
        } else {
            val shape = frame.shape
            shapeInputRect.set(
                shape.centerX - shape.shapeWidth / 2f,
                shape.top,
                shape.centerX + shape.shapeWidth / 2f,
                shape.top + shape.shapeHeight,
            )
            shapeInputPath.reset()
            shapeInputPath.addOval(shapeInputRect, Path.Direction.CW)
            shapeInputPath.transform(contentMatrix)
            val host = hostParams ?: return
            shapeInputClip.set(0, 0, host.width, host.height)
            region.setPath(shapeInputPath, shapeInputClip)
        }
        inputRegion?.update(region)
    }

    private fun displayGeometry(): RotationDisplayGeometry {
        @Suppress("DEPRECATION")
        val display = windowManager.defaultDisplay
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Metrics and rotation are read as one pair. The fast path returns
            // the immutable previous snapshot when nothing changed, avoiding
            // three temporary sample objects on every display-vsync callback.
            val rotationBefore = display.rotation
            val metrics = windowManager.currentWindowMetrics
            val metricsBounds = metrics.bounds
            val insets = metrics.windowInsets.getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout(),
            )
            val rotationAfter = display.rotation
            val previous = lastStableDisplayGeometry
            val unchanged = previous != null && previous.rotation == rotationBefore &&
                previous.physicalBounds.left == metricsBounds.left &&
                previous.physicalBounds.top == metricsBounds.top &&
                previous.physicalBounds.right == metricsBounds.right &&
                previous.physicalBounds.bottom == metricsBounds.bottom &&
                previous.systemInsets.left == insets.left &&
                previous.systemInsets.top == insets.top &&
                previous.systemInsets.right == insets.right &&
                previous.systemInsets.bottom == insets.bottom
            if (rotationBefore == rotationAfter && unchanged) {
                previous
            } else {
                val physicalBounds = IntRect(
                    metricsBounds.left,
                    metricsBounds.top,
                    metricsBounds.right,
                    metricsBounds.bottom,
                )
                val systemInsets = IntRect(insets.left, insets.top, insets.right, insets.bottom)
                val candidate = RotationDisplayGeometry(
                    physicalBounds = physicalBounds,
                    safeBounds = OverlayLayout.safeBoundsForDisplay(
                        displayBounds = physicalBounds,
                        systemInsets = systemInsets,
                        landscape = physicalBounds.width > physicalBounds.height,
                    ),
                    systemInsets = systemInsets,
                    rotation = rotationBefore,
                )
                if (rotationBefore == rotationAfter) lastStableDisplayGeometry = candidate
                if (rotationBefore == rotationAfter || previous == null) candidate else previous
            }
        } else {
            @Suppress("DEPRECATION")
            val rotation = display.rotation
            val point = Point().also(display::getRealSize)
            val physicalBounds = IntRect(0, 0, point.x, point.y)
            val landscape = physicalBounds.width > physicalBounds.height
            val systemInsets = legacyDisplayInsets(display, landscape, rotation)
            val geometry = RotationDisplayGeometry(
                physicalBounds = physicalBounds,
                safeBounds = OverlayLayout.safeBoundsForDisplay(
                    displayBounds = physicalBounds,
                    systemInsets = systemInsets,
                    landscape = landscape,
                ),
                systemInsets = systemInsets,
                rotation = rotation,
            )
            lastStableDisplayGeometry = geometry
            geometry
        }
    }

    @Suppress("DEPRECATION")
    private fun legacyDisplayInsets(
        display: android.view.Display,
        landscape: Boolean,
        rotation: Int,
    ): IntRect {
        // Once the overlay view exists, its root insets are the most accurate
        // source on API 29, including OEM navigation bars and cutouts.
        view?.rootWindowInsets?.let { rootInsets ->
            return IntRect(
                left = rootInsets.systemWindowInsetLeft,
                top = rootInsets.systemWindowInsetTop,
                right = rootInsets.systemWindowInsetRight,
                bottom = rootInsets.systemWindowInsetBottom,
            )
        }

        // During initial creation there is no root view yet. Reconstruct the
        // stable API 29 system-bar geometry from the framework navigation
        // dimensions while the overlay is being created. Cutout insets are
        // supplied by the root-view branch above once the overlay is attached.
        val navigationWidth = systemDimension("navigation_bar_width")
        val navigationHeight = systemDimension("navigation_bar_height")
        val navigationLeft = if (landscape && rotation == android.view.Surface.ROTATION_270) navigationWidth else 0
        val navigationRight = if (landscape && rotation == android.view.Surface.ROTATION_90) navigationWidth else 0
        return IntRect(
            left = navigationLeft,
            top = 0,
            right = navigationRight,
            bottom = if (landscape) 0 else navigationHeight,
        )
    }

    private fun touchBounds(expanded: Boolean, display: RotationDisplayGeometry): IntRect {
        val visualBounds = if (expanded) OverlayLayout.orbTouchBounds(config.geometry, display, density)
        else OverlayLayout.capsuleTouchBounds(config.geometry, display, density)
        if (AccessibilityOverlayBridge.isConnected()) return visualBounds
        return OverlayLayout.moveTouchBelowProtectedTop(
            bounds = visualBounds,
            blockedBottomPx = protectedTopInsetPx(display),
            limitBottomPx = display.safeBounds.bottom,
        )
    }

    private fun protectedTopInsetPx(display: RotationDisplayGeometry): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return display.physicalBounds.top + display.systemInsets.top
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
        // Use physical-left anchoring. START is layout-direction aware and
        // can mirror the x origin on a rotated RTL configuration.
        gravity = Gravity.TOP or Gravity.LEFT
        x = bounds.left
        y = bounds.top
        OverlayRefreshRatePolicy.apply(
            params = this,
            display = displayManager.getDisplay(android.view.Display.DEFAULT_DISPLAY),
        )
        title = "Glass orb overlay"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) setFitInsetsTypes(0)
    }

    private fun createBackgroundDimParams(physical: IntRect): WindowManager.LayoutParams {
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
            gravity = Gravity.TOP or Gravity.LEFT
            x = physical.left
            y = physical.top
            OverlayRefreshRatePolicy.apply(
                params = this,
                display = displayManager.getDisplay(android.view.Display.DEFAULT_DISPLAY),
            )
            // Keep the separate non-touchable dim window under Android's
            // Keep the separate non-touchable dim window below Android's
            // untrusted-touch opacity threshold. The view shader itself uses
            // solid black at the gradient start; the configured dim amount is
            // applied to this window alpha when the overlay updates.
            alpha = 0.28f
            title = "Glass orb background dim"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                setFitInsetsTypes(0)
            }
        }
    }

    private fun updateBackgroundDim(
        display: RotationDisplayGeometry = displayGeometry(),
        expandedFrame: ExpandedFrameGeometry? = null,
        shape: ShapeFrame? = null,
    ) {
        updateBackgroundBlur(display, shape)
        val dimView = backgroundDimView ?: return
        val dimParams = backgroundDimParams ?: return
        val physical = display.physicalBounds
        val portrait = RotationCoordinateSpace.portraitDisplay(display)
        val gradientBottom = OverlayLayout.expandedFrame(
            config.geometry,
            portrait,
            density,
        ).visualBottomPx
        val gradientHeight = gradientBottom.coerceIn(1, portrait.physicalBounds.height)
        val expanded = stateMachine.state != OverlayState.Collapsed &&
            stateMachine.state != OverlayState.Hidden && stateMachine.state != OverlayState.Collapsing
        dimView.update(
            config.container.backgroundDimEnabled && expanded,
            gradientHeight,
            dimTransitionDurationMs(),
            display.rotation,
        )
        backgroundAmbientBlurController?.update(
            enabled = config.container.backgroundAmbientBlurEnabled && expanded,
            crossWindowBlurEnabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                crossWindowBlurEnabled,
            display = display,
            gradientHeightPx = gradientHeight,
            blurPercent = config.container.backgroundAmbientBlurPercent,
        )
        val requestedDimAlpha = (config.container.backgroundDimPercent / 100f).coerceIn(0f, 0.6f)
        val changed = dimParams.width != physical.width || dimParams.height != physical.height ||
            dimParams.x != physical.left || dimParams.y != physical.top ||
            abs(dimParams.alpha - requestedDimAlpha) > 0.001f
        if (changed) {
            dimParams.width = physical.width
            dimParams.height = physical.height
            dimParams.x = physical.left
            dimParams.y = physical.top
            dimParams.alpha = requestedDimAlpha
            runCatching { windowManager.updateViewLayout(dimView, dimParams) }
                .onFailure { onFatalError(it.message ?: "无法更新背景遮罩") }
        }
    }

    private fun updateBackgroundDim(
        presentation: PresentationGeometry,
    ) {
        updateBackgroundDim(presentation.display, presentation.expandedFrame)
    }

    private fun updateBackgroundDim(frame: RenderPresentationFrame) {
        updateBackgroundDim(frame.presentation.display, frame.presentation.expandedFrame, frame.shape)
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

    private fun removeView() {
        removeFrame()
        mainHandler.removeCallbacks(permissionCheck)
        unregisterCrossWindowBlurCapabilityListener()
        displayManager.unregisterDisplayListener(displayListener)
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
        backgroundBlurController?.destroy()
        backgroundBlurController = null
        backgroundAmbientBlurController?.destroy()
        backgroundAmbientBlurController = null
        AccessibilityOverlayBridge.updateTouchBounds(null)
        if (textureView == null) return
        try {
            windowManager.removeViewImmediate(host ?: textureView)
        } catch (_: Throwable) {
            textureView.release()
        }
    }

    private fun updateBackgroundBlur(
        display: RotationDisplayGeometry = displayGeometry(),
        shape: ShapeFrame? = null,
    ) {
        val controller = backgroundBlurController ?: return
        val expanded = stateMachine.state != OverlayState.Collapsed && stateMachine.state != OverlayState.Hidden
        val blurVisibility = backgroundBlurVisibilityFactor()
        val visible = view != null && screenOn && expanded &&
            !fullscreenAutoHidden && fullscreenTransition == FullscreenCapsulePhase.None &&
            blurVisibility > 0f
        val apertureBounds = if (visible) {
            shape?.let { animatedBlurAperture(it, display) }
                ?: OverlayLayout.orbTouchBounds(config.geometry, display, density)
        } else null
        // The floating background window itself has these bounds. Its official
        // round-rect drawable turns a square into a true circular blur and a
        // deformed glass shape into a pill, with layout updates coalesced.
        val windowBounds = apertureBounds
        controller.update(
            BackgroundBlurRequest(
                userEnabled = config.container.backgroundBlurEnabled,
                apiLevel = Build.VERSION.SDK_INT,
                crossWindowBlurEnabled = crossWindowBlurEnabled,
                visible = visible,
                windowBounds = windowBounds,
                apertureBounds = apertureBounds,
                blurRadiusPx = (
                    BackgroundBlurPolicy.radiusPxFromPercent(config.container.backgroundBlurPercent) *
                        blurVisibility
                    ).roundToInt(),
                shape = BackgroundBlurPolicy.shapeFor(config.geometry),
            ),
        )
    }

    private fun backgroundBlurVisibilityFactor(): Float = when (stateMachine.state) {
        OverlayState.Expanding,
        OverlayState.Collapsing,
        OverlayState.Collapsed,
        OverlayState.Hidden,
        -> 0f

        OverlayState.Wave,
        OverlayState.Thinking,
        -> {
            val startedAt = backgroundBlurRevealStartNanos
            if (startedAt == Long.MIN_VALUE) {
                1f
            } else {
                val elapsed = (System.nanoTime() - startedAt).coerceAtLeast(0L).toFloat() /
                    BLUR_REVEAL_DURATION_NANOS
                smoothStep(elapsed)
            }
        }

        is OverlayState.SwipeTracking -> 1f
    }

    private fun smoothStep(value: Float): Float {
        val t = value.coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    /** Maps the canonical GL shape to the physical display without changing its orientation. */
    private fun animatedBlurAperture(shape: ShapeFrame, display: RotationDisplayGeometry): IntRect? {
        val canvas = params ?: return null
        val left = (shape.centerX - shape.shapeWidth * 0.5f).roundToInt()
        val top = shape.top.roundToInt()
        val portraitBounds = IntRect(
            canvas.x + left,
            canvas.y + top,
            canvas.x + left + shape.shapeWidth.roundToInt(),
            canvas.y + top + shape.shapeHeight.roundToInt(),
        )
        return RotationCoordinateSpace.mapPortraitRect(
            portraitBounds,
            display.physicalBounds,
            display.rotation,
        )
    }

    private fun registerCrossWindowBlurCapabilityListener() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || crossWindowBlurListenerRegistered) return
        runCatching {
            crossWindowBlurEnabled = windowManager.isCrossWindowBlurEnabled
            windowManager.addCrossWindowBlurEnabledListener(context.mainExecutor, crossWindowBlurListener)
            crossWindowBlurListenerRegistered = true
        }.onFailure { crossWindowBlurEnabled = false }
    }

    private fun unregisterCrossWindowBlurCapabilityListener() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || !crossWindowBlurListenerRegistered) return
        runCatching { windowManager.removeCrossWindowBlurEnabledListener(crossWindowBlurListener) }
        crossWindowBlurListenerRegistered = false
        crossWindowBlurEnabled = false
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
        const val BLUR_REVEAL_DURATION_NANOS = 180_000_000f
        const val PERMISSION_CHECK_INTERVAL_MS = 15_000L
        const val STATUS_BAR_VISIBILITY_CHECK_MS = 200L
        // Response values are tuned against the settled thresholds below:
        // both fullscreen/landscape directions are approximately 1300ms.
        const val FULLSCREEN_HIDE_RESPONSE = 0.84f
        const val FULLSCREEN_HIDE_DAMPING = 1f
        const val FULLSCREEN_SHOW_RESPONSE = 0.78f
        const val FULLSCREEN_SHOW_DAMPING = 0.64f
        const val ASSISTANT_COLLAPSE_DELAY_MS = 420L
    }
}
