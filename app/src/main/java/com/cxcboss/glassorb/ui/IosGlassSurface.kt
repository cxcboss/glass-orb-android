package com.cxcboss.glassorb.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.components.LiquidBottomTab
import com.kyant.backdrop.catalog.components.LiquidBottomTabs
import com.kyant.backdrop.catalog.components.LiquidButton as ReferenceLiquidButton
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow

enum class SettingsSection(val label: String) { Overview("概览"), Appearance("外观"), Motion("动效") }

val LocalGlassBackdrop = staticCompositionLocalOf<Backdrop> { error("Liquid glass backdrop missing") }

/**
 * App-facing adapter around AndroidLiquidGlass' original LiquidButton implementation.
 * The visual, lens, press deformation and highlight are all rendered by that component.
 */
@Composable
fun LiquidGlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
    content: @Composable RowScope.() -> Unit,
) {
    ReferenceLiquidButton(
        onClick = onClick,
        backdrop = LocalGlassBackdrop.current,
        modifier = modifier,
        tint = tint,
        content = content,
    )
}

/** Compact actions use the same original liquid button code, without a color tint. */
@Composable
fun LiquidGlassTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    content: @Composable RowScope.() -> Unit,
) {
    ReferenceLiquidButton(
        onClick = onClick,
        backdrop = LocalGlassBackdrop.current,
        modifier = modifier,
        content = content,
    )
}

/** Translucent content card; controls inside it use the exact reference components. */
@Composable
fun IosGlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    val backdrop = LocalGlassBackdrop.current
    val surfaceColor = MaterialTheme.colorScheme.surface.copy(alpha = .42f)
    Column(
        modifier
            .fillMaxWidth()
            .drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    vibrancy()
                    blur(2.dp.toPx())
                    lens(12.dp.toPx(), 24.dp.toPx())
                },
                highlight = { Highlight.Ambient },
                shadow = { Shadow(radius = 8.dp, color = Color.Black.copy(alpha = .08f)) },
                innerShadow = { InnerShadow(radius = 4.dp, alpha = .35f) },
                onDrawSurface = { drawRect(surfaceColor) },
            )
            .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f), shape),
        content = content,
    )
}

/** Direct composition of the original LiquidBottomTabs + LiquidBottomTab source. */
@Composable
fun IosFloatingTabBar(
    selected: SettingsSection,
    onSelected: (SettingsSection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sections = SettingsSection.entries
    val selectedIndex = sections.indexOf(selected).coerceAtLeast(0)
    val contentColor = if (isSystemInDarkTheme()) Color.White else Color.Black

    LiquidBottomTabs(
        selectedTabIndex = { selectedIndex },
        onTabSelected = { index -> sections.getOrNull(index)?.let(onSelected) },
        backdrop = LocalGlassBackdrop.current,
        tabsCount = sections.size,
        modifier = modifier.widthIn(max = 420.dp).fillMaxWidth(),
    ) {
        sections.forEach { section ->
            LiquidBottomTab(onClick = { onSelected(section) }) {
                SettingsSectionIcon(section = section, color = contentColor)
                Text(
                    text = section.label,
                    color = contentColor,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun SettingsSectionIcon(section: SettingsSection, color: Color) {
    val surfaceColor = MaterialTheme.colorScheme.surface
    Canvas(Modifier.size(23.dp)) {
        val stroke = Stroke(1.8.dp.toPx())
        when (section) {
            SettingsSection.Overview -> {
                listOf(Offset(.12f, .12f), Offset(.57f, .12f), Offset(.12f, .57f), Offset(.57f, .57f)).forEach {
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(size.width * it.x, size.height * it.y),
                        size = Size(size.width * .31f, size.height * .31f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()),
                        style = stroke,
                    )
                }
            }

            SettingsSection.Appearance -> {
                drawCircle(color, size.width * .36f, style = stroke)
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = 180f,
                    useCenter = true,
                    topLeft = Offset(size.width * .14f, size.height * .14f),
                    size = Size(size.width * .72f, size.height * .72f),
                )
            }

            SettingsSection.Motion -> {
                for (index in 0..2) {
                    val x = size.width * (.22f + index * .28f)
                    val y = size.height * if (index == 1) .65f else .35f
                    drawLine(color, Offset(x, size.height * .15f), Offset(x, size.height * .85f), stroke.width)
                    drawCircle(surfaceColor, 3.dp.toPx(), Offset(x, y))
                    drawCircle(color, 3.dp.toPx(), Offset(x, y), style = stroke)
                }
            }
        }
    }
}
