package com.yourapp.iptv.ui.component.organization

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yourapp.iptv.R
import com.yourapp.iptv.domain.organization.*
import com.yourapp.iptv.ui.component.common.AppButton

@Composable
fun FolderPickerDialog(tree: OrganizationTree, current: String?, onChoose: (String?) -> Unit,
    onDismiss: () -> Unit, nullableLabel: String? = null, excluded: Set<String> = emptySet()) {
    var query by remember { mutableStateOf("") }
    val labels = mutableMapOf<String, String>()
    for (folder in tree.folders) labels[folder.id] = folderPath(tree, folder.id)
    val rows = tree.folders.filter { it.id !in excluded && labels[it.id].orEmpty().contains(query, true) }
        .sortedBy { labels[it.id] }
    val list = rememberLazyListState()
    val focus = remember { FocusRequester() }
    val focusId = current?.takeIf { id -> rows.any { it.id == id } } ?: rows.firstOrNull()?.id
    LaunchedEffect(Unit) {
        val index = rows.indexOfFirst { it.id == focusId } + if (nullableLabel != null) 1 else 0
        list.scrollToItem(if (current == null && nullableLabel != null) 0 else index.coerceAtLeast(0))
        withFrameNanos { }; runCatching { focus.requestFocus() }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.widthIn(max = 680.dp).fillMaxWidth(0.9f).heightIn(max = 500.dp),
            shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.org_pick_folder), style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(query, { query = it.take(100) }, singleLine = true,
                    label = { Text(stringResource(R.string.org_search)) }, modifier = Modifier.fillMaxWidth())
                LazyColumn(state = list, modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(3.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (nullableLabel != null) item("@none") {
                        OrganizerRow(nullableLabel, selected = current == null,
                            modifier = if (current == null) Modifier.focusRequester(focus) else Modifier) { onChoose(null) }
                    }
                    items(rows, key = { it.id }) { folder ->
                        OrganizerRow(labels.getValue(folder.id), subtitle = stringResource(R.string.org_count, tree.counts[folder.id] ?: 0),
                            selected = folder.id == current,
                            modifier = if (folder.id == focusId && !(current == null && nullableLabel != null))
                                Modifier.focusRequester(focus) else Modifier) { onChoose(folder.id) }
                    }
                }
                AppButton(stringResource(R.string.player_close), onDismiss)
            }
        }
    }
}

@Composable
fun FolderFilterButton(tree: OrganizationTree, selected: String?, onSelect: (String?) -> Unit, enabled: Boolean = true) {
    var open by remember { mutableStateOf(false) }
    AppButton(organizationFilterName(selected, tree) ?: stringResource(R.string.org_all_folders), { open = true }, enabled = enabled)
    if (open) FolderPickerDialog(tree, FolderFilter.id(selected),
        onChoose = { onSelect(it?.let(FolderFilter::of)); open = false }, onDismiss = { open = false },
        nullableLabel = stringResource(R.string.org_all_folders))
}