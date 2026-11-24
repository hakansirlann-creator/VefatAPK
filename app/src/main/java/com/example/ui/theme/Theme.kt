package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF74C69D),
    onPrimary = Color(0xFF0D261B),
    primaryContainer = SageTintDark,
    onPrimaryContainer = Color(0xFFD8F3DC),
    secondary = Color(0xFF95D5B2),
    onSecondary = Color(0xFF102A1E),
    secondaryContainer = DarkSurfaceElevated,
    onSecondaryContainer = TextLight,
    background = DarkBg,
    onBackground = TextLight,
    surface = DarkSurface,
    onSurface = TextLight,
    surfaceVariant = DarkSurfaceElevated,
    onSurfaceVariant = TextLightMuted,
    outline = DarkBorder,
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410)
)

private val LightColorScheme = lightColorScheme(
    primary = ForestGreen,
    onPrimary = Color.White,
    primaryContainer = SageTint,
    onPrimaryContainer = ForestGreen,
    secondary = ForestGreenLight,
    onSecondary = Color.White,
    secondaryContainer = SageTint,
    onSecondaryContainer = Anthracite,
    background = WarmPaper,
    onBackground = Anthracite,
    surface = CardWhite,
    onSurface = Anthracite,
    surfaceVariant = Color(0xFFEDF1EE),
    onSurfaceVariant = AnthraciteMuted,
    outline = BorderSoft,
    error = ErrorRed,
    onError = Color.White
)

@Composable
fun BatmanVefatTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = BatmanVefatTypography,
        content = content
    )
}
