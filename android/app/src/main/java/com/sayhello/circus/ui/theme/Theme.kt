package com.sayhello.circus.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CircusRed = Color(0xFFC2185B)
private val CircusYellow = Color(0xFFFFD54F)
private val CircusPurple = Color(0xFF6A1B9A)
private val CircusNavy = Color(0xFF1A1147)
private val CircusCream = Color(0xFFFFF8E1)
private val CircusBlack = Color(0xFF0E0820)

private val DarkScheme = darkColorScheme(
    primary = CircusYellow,
    onPrimary = CircusBlack,
    secondary = CircusRed,
    onSecondary = CircusCream,
    tertiary = CircusPurple,
    background = CircusBlack,
    onBackground = CircusCream,
    surface = CircusNavy,
    onSurface = CircusCream,
    surfaceVariant = CircusPurple,
    onSurfaceVariant = CircusCream,
)

private val LightScheme = lightColorScheme(
    primary = CircusPurple,
    onPrimary = CircusCream,
    secondary = CircusRed,
    onSecondary = CircusCream,
    tertiary = CircusYellow,
    background = CircusCream,
    onBackground = CircusBlack,
    surface = CircusCream,
    onSurface = CircusBlack,
)

@Composable
fun DigitalCircusTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val scheme = if (darkTheme) DarkScheme else LightScheme
    MaterialTheme(colorScheme = scheme, content = content)
}
