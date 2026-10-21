package com.yourapp.iptv.ui.component.organization

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yourapp.iptv.R
import com.yourapp.iptv.domain.organization.*
import com.yourapp.iptv.ui.component.common.AppButton
import com.yourapp.iptv.ui.presentation.FolderDisplayOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
fun FolderBrowser(tree: OrganizationTree, selected: String?, modifier: Modifier = Modifier,
    restoreFocusOnNavigation: Boolean = true,
    onSelect: (String?) -> Unit) {
    val folder = tree.folder(FolderFilter.id(selected))
    val parent = if (folder != null && tree.children(folder.id).isNotEmpty()) folder.id else folder?.parentId
    val children = remember(tree, parent) { tree.children(parent).filter { (tree.counts[it.id] ?: 0) > 0 } }
    val labels = organizationFolderLabels()
    val locale = LocalConfiguration.current.locales[0].toLanguageTag()
    val sorted by produceState(children, tree, parent, labels, locale) {
        value = withContext(Dispatchers.Default) {
            val names = children.associate { it.id to it.name.takeIf(String::isNotBlank).orEmpty()
                .ifEmpty { labels[it.label ?: FolderLabel.OTHER].orEmpty() } }
            FolderDisplayOrder.folders(children, tree, names, Locale.forLanguageTag(locale))
        }
    }
    val visible = remember(sorted, children) {
        val ids = children.map { it.id }.toSet()
        sorted.filter { it.id in ids }
    }
    val list = rememberLazyListState()
    val entryFocus = remember { FocusRequester() }
    var visited by remember { mutableStateOf(false) }
    LaunchedEffect(parent) {
        if (visited && restoreFocusOnNavigation) {
            list.scrollToItem(0); withFrameNanos { }; runCatching { entryFocus.requestFocus() }
        }
        visited = true
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(if (parent != null) folderPath(tree, parent) else stringResource(R.string.org_all_folders),
            maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
        if (parent != null) AppButton(stringResource(R.string.ui_back), {
            onSelect(tree.folder(parent)?.parentId?.let(FolderFilter::of))
        })
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, contentPadding = PaddingValues(3.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            item("@all") {
                OrganizerRow(stringResource(if (parent == null) R.string.org_all_folders else R.string.org_show_all),
                    modifier = Modifier.focusRequester(entryFocus),
                    selected = FolderFilter.id(selected) == parent) { onSelect(parent?.let(FolderFilter::of)) }
            }
            items(visible, key = { it.id }) { child ->
                OrganizerRow(folderName(child), subtitle = stringResource(R.string.org_count, tree.counts[child.id] ?: 0),
                    selected = FolderFilter.id(selected) == child.id) { onSelect(FolderFilter.of(child.id)) }
            }
        }
    }
}