package com.kyant.backdrop.catalog.components

import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import com.cxcboss.glassorb.ui.sliderValueAt
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.catalog.utils.DampedDragAnimation
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import com.cxcboss.glassorb.ui.SliderGestureAxis
import com.cxcboss.glassorb.ui.sliderGestureAxis
import com.cxcboss.glassorb.ui.shouldCommitSliderTap
import kotlinx.coroutines.flow.collectLatest

@Composable
fun LiquidSlider(
    value: () -> Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    visibilityThreshold: Float,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    onInteractionChange: (Boolean) -> Unit = {},
    defaultFraction: Float? = null,
) {
    val isLightTheme = !isSystemInDarkTheme()
    val accentColor =
        if (isLightTheme) Color(0xFF007AFF)
        else Color(0xFF0A84FF)
    val trackColor =
        if (isLightTheme) Color(0xFF787878).copy(0.2f)
        else Color(0xFF787880).copy(0.36f)

    val trackBackdrop = rememberLayerBackdrop()
    val latestValue by rememberUpdatedState(value)
    val latestChange by rememberUpdatedState(onValueChange)
    val latestInteraction by rememberUpdatedState(onInteractionChange)
    val capsule = remember { Capsule() }

    BoxWithConstraints(
        modifier.fillMaxWidth().height(44.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        val trackWidth = constraints.maxWidth

        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val animationScope = rememberCoroutineScope()
        val dampedDragAnimation = remember(animationScope, valueRange, visibilityThreshold) {
            DampedDragAnimation(
                animationScope = animationScope, initialValue = latestValue().coerceIn(valueRange),
                valueRange = valueRange, visibilityThreshold = visibilityThreshold,
                initialScale = 1f, pressedScale = 1.5f,
                onDragStarted = {}, onDragStopped = {}, onDrag = { _, _ -> },
            )
        }

        LaunchedEffect(dampedDragAnimation) {
            snapshotFlow { latestValue() }
                .collectLatest { value ->
                    if (dampedDragAnimation.targetValue != value) {
                        dampedDragAnimation.updateValue(value)
                    }
                }
        }

        Box(
            Modifier.fillMaxWidth().height(44.dp)
                .semantics {
                    progressBarRangeInfo = ProgressBarRangeInfo(latestValue().coerceIn(valueRange), valueRange)
                    setProgress { latestChange(it.coerceIn(valueRange)); true }
                }
                .pointerInput(dampedDragAnimation, isLtr, valueRange) {
                    awaitEachGesture {
                        // Keep the initial down unconsumed so a vertical swipe
                        // starting on this full-width target can be claimed by
                        // the parent LazyColumn after touch slop.
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val touchSlop = viewConfiguration.touchSlop
                        var dragging = false
                        fun update(x: Float) {
                            val next = sliderValueAt(x, size.width.toFloat(), valueRange, !isLtr)
                            dampedDragAnimation.updateValue(next)
                            latestChange(next)
                        }
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (event.changes.count { it.pressed } > 1) break
                                if (!change.pressed) {
                                    // A stationary press is a track tap. Commit
                                    // it only on a real up. Cancellation is
                                    // represented by a consumed up-like event
                                    // and must not persist a new value.
                                    if (shouldCommitSliderTap(dragging, change.pressed, change.isConsumed)) {
                                        update(change.position.x)
                                    }
                                    break
                                }
                                if (change.isConsumed) break

                                if (!dragging) {
                                    when (sliderGestureAxis(
                                        change.position.x - down.position.x,
                                        change.position.y - down.position.y,
                                        touchSlop,
                                    )) {
                                        SliderGestureAxis.Undecided -> continue
                                        SliderGestureAxis.Vertical -> break
                                        SliderGestureAxis.Horizontal -> {
                                            dragging = true
                                            latestInteraction(true)
                                            dampedDragAnimation.press()
                                        }
                                    }
                                }
                                update(change.position.x)
                                change.consume()
                            }
                        } finally {
                            if (dragging) {
                                latestInteraction(false)
                                dampedDragAnimation.release()
                            }
                        }
                    }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
        Box(Modifier.layerBackdrop(trackBackdrop)) {
            Box(
                Modifier
                    .clip(capsule)
                    .background(trackColor)
                    .height(6f.dp)
                    .fillMaxWidth()
            )

            Box(
                Modifier
                    .clip(capsule)
                    .background(accentColor)
                    .height(6f.dp)
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        val width = (constraints.maxWidth * dampedDragAnimation.progress).fastRoundToInt()
                        layout(width, placeable.height) {
                            placeable.place(0, 0)
                        }
                    }
            )
        }

        defaultFraction?.let { fraction ->
            Canvas(Modifier.fillMaxWidth().height(8.dp)) {
                val x = size.width * if (isLtr) fraction else 1f - fraction
                drawLine(if (isLightTheme) Color.Black.copy(alpha = .4f) else Color.White.copy(alpha = .5f),
                    Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
            }
        }

        Box(
            Modifier
                .graphicsLayer {
                    translationX =
                        (-size.width / 2f + trackWidth * dampedDragAnimation.progress)
                            .fastCoerceIn(-size.width / 4f, trackWidth - size.width * 3f / 4f) * if (isLtr) 1f else -1f
                }

                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(
                        backdrop,
                        rememberBackdrop(trackBackdrop) { drawBackdrop ->
                            val progress = dampedDragAnimation.pressProgress
                            val scaleX = lerp(2f / 3f, 1f, progress)
                            val scaleY = lerp(0f, 1f, progress)
                            scale(scaleX, scaleY) {
                                drawBackdrop()
                            }
                        }
                    ),
                    shape = { capsule },
                    effects = {
                        val progress = dampedDragAnimation.pressProgress
                        blur(8f.dp.toPx() * (1f - progress))
                        lens(
                            10f.dp.toPx() * progress,
                            14f.dp.toPx() * progress,
                            chromaticAberration = true
                        )
                    },
                    highlight = {
                        val progress = dampedDragAnimation.pressProgress
                        Highlight.Ambient.copy(
                            width = Highlight.Ambient.width / 1.5f,
                            blurRadius = Highlight.Ambient.blurRadius / 1.5f,
                            alpha = progress
                        )
                    },
                    shadow = {
                        Shadow(
                            radius = 4f.dp,
                            color = Color.Black.copy(alpha = 0.05f)
                        )
                    },
                    innerShadow = {
                        val progress = dampedDragAnimation.pressProgress
                        InnerShadow(
                            radius = 4f.dp * progress,
                            alpha = progress
                        )
                    },
                    layerBlock = {
                        scaleX = dampedDragAnimation.scaleX
                        scaleY = dampedDragAnimation.scaleY
                        val velocity = dampedDragAnimation.velocity / 10f
                        scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                        scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                    },
                    onDrawSurface = {
                        val progress = dampedDragAnimation.pressProgress
                        drawRect(Color.White.copy(alpha = 1f - progress))
                    }
                )
                .size(40f.dp, 24f.dp)
        )
        }
    }
}
