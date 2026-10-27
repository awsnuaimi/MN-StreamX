package com.yourapp.iptv.ui.screen.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yourapp.iptv.R
import com.yourapp.iptv.domain.model.Subscription
import com.yourapp.iptv.domain.model.HomeLanguage
import com.yourapp.iptv.domain.model.Subscriptions
import com.yourapp.iptv.ui.component.common.AppButton
import com.yourapp.iptv.ui.component.common.secureStorageMessage
import com.yourapp.iptv.ui.screen.recovery.AppResetAction
import com.yourapp.iptv.ui.state.*
import com.yourapp.iptv.viewmodel.SettingsViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SubscriptionSettings(state: SettingsUiState, model: SettingsViewModel) {
    if (state.isLoading) {
        CircularProgressIndicator()
        Text(stringResource(R.string.ui_settings_loading))
    } else if (!state.loadFailed) {
        Text(stringResource(R.string.subscription_hint), style = MaterialTheme.typography.bodySmall)
        state.subscriptions.forEach { account ->
            val active = account.id == state.activeSubscriptionId
            Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(if (active) 2.dp else 1.dp,
                    if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                shape = MaterialTheme.shapes.medium) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(accountTitle(account) + " · " + account.settings.source.name,
                        style = MaterialTheme.typography.titleMedium)
                    if (active) Text(stringResource(R.string.subscription_active), color = MaterialTheme.colorScheme.primary)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (!active && account.settings.hasSelectedSource()) AppButton(stringResource(R.string.subscription_use),
                            { model.request(SubscriptionAction.Activate(account.id)) },
                            Modifier.testTag("subscription-use-${account.id}"), enabled = state.enabled)
                        AppButton(stringResource(R.string.subscription_edit), { model.request(SubscriptionAction.Edit(account.id)) },
                            Modifier.testTag("subscription-edit-${account.id}"), enabled = state.enabled && account.id != state.editingSubscriptionId)
                        if (!active) AppButton(stringResource(R.string.subscription_remove), { model.request(SubscriptionAction.Remove(account.id)) },
                            Modifier.testTag("subscription-remove-${account.id}"), enabled = state.enabled)
                    }
                }
            }
        }
        AppButton(stringResource(R.string.subscription_add), { model.request(SubscriptionAction.New) },
            Modifier.testTag("subscription-add"), enabled = state.enabled && state.subscriptions.size < Subscriptions.LIMIT)
        Text(stringResource(if (state.editingSubscriptionId == null) R.string.subscription_new else R.string.subscription_editing),
            style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(state.subscriptionName, model::updateSubscriptionName, Modifier.fillMaxWidth().testTag("subscription-name"),
            enabled = state.enabled, singleLine = true, label = { Text(stringResource(R.string.subscription_name)) },
            placeholder = { Text(stringResource(R.string.subscription_default)) })
        SettingsInputForm(state.settings, state.enabled, model::updateSource, model::updateM3uUrl,
            model::updateXtreamServer, model::updateXtreamUsername, model::updateXtreamPassword, model::updateEpgUrl)
        if (state.editingSubscriptionId != state.activeSubscriptionId) AppButton(stringResource(R.string.subscription_cancel_edit),
            { model.request(SubscriptionAction.Edit(state.activeSubscriptionId)) }, enabled = state.enabled)
    }
    if (state.storageFailure != null) {
        val language = when (LocalConfiguration.current.locales[0].language) {
            "de" -> HomeLanguage.GERMAN
            "en" -> HomeLanguage.ENGLISH
            else -> HomeLanguage.ARABIC
        }
        Text(secureStorageMessage(state.storageFailure,language),color = MaterialTheme.colorScheme.error)
        AppResetAction(state.storageFailure,language)
    } else state.issue?.let { Text(stringResource(issueText(it)), color = MaterialTheme.colorScheme.error) }
    if (state.loadFailed) AppButton(stringResource(R.string.ui_settings_reload), model::loadSavedSettings)
    else AppButton(stringResource(if (state.isSaving) R.string.ui_saving else R.string.subscription_save_use), model::saveSettings,
        Modifier.fillMaxWidth().testTag("subscription-save"), enabled = state.enabled)
    if (state.isSaving) CircularProgressIndicator()
    state.pending?.let { action ->
        val removing = action is SubscriptionAction.Remove
        // Resolve with the selected app language before Dialog creates its own Android window.
        val title = stringResource(if (removing) R.string.subscription_remove_title else R.string.subscription_discard_title)
        val note = stringResource(if (removing) R.string.subscription_remove_note else R.string.subscription_discard_note)
        val confirm = stringResource(if (removing) R.string.subscription_remove else R.string.subscription_continue)
        val cancel = stringResource(R.string.subscription_cancel)
        AlertDialog(onDismissRequest = model::dismissAction,
            title = { Text(title) }, text = { Text(note) },
            confirmButton = { TextButton(onClick = model::confirmAction, modifier = Modifier.testTag("subscription-confirm")) { Text(confirm) } },
            dismissButton = { TextButton(onClick = model::dismissAction) { Text(cancel) } })
    }
}

@Composable
private fun accountTitle(account: Subscription): String = account.name.ifBlank { stringResource(R.string.subscription_default) }

private fun issueText(issue: SettingsIssue): Int = when (issue) {
    SettingsIssue.READ_FAILED -> R.string.ui_settings_read_error
    SettingsIssue.SAVE_FAILED -> R.string.ui_settings_save_error
    SettingsIssue.M3U_REQUIRED -> R.string.ui_settings_m3u_required
    SettingsIssue.M3U_INVALID -> R.string.ui_settings_m3u_invalid
    SettingsIssue.XTREAM_REQUIRED -> R.string.ui_settings_xtream_required
    SettingsIssue.SERVER_INVALID -> R.string.ui_settings_server_invalid
    SettingsIssue.EPG_INVALID -> R.string.ui_settings_epg_invalid
    SettingsIssue.SUBSCRIPTION_NAME -> R.string.subscription_name_error
    SettingsIssue.SUBSCRIPTION_DUPLICATE -> R.string.subscription_duplicate
    SettingsIssue.SUBSCRIPTION_LIMIT -> R.string.subscription_limit
    SettingsIssue.SUBSCRIPTION_MISSING -> R.string.subscription_missing
    SettingsIssue.SUBSCRIPTION_ACTIVE_DELETE -> R.string.subscription_active_delete
    SettingsIssue.SUBSCRIPTION_INVALID -> R.string.subscription_invalid
}