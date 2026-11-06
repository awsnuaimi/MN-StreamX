package mnstudio.apps.mnstreamx.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import mnstudio.apps.mnstreamx.domain.model.HomeTheme

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