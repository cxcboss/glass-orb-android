package com.cxcboss.glassorb.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.components.LiquidButton as ReferenceLiquidButton

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
