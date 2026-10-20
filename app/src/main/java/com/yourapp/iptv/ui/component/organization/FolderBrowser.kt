package com.yourapp.iptv.ui.component.organization

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yourapp.iptv.R
import com.yourapp.iptv.domain.organization.*
import com.yourapp.iptv.ui.component.common.AppButton

@Composable
fun FolderBrowser(tree: OrganizationTree, selected: String?, modifier: Modifier = Modifier,
    onSelect: (String?) -> Unit) {
    val folder = tree.folder(FolderFilter.id(selected))
    val parent = if (folder != null && tree.children(folder.id).isNotEmpty()) folder.id else folder?.parentId
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(if (parent != null) folderPath(tree, parent) else stringResource(R.string.org_all_folders),
            maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
        if (parent != null) AppButton(stringResource(R.string.ui_back), {
            onSelect(tree.folder(parent)?.parentId?.let(FolderFilter::of))
        })
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(3.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            item("@all") {
                OrganizerRow(stringResource(if (parent == null) R.string.org_all_folders else R.string.org_show_all),
                    selected = FolderFilter.id(selected) == parent) { onSelect(parent?.let(FolderFilter::of)) }
            }
            items(tree.children(parent).filter { (tree.counts[it.id] ?: 0) > 0 }, key = { it.id }) { child ->
                OrganizerRow(folderName(child), subtitle = stringResource(R.string.org_count, tree.counts[child.id] ?: 0),
                    selected = FolderFilter.id(selected) == child.id) { onSelect(FolderFilter.of(child.id)) }
            }
        }
    }
}