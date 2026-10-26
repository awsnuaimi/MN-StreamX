package com.yourapp.iptv.ui.screen.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yourapp.iptv.R
import com.yourapp.iptv.ui.component.common.AppButton
import com.yourapp.iptv.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onBack: (() -> Unit)? = null,
    onPlayerAppearance: () -> Unit = {},
    onHomeAppearance: () -> Unit = {},
    onSubscriptions: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.ui_settings_title), style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary)
        AppButton(stringResource(R.string.subscription_title), onSubscriptions)
        state.subscriptions.firstOrNull { it.id == state.activeSubscriptionId }?.let { active ->
            Text(stringResource(R.string.subscription_current,
                active.name.ifBlank { stringResource(R.string.subscription_default) }),
                style = MaterialTheme.typography.bodyMedium)
        }
        AppButton(stringResource(R.string.theme_settings_title), onHomeAppearance)
        AppButton(stringResource(R.string.ui_player_title), onPlayerAppearance)
        AppUpdateSection()
        StartupPlaybackSetting()
        StorageManagementSection()
        if (onBack != null) AppButton(stringResource(R.string.ui_back), onBack)
    }
}