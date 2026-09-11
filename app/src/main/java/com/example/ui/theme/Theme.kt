package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
  primary = CyberCyan,
  onPrimary = Color.Black,
  primaryContainer = ElectricBlue,
  onPrimaryContainer = Color.White,
  secondary = NeonGreen,
  onSecondary = Color.Black,
  background = DarkBg,
  surface = DarkSurface,
  surfaceVariant = DarkSurfaceVariant,
  onBackground = TextPrimaryDark,
  onSurface = TextPrimaryDark,
  outline = DarkCardBorder
)

private val LightColorScheme = lightColorScheme(
  primary = ElectricBlue,
  onPrimary = Color.White,
  primaryContainer = CyberCyan,
  onPrimaryContainer = Color.Black,
  secondary = NeonGreen,
  onSecondary = Color.White,
  background = LightBg,
  surface = LightSurface,
  surfaceVariant = LightSurfaceVariant,
  onBackground = TextPrimaryLight,
  onSurface = TextPrimaryLight,
  outline = LightCardBorder
)

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true, // Default to sleek dark cyber mode for VPN
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

  MaterialTheme(
    colorScheme = colorScheme,
    typography = Typography,
    content = content
  )
}
