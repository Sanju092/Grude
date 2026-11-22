package com.grude.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CobaltPrimary = Color(0xFF2563EB)
private val EmeraldSecondary = Color(0xFF059669)
private val SlateBgDark = Color(0xFF0F172A)
private val SlateSurfaceDark = Color(0xFF1E293B)

private val LightColors = lightColorScheme(
    primary = CobaltPrimary,
    onPrimary = Color.White,
    secondary = EmeraldSecondary,
    onSecondary = Color.White,
    background = Color(0xFFF8FAFC),
    surface = Color.White,
    onBackground = Color(0xFF0F172A),
    onSurface = Color(0xFF0F172A)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF60A5FA),
    onPrimary = Color(0xFF0F172A),
    secondary = Color(0xFF34D399),
    onSecondary = Color(0xFF0F172A),
    background = SlateBgDark,
    surface = SlateSurfaceDark,
    onBackground = Color(0xFFF8FAFC),
    onSurface = Color(0xFFF8FAFC)
)

@Composable
fun GrudeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
