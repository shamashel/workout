package com.shamashel.workout.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Ink = Color(0xFF1C1915)
val Paper = Color(0xFFF6F1E7)
val CardWhite = Color(0xFFFFFCF8)
val Pine = Color(0xFF1B4332)
val Leaf = Color(0xFF2D6A4F)
val Copper = Color(0xFF9C4A2B)
val Line = Color(0xFFE4DCCD)

private val LightColors = lightColorScheme(
    primary = Pine,
    onPrimary = Color(0xFFF6F1E7),
    secondary = Copper,
    onSecondary = Color.White,
    tertiary = Leaf,
    background = Paper,
    onBackground = Ink,
    surface = CardWhite,
    onSurface = Ink,
    surfaceVariant = Color(0xFFEFE7D8),
    onSurfaceVariant = Color(0xFF4A433A),
    error = Color(0xFF8E2F2F),
    outline = Line,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8FCBB0),
    onPrimary = Color(0xFF123028),
    secondary = Color(0xFFE0A48C),
    background = Color(0xFF141210),
    onBackground = Color(0xFFF3EDE3),
    surface = Color(0xFF1E1B17),
    onSurface = Color(0xFFF3EDE3),
    surfaceVariant = Color(0xFF2A261F),
    error = Color(0xFFFFB4AB),
)

@Composable
fun WorkoutTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
