package com.yourapp.iptv.ui.component.organization

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.yourapp.iptv.R
import com.yourapp.iptv.domain.organization.*
import com.yourapp.iptv.ui.component.common.AppButton

@Composable
fun FolderEditorDialog(tree: OrganizationTree, folder: ChannelFolder?, onSave: (String, String?) -> Unit, onDismiss: () -> Unit) {
    val title = folder?.let { folderName(it) }.orEmpty()
    var name by rememberSaveable(folder?.id) { mutableStateOf(title) }
    var parent by rememberSaveable(folder?.id) { mutableStateOf(folder?.parentId) }
    var chooseParent by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(if (folder == null) R.string.org_new_folder else R.string.org_edit_folder),
                    style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(name, { name = it.take(80) }, singleLine = true,
                    label = { Text(stringResource(R.string.org_name)) }, modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.org_parent), style = MaterialTheme.typography.labelLarge)
                AppButton(parent?.let { folderPath(tree, it) } ?: stringResource(R.string.org_top_level), { chooseParent = true })
                AppButton(stringResource(R.string.org_done), { onSave(name.trim(), parent) }, enabled = name.isNotBlank())
                AppButton(stringResource(R.string.player_close), onDismiss)
            }
        }
    }
    if (chooseParent) FolderPickerDialog(tree, parent, onChoose = { parent = it; chooseParent = false },
        onDismiss = { chooseParent = false }, nullableLabel = stringResource(R.string.org_top_level),
        excluded = if (folder == null) emptySet() else tree.folders.filter { f ->
            tree.path(f.id).any { it.id == folder.id }
        }.map { it.id }.toSet())
}