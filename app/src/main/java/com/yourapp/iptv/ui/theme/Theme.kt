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

private val NightColorScheme = darkColorScheme(
    primary = NeonBlue,
    onPrimary = Color.Black,
    background = Color.Black,
    onBackground = Color.White,
    surface = Color(0xFF090909),
    onSurface = Color.White,
    onSurfaceVariant = Color(0xFFCACACA),
    surfaceVariant = Color(0xFF161616),
    surfaceDim = Color.Black,
    surfaceBright = Color(0xFF202020),
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF050505),
    surfaceContainer = Color(0xFF0A0A0A),
    surfaceContainerHigh = Color(0xFF141414),
    surfaceContainerHighest = Color(0xFF202020),
    inverseSurface = Color(0xFFE8E8E8),
    inverseOnSurface = Color(0xFF111111),
    outline = Color(0xFF595959),
    outlineVariant = Color(0xFF383838),
    scrim = Color.Black,
    error = Color(0xFFFF8080),
    onError = Color.Black
)

@Composable
fun IPTVAppTheme(
    isDarkTheme: Boolean = true,
    isNightMode: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = when {
            isNightMode -> NightColorScheme
            isDarkTheme -> DarkColorScheme
            else -> LightColorScheme
        },
        typography = AppTypography,
        content = content
    )
}