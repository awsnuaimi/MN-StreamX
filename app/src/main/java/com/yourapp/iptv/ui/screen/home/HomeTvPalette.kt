package com.yourapp.iptv.ui.screen.home

import androidx.compose.ui.graphics.Color
import androidx.compose.material3.ColorScheme
import com.yourapp.iptv.ui.theme.AppThemeColors
import com.yourapp.iptv.domain.model.HomeTheme

internal data class HomeTvPalette(
    val background: Color,
    val sidebar: Color,
    val surface: Color,
    val focused: Color,
    val selected: Color,
    val outline: Color,
    val selectedOutline: Color,
    val text: Color,
    val muted: Color,
    val accent: Color,
    val logoSurface: Color,
    val heroGradient: List<Color>,
    val heroOverlay: List<Color>,
    val cardOverlay: List<Color>,
    val cardFallbackGradient: List<Color>,
    val liveBadge: Color
)

internal object HomeTvPalettes {
    private val dark = from(AppThemeColors.forMode(true))
    private val light = from(AppThemeColors.forMode(false))
    private val red = from(AppThemeColors.forTheme(HomeTheme.RED))
    private val night = from(AppThemeColors.forMode(true, true))

    private fun from(colors: ColorScheme): HomeTvPalette = HomeTvPalette(
        background = colors.background, sidebar = colors.surfaceContainerLowest,
        surface = colors.surface, focused = colors.primaryContainer,
        selected = colors.secondaryContainer, outline = colors.outlineVariant,
        selectedOutline = colors.outline, text = colors.onSurface,
        muted = colors.onSurfaceVariant, accent = colors.primary,
        logoSurface = colors.surfaceContainerLow,
        heroGradient = listOf(colors.primaryContainer, colors.surfaceContainerLow),
        heroOverlay = listOf(colors.background.copy(alpha = 0.95f),
            colors.background.copy(alpha = 0.7f), colors.background.copy(alpha = 0.38f)),
        cardOverlay = listOf(Color.Transparent, Color.Black.copy(alpha = 0.86f)),
        cardFallbackGradient = listOf(colors.surfaceContainerHighest, colors.surfaceContainerLowest),
        liveBadge = Color(0xFFD32F2F)
    )

    fun forTheme(theme: HomeTheme, isNight: Boolean = false): HomeTvPalette = when {
        isNight -> night
        theme == HomeTheme.RED -> red
        theme == HomeTheme.LIGHT -> light
        else -> dark
    }

    fun forMode(isDark: Boolean, isNight: Boolean = false): HomeTvPalette = when {
        isNight -> night
        isDark -> dark
        else -> light
    }
}