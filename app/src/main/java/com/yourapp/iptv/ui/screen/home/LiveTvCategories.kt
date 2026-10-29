package com.yourapp.iptv.ui.screen.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.yourapp.iptv.domain.model.Channel
import com.yourapp.iptv.domain.organization.*
import com.yourapp.iptv.ui.component.organization.organizationFolderLabels
import com.yourapp.iptv.ui.presentation.FolderDisplayOrder
import com.yourapp.iptv.ui.presentation.LiveChannelRows
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

private data class LiveCategory(val key: String?, val title: String, val count: Int)

@Composable
internal fun LiveTvCategories(groups: List<String>, channels: List<Channel>, tree: OrganizationTree,
    selected: String?, labels: HomeLabels, colors: HomeTvPalette, entry: FocusRequester,
    onSelect: (String?) -> Unit, projectionDispatcher: CoroutineDispatcher = Dispatchers.Default) {
    val names = organizationFolderLabels()
    val folder = tree.folder(FolderFilter.id(selected))
    val parent = if (folder != null && tree.children(folder.id).isNotEmpty()) folder.id else folder?.parentId
    val input = remember(groups, channels, tree, parent, names, projectionDispatcher) { Any() }
    val result by produceState<Pair<Any, List<LiveCategory>>?>(null, input) {
        value = input to withContext(projectionDispatcher) {
            if (tree.enabled) {
                val children = tree.children(parent).filter { (tree.counts[it.id] ?: 0) > 0 }
                val titles = children.associate { it.id to it.name.ifBlank { names[it.label ?: FolderLabel.OTHER].orEmpty() } }
                FolderDisplayOrder.folders(children, tree, titles, Locale.getDefault()).map {
                    LiveCategory(FolderFilter.of(it.id), titles[it.id].orEmpty(), tree.counts[it.id] ?: 0)
                }
            } else {
                val counts = channels.groupingBy { it.group }.eachCount()
                groups.map { LiveCategory(it, LiveChannelRows.category(it), counts[it] ?: 0) }
            }
        }
    }
    val entries = result?.takeIf { it.first === input }?.second.orEmpty()
    val list = rememberLazyListState()
    val activeIndex = entries.indexOfFirst { it.key == selected }.let { if (it < 0) 0 else it + if (parent == null) 1 else 2 }
    LaunchedEffect(selected, entries) { if (entries.isNotEmpty()) list.animateScrollToItem(activeIndex) }
    LazyRow(state = list, modifier = Modifier.fillMaxWidth().testTag("live-categories"),
        horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 2.dp)) {
        if (parent != null) item("@parent") {
            LiveTvPill(labels.text("‹ رجوع", "‹ Back", "‹ Zurück"), colors,
                { onSelect(tree.folder(parent)?.parentId?.let(FolderFilter::of)) }, Modifier.testTag("live-category-parent"))
        }
        item("@all") {
            val active = FolderFilter.id(selected) == parent && (tree.enabled || selected == null)
            LiveTvPill(labels.text("الكل", "All", "Alle"), colors,
                { onSelect(parent?.let(FolderFilter::of)) }, Modifier
                    .then(if (activeIndex == 0) Modifier.focusRequester(entry) else Modifier)
                    .testTag("live-category-all").semantics { this.selected = active }, active)
        }
        itemsIndexed(entries, key = { _, item -> item.key.orEmpty() }) { index, item ->
            val active = item.key == selected
            LiveTvPill("${item.title} · ${item.count}", colors, { onSelect(item.key) }, Modifier
                .then(if (index + (if (parent == null) 1 else 2) == activeIndex) Modifier.focusRequester(entry) else Modifier)
                .testTag("live-category-${item.key}").semantics { this.selected = active }, active)
        }
    }
}