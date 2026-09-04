package com.cxcboss.glassorb.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.PaddingValues
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow

enum class SettingsSection(val label: String) { Overview("概览"), Appearance("外观"), Motion("动效") }
val LocalGlassBackdrop = staticCompositionLocalOf<Backdrop> { error("Liquid glass backdrop missing") }

@Composable
fun LiquidGlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
    content: @Composable RowScope.() -> Unit,
) {
    val backdrop = LocalGlassBackdrop.current
    val interaction = androidx.compose.runtime.remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 1.07f else 1f, spring(.65f, 500f), label = "liquidButton")
    Row(
        modifier.height(48.dp).graphicsLayer { scaleX = scale; scaleY = scale }
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedCornerShape(999.dp) },
                effects = { vibrancy(); blur(2.dp.toPx()); lens(12.dp.toPx(), 24.dp.toPx()) },
                highlight = { Highlight.Ambient.copy(alpha = if (pressed) 1f else .55f) },
                shadow = { Shadow(radius = 4.dp, color = Color.Black.copy(alpha = .08f)) },
                innerShadow = { InnerShadow(radius = 4.dp, alpha = if (pressed) .9f else .35f) },
                onDrawSurface = { drawRect(tint.copy(alpha = if (pressed) .68f else .78f)) },
            )
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
fun LiquidGlassTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    content: @Composable RowScope.() -> Unit,
) {
    val interaction = androidx.compose.runtime.remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        modifier.clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = if (pressed) .16f else .06f))
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(contentPadding),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** Translucent chrome only; labels are rendered sharply above the surface. */
@Composable
fun IosGlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    val backdrop = LocalGlassBackdrop.current
    val surfaceColor = MaterialTheme.colorScheme.surface.copy(alpha = .42f)
    Column(modifier.fillMaxWidth().clip(shape)
        .drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = { vibrancy(); blur(2.dp.toPx()); lens(12.dp.toPx(), 24.dp.toPx()) },
            highlight = { Highlight.Ambient },
            shadow = { Shadow(radius = 8.dp, color = Color.Black.copy(alpha = .08f)) },
            innerShadow = { InnerShadow(radius = 4.dp, alpha = .35f) },
            onDrawSurface = { drawRect(surfaceColor) },
        )
        .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f), shape), content = content)
}

@Composable
fun IosFloatingTabBar(selected: SettingsSection, onSelected: (SettingsSection) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(36.dp)
    val colors = MaterialTheme.colorScheme
    val backdrop = LocalGlassBackdrop.current
    val targetIndex = SettingsSection.entries.indexOf(selected).coerceAtLeast(0)
    val indicatorIndex by animateFloatAsState(targetIndex.toFloat(), spring(dampingRatio = .72f, stiffness = 420f), label = "liquidTab")
    var dragTotal = 0f
    Box(modifier.widthIn(max = 420.dp).fillMaxWidth().height(72.dp).shadow(14.dp, shape)
        .clip(shape).drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = { vibrancy(); blur(8.dp.toPx()); lens(24.dp.toPx(), 24.dp.toPx()) },
            highlight = { Highlight.Default },
            shadow = { Shadow(radius = 8.dp, color = Color.Black.copy(alpha = .12f)) },
            onDrawSurface = { drawRect(colors.surface.copy(alpha = .40f)) },
        )
        .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.65f), colors.outline.copy(alpha = 0.25f))), shape)
        .padding(6.dp)
        .pointerInput(selected) {
            detectHorizontalDragGestures(
                onDragStart = { dragTotal = 0f },
                onHorizontalDrag = { _, amount ->
                    dragTotal += amount
                    if (kotlin.math.abs(dragTotal) > size.width / 6f) {
                        val next = (targetIndex + if (dragTotal > 0f) 1 else -1).coerceIn(0, SettingsSection.entries.lastIndex)
                        onSelected(SettingsSection.entries[next])
                        dragTotal = 0f
                    }
                },
            )
        }) {
        Box(Modifier.fillMaxWidth(1f / 3f).fillMaxHeight().graphicsLayer { translationX = size.width * indicatorIndex }
            .clip(RoundedCornerShape(30.dp))
            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = .22f), colors.primary.copy(alpha = .14f))))
            .border(.5.dp, Color.White.copy(alpha = .35f), RoundedCornerShape(30.dp)))
        Row(Modifier.fillMaxSize().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        SettingsSection.entries.forEach { section ->
            val active = selected == section
            val tint = if (active) colors.primary else colors.onSurfaceVariant
            Column(Modifier.weight(1f).clip(RoundedCornerShape(30.dp))
                .background(if (active) colors.primary.copy(alpha = 0.12f) else Color.Transparent)
                .selectable(active, role = Role.Tab, onClick = { onSelected(section) })
                .padding(vertical = 9.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Canvas(Modifier.size(23.dp)) {
                    val stroke = Stroke(1.8.dp.toPx())
                    when (section) {
                        SettingsSection.Overview -> {
                            listOf(Offset(.12f,.12f), Offset(.57f,.12f), Offset(.12f,.57f), Offset(.57f,.57f)).forEach {
                                drawRoundRect(tint, Offset(size.width*it.x,size.height*it.y), Size(size.width*.31f,size.height*.31f), androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()), style = stroke)
                            }
                        }
                        SettingsSection.Appearance -> {
                            drawCircle(tint, size.width*.36f, style = stroke)
                            drawArc(tint, -90f, 180f, true, Offset(size.width*.14f,size.height*.14f), Size(size.width*.72f,size.height*.72f))
                        }
                        SettingsSection.Motion -> {
                            for (i in 0..2) {
                                val x = size.width*(.22f+i*.28f)
                                val y = size.height*(if(i==1) .65f else .35f)
                                drawLine(tint, Offset(x,size.height*.15f),Offset(x,size.height*.85f),stroke.width)
                                drawCircle(colors.surface,3.dp.toPx(),Offset(x,y))
                                drawCircle(tint,3.dp.toPx(),Offset(x,y),style=stroke)
                            }
                        }
                    }
                }
                Text(section.label, color = tint, style = MaterialTheme.typography.labelMedium, fontWeight = if(active) FontWeight.SemiBold else FontWeight.Medium)
            }
        }
        }
    }
}
