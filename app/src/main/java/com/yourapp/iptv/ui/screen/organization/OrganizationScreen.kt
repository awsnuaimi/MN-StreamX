package com.yourapp.iptv.ui.screen.organization

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yourapp.iptv.R
import com.yourapp.iptv.domain.model.PlaybackQueue
import com.yourapp.iptv.domain.organization.*
import com.yourapp.iptv.ui.component.common.AppButton
import com.yourapp.iptv.ui.component.organization.*
import com.yourapp.iptv.viewmodel.*

private data class MoveTarget(val group: String? = null, val channel: String? = null, val current: String? = null)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OrganizationScreen(onBack: () -> Unit, viewModel: OrganizationEditorViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val value = state.value
    val preview = state.preview
    val editable = value != null && !state.loading && !state.saving && state.issue != OrganizationIssue.READ
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var previewFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var discard by remember { mutableStateOf(false) }
    var target by remember { mutableStateOf<MoveTarget?>(null) }
    var folderEditor by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ChannelFolder?>(null) }
    fun leave() { if (state.dirty) discard = true else onBack() }
    BackHandler { if (!state.saving) leave() }
    LaunchedEffect(state.saved) { if (state.saved) onBack() }
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.org_title), Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary)
            AppButton(stringResource(if (state.saving) R.string.ui_saving else R.string.org_apply), viewModel::save,
                enabled = editable && !state.computing && state.issue == null)
            AppButton(stringResource(R.string.ui_back), ::leave, enabled = !state.saving)
        }
        Text(stringResource(R.string.org_intro), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
        if (state.loading) CircularProgressIndicator()
        state.issue?.let { issue ->
            Text(stringResource(when (issue) {
                OrganizationIssue.READ -> R.string.org_read_error
                OrganizationIssue.SAVE -> R.string.org_save_error
                OrganizationIssue.INVALID_FOLDER -> R.string.org_invalid_folder
            }), color = MaterialTheme.colorScheme.error)
            if (issue != OrganizationIssue.INVALID_FOLDER) AppButton(stringResource(R.string.ui_reload), viewModel::load, enabled = !state.saving)
        }
        if (preview != null && value != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Switch(value.enabled, viewModel::enable, enabled = editable)
                Text(stringResource(R.string.org_enabled), Modifier.padding(top = 10.dp), style = MaterialTheme.typography.bodySmall)
                FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(R.string.org_preview, R.string.org_groups, R.string.org_channels, R.string.org_folders).forEachIndexed { i, label ->
                        FilterChip(tab == i, { tab = i }, label = { Text(stringResource(label)) }, enabled = !state.saving)
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.org_summary, preview.channels.size, preview.groups.size), style = MaterialTheme.typography.labelSmall)
                    Text(stringResource(R.string.org_folder_summary, preview.tree.counts.values.count { it > 0 },
                        preview.tree.counts["unclassified"] ?: 0), style = MaterialTheme.typography.labelSmall)
                }
                AppButton(stringResource(R.string.org_reset), viewModel::reset, enabled = editable)
            }
            OutlinedTextField(query, { query = it.take(200) }, singleLine = true, enabled = !state.saving,
                label = { Text(stringResource(R.string.org_search)) }, modifier = Modifier.fillMaxWidth())
            if (state.computing) LinearProgressIndicator(Modifier.fillMaxWidth())
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (tab) {
                    0 -> Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        FolderBrowser(preview.tree, previewFilter, Modifier.weight(0.35f).fillMaxHeight()) { previewFilter = it }
                        Box(Modifier.weight(0.65f).fillMaxHeight()) {
                            val scoped = preview.copy(channels = PlaybackQueue.create(preview.channels, preview.resolveFilter(previewFilter)).channels)
                            OrganizationChannels(scoped, value, query, editable,
                                onMove = { target = MoveTarget(channel = it, current = value.channelOverrides[it]) },
                                onClear = { viewModel.moveChannel(it, null) })
                        }
                    }
                    1 -> OrganizationGroups(preview, value, query, editable,
                        onMove = { target = MoveTarget(group = it.key, current = value.groupOverrides[it.key]) },
                        onClear = { viewModel.moveGroup(it, null) })
                    2 -> OrganizationChannels(preview, value, query, editable,
                        onMove = { target = MoveTarget(channel = it, current = value.channelOverrides[it]) },
                        onClear = { viewModel.moveChannel(it, null) })
                    else -> OrganizationFolders(preview.tree, query, editable,
                        onEdit = { editing = it; folderEditor = true }, onReorder = viewModel::reorder)
                }
            }
        } else Spacer(Modifier.weight(1f))

    }
    if (target != null && preview != null) {
        val move = target!!
        FolderPickerDialog(preview.tree, move.current, onChoose = { destination ->
            move.group?.let { viewModel.moveGroup(it, destination) }
            move.channel?.let { viewModel.moveChannel(it, destination) }
            target = null
        }, onDismiss = { target = null }, nullableLabel = stringResource(R.string.org_automatic))
    }
    if (folderEditor && preview != null) FolderEditorDialog(preview.tree, editing,
        onSave = { name, parent -> viewModel.folder(editing?.id, name, parent); folderEditor = false },
        onDismiss = { folderEditor = false })
    if (discard) AlertDialog(onDismissRequest = { discard = false }, title = { Text(stringResource(R.string.org_discard_title)) },
        text = { Text(stringResource(R.string.org_discard_message)) },
        confirmButton = { AppButton(stringResource(R.string.org_discard), onBack) },
        dismissButton = { AppButton(stringResource(R.string.org_stay), { discard = false }) })
}