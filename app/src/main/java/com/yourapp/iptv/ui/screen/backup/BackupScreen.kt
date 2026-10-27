package com.yourapp.iptv.ui.screen.backup

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.ui.component.common.LocalizedResources
import com.yourapp.iptv.viewmodel.*
import com.yourapp.iptv.ui.screen.parental.LocalParentalSession

@Composable
fun BackupScreen(onBack: () -> Unit, model: BackupViewModel = hiltViewModel(),
    appearanceModel: HomeAppearanceViewModel = hiltViewModel()) {
    val state by model.state.collectAsStateWithLifecycle()
    val appearance by appearanceModel.appearance.collectAsStateWithLifecycle()
    val language = appearance?.language ?: HomeLanguage.ARABIC
    val labels = remember(language) { BackupLabels(language) }
    val protection = LocalParentalSession.current.model
    var external by remember { mutableStateOf<Long?>(null) }
    var returned by remember { mutableStateOf(false) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) {
        model.write(it?.toString()); returned = true
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        model.read(it?.toString()); returned = true
    }
    LaunchedEffect(returned,state.busy) {
        if (returned && !state.busy) { protection?.finishExternal(external); external = null; returned = false }
    }
    LaunchedEffect(state.exportRequest) {
        if (state.exportRequest != null) {
            model.consumeExportRequest()
            try { external = protection?.beginExternal(); returned = false; export.launch(state.exportName) }
            catch (_: Exception) { protection?.finishExternal(external); external = null; model.pickerUnavailable() }
        }
    }
    BackHandler(state.busy) { }
    LocalizedResources(language) {
        BackupContent(state, labels, onBack, model::create, onChoose = {
            try { external = protection?.beginExternal(); returned = false; import.launch(arrayOf("*/*")) }
            catch (_: Exception) { protection?.finishExternal(external); external = null; model.pickerUnavailable() }
        }, onInspect = model::inspect, onCancelPreview = model::clearPreview, onRestore = model::restore)
    }
}

@Composable
internal fun BackupContent(state: BackupUiState, labels: BackupLabels, onBack: () -> Unit,
    onCreate: (CharArray, CharArray) -> Unit, onChoose: () -> Unit, onInspect: (CharArray) -> Unit,
    onCancelPreview: () -> Unit, onRestore: (BackupRestoreOptions) -> Unit) {
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var importPassword by remember(state.fileName) { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(20.dp).testTag("backup-page"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(onClick = onBack, enabled = !state.busy, modifier = Modifier.testTag("backup-back")) { Text(labels.back()) }
            Text(labels.title(), style = MaterialTheme.typography.headlineSmall)
        }
        state.problem?.let { Text(labels.error(it), color = MaterialTheme.colorScheme.error) }
        if (state.working) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (state.exported) Text(labels.saved(), color = MaterialTheme.colorScheme.primary)
        if (state.restored) Text(labels.restored(), color = MaterialTheme.colorScheme.primary)
        val preview = state.preview
        if (preview != null) {
            BackupPreviewContent(preview, state.busy, labels, onCancelPreview, onRestore, Modifier.weight(1f))
        } else LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(labels.description())
                    Text(labels.excluded(), style = MaterialTheme.typography.bodySmall)
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(labels.export(), style = MaterialTheme.typography.titleLarge)
                    BackupPasswordField(password, { password = it }, labels.password(), "backup-password", !state.busy)
                    BackupPasswordField(confirmation, { confirmation = it }, labels.confirmation(), "backup-confirmation", !state.busy)
                    Text(labels.passwordHint(), style = MaterialTheme.typography.bodySmall)
                    Button(onClick = {
                        val first = password.toCharArray(); val second = confirmation.toCharArray()
                        password = ""; confirmation = ""
                        onCreate(first, second)
                    }, enabled = !state.busy, modifier = Modifier.testTag("backup-create")) { Text(labels.export()) }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    HorizontalDivider()
                    Text(labels.import(), style = MaterialTheme.typography.titleLarge)
                    OutlinedButton(onClick = onChoose, enabled = !state.busy, modifier = Modifier.testTag("backup-choose")) { Text(labels.choose()) }
                    state.fileName?.let { name ->
                        Text(name, maxLines = 2)
                        BackupPasswordField(importPassword, { importPassword = it }, labels.password(), "backup-import-password", !state.busy)
                        Button(onClick = {
                            val secret = importPassword.toCharArray(); importPassword = ""; onInspect(secret)
                        }, enabled = !state.busy, modifier = Modifier.testTag("backup-inspect")) { Text(labels.inspect()) }
                    }
                }
            }
        }
    }
}