package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
  primary = NightPrimary,
  onPrimary = Color(0xFF0A192F),
  primaryContainer = Color(0xFF1E3A5F),
  onPrimaryContainer = Color(0xFFD6E4FF),
  secondary = Color(0xFF9FB3C8),
  onSecondary = Color(0xFF102A43),
  background = NightBackground,
  onBackground = NightInk,
  surface = NightSurface,
  onSurface = NightInk,
  surfaceVariant = NightSurfaceVariant,
  onSurfaceVariant = NightInkSecondary,
  outline = NightOutline
)

private val LightColorScheme = lightColorScheme(
  primary = SlateNavy,
  onPrimary = Color.White,
  primaryContainer = Color(0xFFE0EAFF),
  onPrimaryContainer = SlateNavy,
  secondary = SlateNavyLight,
  onSecondary = Color.White,
  background = PaperBackground,
  onBackground = InkPrimary,
  surface = PaperSurface,
  onSurface = InkPrimary,
  surfaceVariant = PaperSurfaceVariant,
  onSurfaceVariant = InkSecondary,
  outline = OutlineSoft
)

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
  MaterialTheme(
    colorScheme = colorScheme,
    typography = Typography,
    content = content
  )
}
