package com.rfandango.haku_x.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** hakuX's look: white surfaces and the Xbox-era green, as the app had before Compose. */
private val HakuXColors = lightColorScheme(
  primary = Color(0xFF2E7D32),
  onPrimary = Color.White,
  primaryContainer = Color(0xFFC8E6C9),
  onPrimaryContainer = Color(0xFF1B5E20),
  secondary = Color(0xFF2E7D32),
  onSecondary = Color.White,
  secondaryContainer = Color(0xFFE8F5E9),
  onSecondaryContainer = Color(0xFF1B5E20),
  background = Color.White,
  onBackground = Color(0xFF1C1B1F),
  surface = Color.White,
  onSurface = Color(0xFF1C1B1F),
  surfaceVariant = Color(0xFFF5F5F5),
  onSurfaceVariant = Color(0xFF5F6368),
  surfaceContainerLowest = Color.White,
  surfaceContainerLow = Color(0xFFFAFAFA),
  surfaceContainer = Color(0xFFF5F5F5),
  surfaceContainerHigh = Color(0xFFEEEEEE),
  surfaceContainerHighest = Color(0xFFE8E8E8),
  outline = Color(0xFFDADCE0),
  outlineVariant = Color(0xFFE0E0E0),
  error = Color(0xFFD32F2F),
)

@Composable
fun HakuXTheme(content: @Composable () -> Unit) {
  MaterialTheme(colorScheme = HakuXColors, content = content)
}
