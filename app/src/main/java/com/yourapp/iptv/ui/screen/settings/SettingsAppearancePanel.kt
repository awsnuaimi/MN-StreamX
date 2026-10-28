package com.yourapp.iptv.ui.screen.settings

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yourapp.iptv.R
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.ui.theme.IPTVAppTheme
import com.yourapp.iptv.viewmodel.HomeAppearanceEditorState

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SettingsAppearancePanel(state: HomeAppearanceEditorState, labels: SettingsLabels, first: FocusRequester,
    onChange: (HomeAppearance) -> Unit, onRetry: () -> Unit, onPlayerText: () -> Unit = {}) {
    val value = state.value
    val enabled = !state.loading && !state.saving && !state.failed
    val c = SettingsCategory.APPEARANCE
    fun text(ar: String, en: String, de: String) = labels.text(ar, en, de)
    if (state.loading) { CircularProgressIndicator();return }
    if (state.failed) {
        SettingsActionRow(stringResource(R.string.ui_reload), stringResource(R.string.ui_home_read_error), category = c,
            modifier = Modifier.focusRequester(first).testTag("settings-appearance-retry"), onClick = onRetry)
        return
    }
    SettingsChoiceRow(stringResource(R.string.ui_language), value.language,
        listOf(HomeLanguage.ARABIC to "العربية", HomeLanguage.ENGLISH to "English", HomeLanguage.GERMAN to "Deutsch"), c,
        "settings-language", enabled, Modifier.focusRequester(first)) { onChange(value.copy(language = it)) }
    var advanced by rememberSaveable(value.profileId) { mutableStateOf(false) }
    var display by rememberSaveable(value.profileId) { mutableStateOf(false) }
    val displayFocus = remember { FocusRequester() }
    var themeFocused by remember { mutableStateOf(false) }
    Surface(Modifier.fillMaxWidth().onFocusChanged { themeFocused = it.hasFocus }.focusGroup(),
        shape = MaterialTheme.shapes.medium,
        color = if (themeFocused) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(if (themeFocused) 2.dp else 1.dp,
            if (themeFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {
        BoxWithConstraints(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
            val compact = maxWidth.value / LocalDensity.current.fontScale.coerceAtLeast(1f) < 450f
            val choices: @Composable () -> Unit = { FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(HomeTheme.DARK_BLUE to R.string.theme_dark_blue, HomeTheme.RED to R.string.theme_red,
                    HomeTheme.LIGHT to R.string.theme_light).forEach { (theme, title) ->
                    FilterChip(value.theme == theme, { onChange(value.copy(theme = theme)) }, enabled = enabled,
                        label = { Text((if (value.theme == theme) "✓ " else "") + stringResource(title)) },
                        leadingIcon = { Canvas(Modifier.size(13.dp)) { drawCircle(when (theme) {
                            HomeTheme.DARK_BLUE -> Color(0xFF21518A);HomeTheme.RED -> Color(0xFFFF3038);HomeTheme.LIGHT -> Color.White
                        }) } }, modifier = Modifier.testTag("settings-theme-${theme.name}"))
                }
            } }
            if (compact) Column {
                Text(stringResource(R.string.theme_choice_title), style = MaterialTheme.typography.titleSmall);choices()
            } else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SettingsIcon(c, MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.theme_choice_title), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                choices()
            }
        }
    }
    SettingsActionRow(stringResource(R.string.theme_night), text("خلفية سوداء مستقلة عن التيم", "Black background independent of the theme", "Schwarzer Hintergrund unabhängig vom Design"), category = c,
        checked = value.isNightMode, enabled = enabled, glyph = "settings-night", modifier = Modifier.testTag("settings-night")) {
        onChange(value.copy(isNightMode = !value.isNightMode))
    }
    SettingsActionRow(text("حجم النص داخل المشغّل", "Player text size", "Textgröße im Player"),
        category = SettingsCategory.PLAYBACK,
        glyph = "settings-player-text", enabled = enabled, modifier = Modifier.testTag("settings-player-text"), onClick = onPlayerText)
    val layout = stringResource(if (value.channelLayout == HomeChannelLayout.GRID) R.string.channel_layout_grid else R.string.channel_layout_list)
    SettingsActionRow(stringResource(R.string.channel_layout_title), value = layout + if (value.channelLayout == HomeChannelLayout.GRID) " · ${value.columns}" else "",
        category = c, glyph = "settings-display", enabled = enabled,
        modifier = Modifier.focusRequester(displayFocus).testTag("settings-display")) { display = true }
    if (display) AlertDialog(onDismissRequest = { display = false;displayFocus.requestFocus() },
        title = { Text(stringResource(R.string.channel_layout_title)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SettingsChoiceRow(stringResource(R.string.channel_layout_title), value.channelLayout,
                listOf(HomeChannelLayout.GRID to stringResource(R.string.channel_layout_grid), HomeChannelLayout.LIST to stringResource(R.string.channel_layout_list)),
                c, "settings-channel-layout", enabled) { onChange(value.copy(channelLayout = it)) }
            SettingsChoiceRow(stringResource(R.string.ui_columns), value.columns,
                listOf(2 to stringResource(R.string.ui_columns_two), 3 to stringResource(R.string.ui_columns_three),
                    4 to stringResource(R.string.ui_columns_four), 5 to stringResource(R.string.ui_columns_five)),
                c, "settings-columns", enabled) { onChange(value.copy(columns = it)) }
            Text(stringResource(R.string.ui_grid_adaptive_hint), style = MaterialTheme.typography.bodySmall)
        } }, confirmButton = { TextButton({ display = false;displayFocus.requestFocus() },
            modifier = Modifier.testTag("settings-display-done")) { Text(text("تم", "Done", "Fertig")) } })
    Text(stringResource(R.string.theme_preview), style = MaterialTheme.typography.titleSmall)
    IPTVAppTheme(isNightMode = value.isNightMode, theme = value.theme) {
        Surface(Modifier.fillMaxWidth().testTag("settings-theme-preview"), color = MaterialTheme.colorScheme.background,
            shape = MaterialTheme.shapes.medium, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text("اسم القناة", "Channel name", "Sendername"), color = MaterialTheme.colorScheme.onSurface)
                    LinearProgressIndicator(progress = { 0.55f }, modifier = Modifier.fillMaxWidth())
                }
                Surface(color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = MaterialTheme.shapes.small) { Text(stringResource(R.string.theme_selection), Modifier.padding(8.dp)) }
            }
        }
    }
    SettingsActionRow(text("خيارات العرض الإضافية", "More display options", "Weitere Anzeigeoptionen"), category = c,
        value = if (advanced) text("إخفاء", "Hide", "Ausblenden") else text("إظهار", "Show", "Anzeigen"),
        enabled = enabled, glyph = "settings-advanced", modifier = Modifier.testTag("settings-advanced")) { advanced = !advanced }
    if (advanced) {
    SettingsActionRow(stringResource(R.string.channel_logos_title), category = c, checked = value.showChannelLogos,
        enabled = enabled, glyph = "settings-logos", modifier = Modifier.testTag("settings-logos")) { onChange(value.copy(showChannelLogos = !value.showChannelLogos)) }
    SettingsChoiceRow(stringResource(R.string.ui_sidebar_side), value.sidebarSide,
        listOf(SidebarSide.AUTO to stringResource(R.string.ui_side_auto), SidebarSide.RIGHT to stringResource(R.string.ui_right),
            SidebarSide.LEFT to stringResource(R.string.ui_left)), c, "settings-sidebar-side", enabled) { onChange(value.copy(sidebarSide = it)) }
    SettingsActionRow(stringResource(R.string.ui_sidebar_expanded), category = c, checked = value.expandedSidebar,
        enabled = enabled, glyph = "settings-sidebar-expanded", modifier = Modifier.testTag("settings-sidebar-expanded")) { onChange(value.copy(expandedSidebar = !value.expandedSidebar)) }
    SettingsActionRow(stringResource(R.string.home_session_restore), stringResource(R.string.home_session_hint), category = c,
        checked = value.restoreBrowseSession, enabled = enabled, glyph = "settings-browse-restore", modifier = Modifier.testTag("settings-browse-restore")) {
        onChange(value.copy(restoreBrowseSession = !value.restoreBrowseSession))
    }
    }
    state.error?.let { Text(stringResource(R.string.ui_home_save_error), color = MaterialTheme.colorScheme.error) }
}