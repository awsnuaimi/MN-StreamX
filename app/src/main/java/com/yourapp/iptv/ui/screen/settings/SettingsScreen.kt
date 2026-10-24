package com.yourapp.iptv.ui.screen.settings

import androidx.compose.ui.res.stringResource
import com.yourapp.iptv.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yourapp.iptv.ui.component.common.AppButton
import com.yourapp.iptv.ui.state.SettingsIssue
import com.yourapp.iptv.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onSettingsSaved: () -> Unit = {},
    onBack: (() -> Unit)? = null,
    onPlayerAppearance: () -> Unit = {},
    onHomeAppearance: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.isSaved) {
        if (state.isSaved) {
            viewModel.resetSaveStatus()
            onSettingsSaved()
        }
    }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(stringResource(R.string.ui_settings_title), style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary)
        AppButton(stringResource(R.string.ui_home_appearance), onHomeAppearance, enabled = !state.isSaving)
        AppButton(stringResource(R.string.ui_player_title), onPlayerAppearance, enabled = !state.isSaving)
        StartupPlaybackSetting()
        StorageManagementSection()
        if (state.isLoading) {
            CircularProgressIndicator()
            Text(stringResource(R.string.ui_settings_loading))
        } else {
            SettingsInputForm(
                settings = state.settings,
                enabled = !state.isSaving && !state.loadFailed,
                onSourceChanged = viewModel::updateSource,
                onM3uChanged = viewModel::updateM3uUrl,
                onServerChanged = viewModel::updateXtreamServer,
                onUsernameChanged = viewModel::updateXtreamUsername,
                onPasswordChanged = viewModel::updateXtreamPassword,
                onEpgChanged = viewModel::updateEpgUrl
            )
        }
        state.issue?.let { issue -> Text(stringResource(when (issue) {
            SettingsIssue.READ_FAILED -> R.string.ui_settings_read_error
            SettingsIssue.SAVE_FAILED -> R.string.ui_settings_save_error
            SettingsIssue.M3U_REQUIRED -> R.string.ui_settings_m3u_required
            SettingsIssue.M3U_INVALID -> R.string.ui_settings_m3u_invalid
            SettingsIssue.XTREAM_REQUIRED -> R.string.ui_settings_xtream_required
            SettingsIssue.SERVER_INVALID -> R.string.ui_settings_server_invalid
            SettingsIssue.EPG_INVALID -> R.string.ui_settings_epg_invalid
        }), color = MaterialTheme.colorScheme.error) }
        if (state.loadFailed) {
            AppButton(stringResource(R.string.ui_settings_reload), viewModel::loadSavedSettings)
        } else {
            AppButton(
                text = if (state.isSaving) stringResource(R.string.ui_saving) else stringResource(R.string.ui_settings_save),
                onClick = viewModel::saveSettings,
                enabled = !state.isLoading && !state.isSaving,
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (state.isSaving) CircularProgressIndicator()
        if (onBack != null) {
            Row { AppButton(stringResource(R.string.ui_back), onBack, enabled = !state.isSaving) }
        }
    }
}