package com.yourapp.iptv.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yourapp.iptv.R
import com.yourapp.iptv.ui.component.common.AppButton
import com.yourapp.iptv.viewmodel.StorageManagementViewModel
import java.util.Locale

@Composable
fun StorageManagementSection(viewModel: StorageManagementViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmation by remember { mutableStateOf(false) }
    val modelBytes = state.snapshot?.modelBytes ?: 0L

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.storage_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.storage_description), style = MaterialTheme.typography.bodyMedium)
        state.snapshot?.let { snapshot ->
            Text(stringResource(R.string.storage_available, formatSize(snapshot.freeBytes)))
            Text(stringResource(R.string.storage_downloaded, formatSize(snapshot.modelBytes)))
        }
        AppButton(stringResource(R.string.storage_refresh), viewModel::refresh, enabled = !state.busy)
        AppButton(stringResource(R.string.storage_delete_button), { confirmation = true },
            modifier = Modifier.fillMaxWidth(), enabled = !state.busy && modelBytes > 0L)
        if (state.busy) Text(stringResource(R.string.storage_working))
        if (state.blocked) Text(stringResource(R.string.storage_busy), color = MaterialTheme.colorScheme.error)
        if (state.failure) Text(stringResource(R.string.storage_error), color = MaterialTheme.colorScheme.error)
        state.deletedBytes?.let { Text(stringResource(R.string.storage_deleted, formatSize(it))) }
        Text(stringResource(R.string.storage_apk_note), style = MaterialTheme.typography.bodySmall)
    }
    if (confirmation) AlertDialog(
        onDismissRequest = { confirmation = false },
        title = { Text(stringResource(R.string.storage_confirm_title)) },
        text = { Text(stringResource(R.string.storage_confirm_message, formatSize(modelBytes))) },
        confirmButton = {
            TextButton(onClick = { confirmation = false; viewModel.deleteModels() }) {
                Text(stringResource(R.string.storage_confirm_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = { confirmation = false }) { Text(stringResource(R.string.storage_cancel)) }
        }
    )
}

private fun formatSize(bytes: Long): String =
    String.format(Locale.ROOT, "%.1f MiB", bytes.coerceAtLeast(0).toDouble() / (1024.0 * 1024.0))