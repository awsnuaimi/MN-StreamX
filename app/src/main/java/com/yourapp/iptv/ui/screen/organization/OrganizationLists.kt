package com.yourapp.iptv.ui.screen.organization

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yourapp.iptv.R
import com.yourapp.iptv.domain.organization.*
import com.yourapp.iptv.ui.component.common.AppButton
import com.yourapp.iptv.ui.component.organization.*

@Composable
internal fun OrganizationGroups(preview: OrganizedChannels, value: OrganizationConfig, query: String,
    enabled: Boolean, onMove: (SourceGroup) -> Unit, onClear: (String) -> Unit) {
    val groups = preview.groups.filter { it.name.orEmpty().contains(query, true) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(3.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item("@hint") {
            Text(stringResource(R.string.org_move), style = MaterialTheme.typography.labelLarge)
            Text(stringResource(R.string.org_rule_hint), style = MaterialTheme.typography.bodySmall)
        }
        items(groups, key = { it.key }) { group ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val target = group.folderIds.singleOrNull()?.let { folderPath(preview.tree, it) }
                    ?: stringResource(R.string.org_destinations, group.folderIds.size)
                OrganizerRow(group.name?.takeIf { it.isNotBlank() } ?: stringResource(R.string.org_no_group),
                    subtitle = stringResource(R.string.org_count, group.count) + " · " + target, enabled = enabled) { onMove(group) }
                if (group.key in value.groupOverrides) AppButton(stringResource(R.string.org_clear_rule),
                    { onClear(group.key) }, enabled = enabled)
            }
        }
        if (groups.isEmpty()) item { Text(stringResource(R.string.org_empty)) }
    }
}

@Composable
internal fun OrganizationChannels(preview: OrganizedChannels, value: OrganizationConfig, query: String,
    enabled: Boolean, onMove: (String) -> Unit, onClear: (String) -> Unit) {
    val channels = preview.channels.filter { it.name.contains(query, true) || it.group.orEmpty().contains(query, true) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(3.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item("@hint") { Text(stringResource(R.string.org_move), style = MaterialTheme.typography.labelLarge) }
        items(channels, key = { it.id }) { channel ->
            val assignment = preview.assignments.getValue(channel.id)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OrganizerRow(channel.name, subtitle = folderPath(preview.tree, assignment.folderId) + " · " + assignmentName(assignment.reason),
                    enabled = enabled) { onMove(channel.id) }
                Text(stringResource(R.string.org_source, channel.group ?: stringResource(R.string.org_no_group)),
                    style = MaterialTheme.typography.labelSmall)
                if (channel.id in value.channelOverrides) AppButton(stringResource(R.string.org_clear_rule),
                    { onClear(channel.id) }, enabled = enabled)
            }
        }
        if (channels.isEmpty()) item { Text(stringResource(R.string.org_empty)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun OrganizationFolders(tree: OrganizationTree, query: String, enabled: Boolean,
    onEdit: (ChannelFolder?) -> Unit, onReorder: (String, Int) -> Unit) {
    val labels = mutableMapOf<String, String>()
    for (folder in tree.folders) labels[folder.id] = folderPath(tree, folder.id)
    val folders = tree.orderedFolders().filter { labels.getValue(it.id).contains(query, true) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(3.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item("@new") { AppButton(stringResource(R.string.org_new_folder), { onEdit(null) }, enabled = enabled) }
        items(folders, key = { it.id }) { folder ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OrganizerRow(labels.getValue(folder.id), subtitle = stringResource(R.string.org_count, tree.counts[folder.id] ?: 0),
                    enabled = enabled) { onEdit(folder) }
                val siblings = tree.children(folder.parentId)
                val index = siblings.indexOfFirst { it.id == folder.id }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppButton(stringResource(R.string.org_up), { onReorder(folder.id, -1) }, enabled = enabled && index > 0)
                    AppButton(stringResource(R.string.org_down), { onReorder(folder.id, 1) }, enabled = enabled && index < siblings.lastIndex)
                }
            }
        }
    }
}