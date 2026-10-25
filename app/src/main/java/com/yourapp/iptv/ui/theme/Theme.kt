package com.yourapp.iptv.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.yourapp.iptv.domain.model.HomeTheme

@Composable
fun IPTVAppTheme(
    isDarkTheme: Boolean = true,
    isNightMode: Boolean = false,
    theme: HomeTheme = if (isDarkTheme) HomeTheme.DARK_BLUE else HomeTheme.LIGHT,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = AppThemeColors.forTheme(theme, isNightMode),
        typography = AppTypography,
        content = content
    )
}