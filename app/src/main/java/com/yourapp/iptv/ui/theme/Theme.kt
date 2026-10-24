package com.yourapp.iptv.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = NeonBlue,
    onPrimary = Color.Black,
    background = DeepSpace,
    onBackground = Color(0xFFF1F6FF),
    surface = CardGlass,
    onSurface = TextWhite,
    onSurfaceVariant = Color(0xFFBED0E2),
    surfaceVariant = Color(0xFF263B54),
    outline = Color(0xFF6886A5)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF005EB8),
    onPrimary = Color.White,
    background = Color(0xFFF0F4F8),
    onBackground = Color(0xFF16283B),
    surface = Color.White,
    onSurface = Color(0xFF16283B),
    onSurfaceVariant = Color(0xFF52667A),
    surfaceVariant = Color(0xFFE3EBF4),
    outline = Color(0xFF75899D)
)

@Composable
fun IPTVAppTheme(isDarkTheme: Boolean = true, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isDarkTheme) DarkColorScheme else LightColorScheme,
        typography = AppTypography,
        content = content
    )
}