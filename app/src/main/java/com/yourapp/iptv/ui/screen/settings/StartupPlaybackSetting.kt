package com.yourapp.iptv.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yourapp.iptv.R
import com.yourapp.iptv.ui.component.common.AppButton
import com.yourapp.iptv.viewmodel.StartupPlaybackSettingsViewModel

@Composable
internal fun StartupPlaybackSetting(viewModel: StartupPlaybackSettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val value = stringResource(if (state.enabled) R.string.startup_enabled else R.string.startup_disabled)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.startup_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.startup_description), style = MaterialTheme.typography.bodySmall)
        AppButton(stringResource(when {
            state.isLoading -> R.string.startup_loading
            state.isSaving -> R.string.startup_saving
            state.enabled -> R.string.startup_turn_off
            else -> R.string.startup_turn_on
        }), viewModel::toggle,
            modifier = settingsFocusTarget("startup-toggle", !state.isLoading && !state.isSaving && !state.readFailed).semantics { stateDescription = value },
            enabled = !state.isLoading && !state.isSaving && !state.readFailed)
        if (state.readFailed) {
            Text(stringResource(R.string.startup_read_error), color = MaterialTheme.colorScheme.error)
            AppButton(stringResource(R.string.startup_retry), viewModel::load, modifier = settingsFocusTarget("startup-retry"))
        }
        if (state.saveFailed) Text(stringResource(R.string.startup_save_error), color = MaterialTheme.colorScheme.error)
    }
}