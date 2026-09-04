package com.cxcboss.glassorb.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val GlassOrbColors = darkColorScheme(
    primary = Color(0xFFAFC0FF),
    onPrimary = Color(0xFF122052),
    primaryContainer = Color(0xFF273665),
    onPrimaryContainer = Color(0xFFDCE3FF),
    secondary = Color(0xFF8EE7DC),
    onSecondary = Color(0xFF003731),
    secondaryContainer = Color(0xFF174D49),
    onSecondaryContainer = Color(0xFFB8F3EA),
    tertiary = Color(0xFFFFB2D0),
    background = Color(0xFF090B12),
    onBackground = Color(0xFFE6E7F0),
    surface = Color(0xFF10131D),
    onSurface = Color(0xFFE6E7F0),
    surfaceVariant = Color(0xFF191D2A),
    onSurfaceVariant = Color(0xFFBFC3D5),
    outline = Color(0xFF3D4354),
    error = Color(0xFFFFB4AB),
)

@Composable
fun GlassOrbTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = GlassOrbColors,
        content = content,
    )
}
