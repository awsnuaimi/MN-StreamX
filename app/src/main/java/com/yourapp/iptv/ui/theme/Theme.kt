package com.yourapp.iptv.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

@Composable
fun IPTVAppTheme(
    isDarkTheme: Boolean = true,
    isNightMode: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = AppThemeColors.forMode(isDarkTheme, isNightMode),
        typography = AppTypography,
        content = content
    )
}