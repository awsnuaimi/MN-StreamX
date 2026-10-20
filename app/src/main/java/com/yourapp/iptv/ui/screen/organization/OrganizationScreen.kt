package com.yourapp.iptv.ui.screen.organization

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yourapp.iptv.R
import com.yourapp.iptv.domain.organization.*
import com.yourapp.iptv.ui.presentation.OrganizationSearchRows
import com.yourapp.iptv.ui.component.common.AppButton
import com.yourapp.iptv.ui.component.organization.*
import com.yourapp.iptv.viewmodel.*

private data class EditorDisplay(val tab: Int, val preview: OrganizedChannels, val rows: OrganizationSearchRows)
private data class MoveTarget(val group: String? = null, val channel: String? = null, val current: String? = null, val bulk: Boolean = false)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OrganizationScreen(onBack: () -> Unit, viewModel: OrganizationEditorViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val value = state.value
    val preview = state.preview
    val editable = value != null && !state.loading && !state.saving && state.issue != OrganizationIssue.READ
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var lastDisplay by remember { mutableStateOf<EditorDisplay?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var previewFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var bulk by rememberSaveable { mutableStateOf(false) }
    var hadBulk by remember { mutableStateOf(false) }
    val bulkFocus = remember { FocusRequester() }
    var reviewOnly by rememberSaveable { mutableStateOf(false) }
    var sortDialog by remember { mutableStateOf(false) }
    var discard by remember { mutableStateOf(false) }
    var target by remember { mutableStateOf<MoveTarget?>(null) }
    var folderEditor by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ChannelFolder?>(null) }
    fun leave() { if (state.dirty) discard = true else onBack() }
    BackHandler {
        if (!state.saving) {
            if (bulk) { bulk = false; viewModel.clearGroupSelection() } else leave()
        }
    }
    LaunchedEffect(state.saved) { if (state.saved) onBack() }
    LaunchedEffect(bulk, tab) {
        if (!bulk && hadBulk && tab == 1) { withFrameNanos { }; runCatching { bulkFocus.requestFocus() } }
        hadBulk = bulk
    }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
            val scope = if (tab == 0) FolderFilter.id(preview.resolveFilter(previewFilter)) else null
            val rows = organizationSearchRows(preview, query, scope, reviewOnly && tab != 3)
            val display = rows?.let { EditorDisplay(tab, preview, it) } ?: lastDisplay?.takeIf { it.tab == tab }
                ?: EditorDisplay(tab, preview, OrganizationSearchRows(emptyList(), emptyList(), emptyList(), emptyMap()))
            SideEffect { if (rows != null) lastDisplay = display }
            val ready = rows != null && !state.computing
            val listRows = display.rows
            val listPreview = display.preview
            val controlsHeight = (LocalConfiguration.current.screenHeightDp.dp * 0.45f).coerceAtLeast(140.dp)
            Column(Modifier.fillMaxWidth().heightIn(max = controlsHeight).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Switch(value.enabled, viewModel::enable, enabled = editable)
                Text(stringResource(R.string.org_enabled), Modifier.padding(top = 10.dp), style = MaterialTheme.typography.bodySmall)
                FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(R.string.org_preview, R.string.org_groups, R.string.org_channels, R.string.org_folders).forEachIndexed { i, label ->
                        FilterChip(tab == i, {
                            tab = i
                            if (i != 1) { bulk = false; viewModel.clearGroupSelection() }
                        }, label = { Text(stringResource(label)) }, enabled = !state.saving)
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.org_summary, preview.channels.size, preview.groups.size), style = MaterialTheme.typography.labelSmall)
                    Text(stringResource(R.string.org_folder_summary, preview.tree.counts.values.count { it > 0 },
                        preview.tree.counts["unclassified"] ?: 0), style = MaterialTheme.typography.labelSmall)
                }
                if (tab == 1) FilterChip(bulk, { bulk = !bulk; viewModel.clearGroupSelection() },
                    label = { Text(stringResource(R.string.org_bulk_mode)) }, modifier = Modifier.focusRequester(bulkFocus), enabled = editable)
                AppButton(stringResource(R.string.org_reset), viewModel::reset, enabled = editable)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                OutlinedTextField(query, { query = it.take(200) }, singleLine = true, enabled = !state.saving,
                    label = { Text(stringResource(R.string.org_search)) }, modifier = Modifier.weight(1f))
                AppButton(stringResource(R.string.org_sort_button, folderSortName(value.folderSort)), { sortDialog = true }, enabled = editable)
                if (tab != 3) FilterChip(reviewOnly, { reviewOnly = !reviewOnly },
                    label = { Text(stringResource(R.string.org_review_only)) }, enabled = !state.saving)
            }
            if (tab == 1 && bulk) GroupSelectionBar(state.selectedGroups.size, editable, rows != null && !state.computing,
                onAll = { rows?.let { viewModel.selectGroupResults(it.groups.map { group -> group.key }) } },
                onClear = viewModel::clearGroupSelection, onMove = { target = MoveTarget(bulk = true) })
            if (state.computing || rows == null) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (rows == null) Text(stringResource(R.string.org_searching), style = MaterialTheme.typography.bodySmall)
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (tab) {
                    0 -> Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        FolderBrowser(preview.tree, previewFilter, Modifier.weight(0.35f).fillMaxHeight()) { previewFilter = it }
                        Box(Modifier.weight(0.65f).fillMaxHeight()) {
                            key(scope) {
                            OrganizationChannels(listPreview, value, listRows, editable,
                                onMove = { if (ready) target = MoveTarget(channel = it, current = value.channelOverrides[it]) },
                                onClear = { if (ready) viewModel.moveChannel(it, null) })
                            }
                        }
                    }
                    1 -> OrganizationGroups(listPreview, value, listRows, editable, bulk, state.selectedGroups,
                        onMove = { if (ready) target = MoveTarget(group = it.key, current = value.groupOverrides[it.key]) },
                        onClear = { if (ready) viewModel.moveGroup(it, null) }, onToggle = { if (ready) viewModel.toggleGroupSelection(it) })
                    2 -> OrganizationChannels(listPreview, value, listRows, editable,
                        onMove = { if (ready) target = MoveTarget(channel = it, current = value.channelOverrides[it]) },
                        onClear = { if (ready) viewModel.moveChannel(it, null) })
                    else -> OrganizationFolders(listPreview.tree, listRows, editable,
                        onEdit = { if (ready) { editing = it; folderEditor = true } },
                        onReorder = { id, direction -> if (ready) viewModel.reorder(id, direction) })
                }
            }
        } else Spacer(Modifier.weight(1f))

    }
    if (target != null && preview != null) {
        val move = target!!
        FolderPickerDialog(preview.tree, move.current, onChoose = { destination ->
            if (move.bulk && viewModel.moveSelectedGroups(destination)) bulk = false
            move.group?.let { viewModel.moveGroup(it, destination) }
            move.channel?.let { viewModel.moveChannel(it, destination) }
            target = null
        }, onDismiss = { target = null }, nullableLabel = stringResource(R.string.org_automatic))
    }
    if (sortDialog && value != null) OrganizationSortDialog(value.folderSort,
        onChoose = { viewModel.setFolderSort(it); sortDialog = false }, onDismiss = { sortDialog = false })
    if (folderEditor && preview != null) FolderEditorDialog(preview.tree, editing,
        onSave = { name, parent -> viewModel.folder(editing?.id, name, parent); folderEditor = false },
        onDismiss = { folderEditor = false })
    if (discard) AlertDialog(onDismissRequest = { discard = false }, title = { Text(stringResource(R.string.org_discard_title)) },
        text = { Text(stringResource(R.string.org_discard_message)) },
        confirmButton = { AppButton(stringResource(R.string.org_discard), onBack) },
        dismissButton = { AppButton(stringResource(R.string.org_stay), { discard = false }) })
}