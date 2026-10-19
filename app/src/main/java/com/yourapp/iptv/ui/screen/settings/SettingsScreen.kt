package com.yourapp.iptv.ui.screen.settings

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
import com.yourapp.iptv.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onSettingsSaved: () -> Unit = {},
    onBack: (() -> Unit)? = null,
    onPlayerAppearance: () -> Unit = {}
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
        Text("إعدادات التطبيق", style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary)
        AppButton("مظهر المشغّل", onPlayerAppearance, enabled = !state.isSaving)
        if (state.isLoading) {
            CircularProgressIndicator()
            Text("جارٍ قراءة الإعدادات...")
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
        state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (state.loadFailed) {
            AppButton("إعادة قراءة الإعدادات", viewModel::loadSavedSettings)
        } else {
            AppButton(
                text = if (state.isSaving) "جارٍ الحفظ..." else "حفظ الإعدادات وفتح القنوات",
                onClick = viewModel::saveSettings,
                enabled = !state.isLoading && !state.isSaving,
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (state.isSaving) CircularProgressIndicator()
        if (onBack != null) {
            Row { AppButton("رجوع", onBack, enabled = !state.isSaving) }
        }
    }
}