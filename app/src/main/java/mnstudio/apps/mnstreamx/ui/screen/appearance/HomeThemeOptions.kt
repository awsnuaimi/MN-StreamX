package mnstudio.apps.mnstreamx.ui.screen.appearance

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import mnstudio.apps.mnstreamx.R
import mnstudio.apps.mnstreamx.domain.model.HomeAppearance
import mnstudio.apps.mnstreamx.domain.model.HomeTheme
import mnstudio.apps.mnstreamx.ui.theme.IPTVAppTheme

@Composable
internal fun HomeThemeOptions(value: HomeAppearance, enabled: Boolean, onChange: (HomeAppearance) -> Unit) {
    AppearanceChoice(stringResource(R.string.theme_choice_title), value.theme,
        listOf(HomeTheme.DARK_BLUE to stringResource(R.string.theme_dark_blue),
            HomeTheme.LIGHT to stringResource(R.string.theme_light),
            HomeTheme.RED to stringResource(R.string.theme_red)),
        enabled) { onChange(value.copy(theme = it)) }
    AppearanceToggle(stringResource(R.string.theme_night), value.isNightMode, enabled) {
        onChange(value.copy(isNightMode = it))
    }
    Text(stringResource(R.string.theme_night_hint), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(stringResource(R.string.theme_preview), style = MaterialTheme.typography.titleSmall)
    HomeThemePreview(value)
}

@Composable
internal fun HomeThemePreview(value: HomeAppearance) {
    IPTVAppTheme(isNightMode = value.isNightMode, theme = value.theme) {
        val colors = MaterialTheme.colorScheme
        Surface(Modifier.fillMaxWidth().testTag("theme-preview"), color = colors.background,
            contentColor = colors.onBackground, shape = MaterialTheme.shapes.medium,
            border = BorderStroke(1.dp, colors.outline)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(when {
                    value.isNightMode -> R.string.theme_night
                    value.theme == HomeTheme.RED -> R.string.theme_red
                    value.theme == HomeTheme.DARK_BLUE -> R.string.theme_dark_blue
                    else -> R.string.theme_light
                }), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.theme_preview_text), color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(color = colors.primary, contentColor = colors.onPrimary,
                        shape = MaterialTheme.shapes.small) {
                        Text(stringResource(R.string.theme_focus), Modifier.padding(8.dp))
                    }
                    Surface(color = colors.secondaryContainer, contentColor = colors.onSecondaryContainer,
                        shape = MaterialTheme.shapes.small) {
                        Text(stringResource(R.string.theme_selection), Modifier.padding(8.dp))
                    }
                }
            }
        }
    }
}