package com.yourapp.iptv.ui.screen.home

import androidx.compose.runtime.*
import androidx.compose.ui.focus.*
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
    onSelect: (String?) -> Unit, projectionDispatcher: CoroutineDispatcher = Dispatchers.Default,
    listEntry: FocusRequester? = null, header: FocusRequester? = null,
    sidebar: FocusRequester? = null, sidebarOnRight: Boolean = false) {
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
    val targets = buildList {
        if (parent != null) add(LiveTvCategoryTarget("@parent", "live-category-parent",
            labels.text("‹ رجوع", "‹ Back", "‹ Zurück"), false) {
            onSelect(tree.folder(parent)?.parentId?.let(FolderFilter::of))
        })
        add(LiveTvCategoryTarget("@all", "live-category-all", labels.text("الكل", "All", "Alle"),
            FolderFilter.id(selected) == parent && (tree.enabled || selected == null)) { onSelect(parent?.let(FolderFilter::of)) })
        entries.forEach { item ->
            add(LiveTvCategoryTarget("group:${item.key}", "live-category-${item.key}",
                "${item.title} · ${item.count}", item.key == selected) { onSelect(item.key) })
        }
    }
    val activeIndex = targets.indexOfFirst { it.selected }.coerceAtLeast(0)
    LiveTvCategoryFocus(targets, activeIndex, colors, entry, header, listEntry, sidebar, sidebarOnRight)
}