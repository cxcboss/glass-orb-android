package com.cxcboss.glassorb.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val GlassOrbColors = darkColorScheme(
    primary = Color(0xFF0A84FF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF102B48),
    onPrimaryContainer = Color(0xFFB8D9FF),
    secondary = Color(0xFF30D158),
    onSecondary = Color.Black,
    background = Color.Black,
    onBackground = Color.White,
    surface = Color(0xFF1C1C1E),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF2C2C2E),
    onSurfaceVariant = Color(0xFF8E8E93),
    outline = Color(0xFF48484A),
    error = Color(0xFFFF453A),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF007AFF), onPrimary = Color.White,
    primaryContainer = Color(0xFFE5F0FF), onPrimaryContainer = Color(0xFF0055B3),
    secondary = Color(0xFF34C759), onSecondary = Color.White,
    background = Color(0xFFF2F2F7), onBackground = Color.Black,
    surface = Color.White, onSurface = Color.Black,
    surfaceVariant = Color(0xFFE5E5EA), onSurfaceVariant = Color(0xFF8E8E93),
    outline = Color(0xFFC6C6C8), error = Color(0xFFFF3B30),
)

@Composable
fun GlassOrbTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) GlassOrbColors else LightColors,
        content = content,
    )
}
