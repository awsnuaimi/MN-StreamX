package com.yourapp.iptv.ui.screen.backup

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.yourapp.iptv.domain.model.*

@Composable
internal fun BackupPasswordField(value: String, onChange: (String) -> Unit, label: String, tag: String, enabled: Boolean) {
    val focus = LocalFocusManager.current
    OutlinedTextField(value, { onChange(it.take(128)) }, modifier = Modifier.fillMaxWidth().testTag(tag)
        .onPreviewKeyEvent {
            if (it.key == Key.DirectionDown && it.type == KeyEventType.KeyDown) {
                focus.moveFocus(FocusDirection.Down); true
            } else false
        }, enabled = enabled, singleLine = true, label = { Text(label) },
        visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
}

@Composable
internal fun BackupPreviewContent(preview: BackupPreview, busy: Boolean, labels: BackupLabels,
    onCancel: () -> Unit, onRestore: (BackupRestoreOptions) -> Unit, modifier: Modifier = Modifier) {
    var settings by remember(preview.token) { mutableStateOf(true) }
    var folders by remember(preview.token) { mutableStateOf(true) }
    var activate by remember(preview.token) { mutableStateOf(true) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text(labels.preview(), style = MaterialTheme.typography.titleLarge) }
            item { Text(labels.date(preview.createdAtMillis)) }
            item { Text(labels.profileCount(preview.profileCount)) }
            item { Text(labels.accounts(preview.newSubscriptions, preview.existingSubscriptions)) }
            item { Text(labels.counts(preview.favoriteCount, preview.folderCount, preview.historyCount, preview.progressCount)) }
            item {
                preview.subscriptionNames.forEach { Text(it.ifBlank { labels.defaultName() }, maxLines = 2) }
            }
            item { Text(labels.mergeNote()) }
            item { BackupChoice(settings, { settings = it }, labels.settings(), "backup-option-settings", !busy) }
            item { BackupChoice(folders, { folders = it }, labels.folders(), "backup-option-folders", !busy) }
            item { BackupChoice(activate, { activate = it }, labels.activate(), "backup-option-active", !busy) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { onRestore(BackupRestoreOptions(settings, folders, activate)) }, enabled = !busy,
                modifier = Modifier.testTag("backup-restore")) { Text(labels.restore()) }
            OutlinedButton(onClick = onCancel, enabled = !busy, modifier = Modifier.testTag("backup-cancel")) { Text(labels.cancel()) }
        }
    }
}

@Composable
private fun BackupChoice(checked: Boolean, onChange: (Boolean) -> Unit, label: String, tag: String, enabled: Boolean) {
    Row(Modifier.fillMaxWidth()) {
        Checkbox(checked, onChange, enabled = enabled, modifier = Modifier.testTag(tag))
        Text(label, Modifier.weight(1f).padding(top = 12.dp))
    }
}