package mnstudio.apps.mnstreamx.ui.screen.settings

import androidx.compose.ui.res.stringResource
import mnstudio.apps.mnstreamx.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import mnstudio.apps.mnstreamx.ui.component.common.TvTextInput
import mnstudio.apps.mnstreamx.ui.component.common.TvTextInputDialog
import mnstudio.apps.mnstreamx.ui.component.common.TvTextInputState
import mnstudio.apps.mnstreamx.ui.component.common.rememberTvTextInputState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import mnstudio.apps.mnstreamx.domain.model.AppSettings
import mnstudio.apps.mnstreamx.domain.model.PlaylistSource

@Composable
fun SettingsInputForm(
    settings: AppSettings,
    enabled: Boolean,
    onSourceChanged: (PlaylistSource) -> Unit,
    onM3uChanged: (String) -> Unit,
    onServerChanged: (String) -> Unit,
    onUsernameChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onEpgChanged: (String) -> Unit
) {
    val editor = rememberTvTextInputState()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.ui_source))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilterChip(selected = settings.source == PlaylistSource.M3U,
                onClick = { onSourceChanged(PlaylistSource.M3U) }, enabled = enabled, label = { Text("M3U") })
            FilterChip(selected = settings.source == PlaylistSource.XTREAM,
                onClick = { onSourceChanged(PlaylistSource.XTREAM) }, enabled = enabled, label = { Text("Xtream Codes") })
        }
        if (settings.source == PlaylistSource.M3U) {
            SettingsField(editor, stringResource(R.string.ui_m3u_url), settings.m3uUrl, enabled, onM3uChanged,
                placeholder = "https://example.com/playlist.m3u", uri = true)
        } else {
            SettingsField(editor, stringResource(R.string.ui_xtream_server), settings.xtreamServer, enabled, onServerChanged,
                placeholder = "https://example.com:8080", uri = true)
            SettingsField(editor, stringResource(R.string.ui_xtream_user), settings.xtreamUsername, enabled, onUsernameChanged)
            SettingsField(editor, stringResource(R.string.ui_xtream_password), settings.xtreamPassword, enabled, onPasswordChanged, password = true)
            Text(stringResource(R.string.ui_xtream_hint))
        }
        SettingsField(editor, stringResource(R.string.ui_epg_url), settings.epgUrl, enabled, onEpgChanged, uri = true)
    }
    TvTextInputDialog(editor, enabled)
}

@Composable
private fun SettingsField(
    editor: TvTextInputState,
    label: String,
    value: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    uri: Boolean = false,
    password: Boolean = false
) {
    TvTextInput(editor, label, value, onValueChange, modifier = Modifier.fillMaxWidth(),
        enabled = enabled, secret = password, placeholder = placeholder,
        keyboardType = if (uri) KeyboardType.Uri else KeyboardType.Text)
}