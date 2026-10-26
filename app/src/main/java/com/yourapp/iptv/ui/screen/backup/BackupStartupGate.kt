package com.yourapp.iptv.ui.screen.backup

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yourapp.iptv.domain.model.*

@Composable
internal fun BackupStartupGate(state: BackupMaintenance, language: HomeLanguage, onRetry: () -> Unit,
    content: @Composable () -> Unit) {
    val labels = BackupLabels(language)
    if (state == BackupMaintenance.READY || state == BackupMaintenance.RESTORING) {
        content()
        if (state == BackupMaintenance.RESTORING) {
            val message = labels.restoring()
            Dialog(onDismissRequest = {}, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)) {
                Surface(shape = MaterialTheme.shapes.large) {
                    Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        CircularProgressIndicator(); Text(message)
                    }
                }
            }
        }
    } else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (state == BackupMaintenance.STARTING) {
                CircularProgressIndicator(); Text(labels.recovery())
            } else {
                Text(labels.error(BackupProblem.RECOVERY))
                Button(onClick = onRetry) { Text(labels.retry()) }
            }
        }
    }
}