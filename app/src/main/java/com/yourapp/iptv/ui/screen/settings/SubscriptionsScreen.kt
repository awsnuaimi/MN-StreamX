package com.yourapp.iptv.ui.screen.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yourapp.iptv.R
import com.yourapp.iptv.ui.component.common.AppButton
import com.yourapp.iptv.viewmodel.SettingsViewModel

@Composable
fun SubscriptionsScreen(onBack: () -> Unit, onUsed: () -> Unit,
    model: SettingsViewModel = hiltViewModel()) {
    val state by model.uiState.collectAsStateWithLifecycle()
    var exit by remember { mutableStateOf(false) }
    val back = { if (state.dirty) exit = true else onBack() }
    BackHandler(state.isSaving || state.dirty) { if (!state.isSaving) exit = true }
    LaunchedEffect(state.isSaved) { if (state.isSaved) { model.resetSaveStatus(); onUsed() } }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.subscription_title), style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary)
            AppButton(stringResource(R.string.ui_back), back, enabled = !state.isSaving)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)) { SubscriptionSettings(state, model) }
    }
    if (exit) {
        val title = stringResource(R.string.subscription_discard_title)
        val note = stringResource(R.string.subscription_discard_note)
        val confirm = stringResource(R.string.subscription_continue)
        val cancel = stringResource(R.string.subscription_cancel)
        AlertDialog(onDismissRequest = { exit = false }, title = { Text(title) }, text = { Text(note) },
            confirmButton = { TextButton(onClick = { exit = false; onBack() }) { Text(confirm) } },
            dismissButton = { TextButton(onClick = { exit = false }) { Text(cancel) } })
    }
}