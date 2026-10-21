package com.yourapp.iptv.domain.model

import com.yourapp.iptv.domain.organization.FolderFilter
import com.yourapp.iptv.domain.organization.OrganizationTree

data class ResolvedHomeSession(val session: HomeBrowseSession, val visible: List<Channel>)

object HomeSessionResolver {
    fun resolve(saved: HomeBrowseSession, channels: List<Channel>, tree: OrganizationTree): ResolvedHomeSession {
        fun exists(group: String?): Boolean {
            if (group == null) return true
            val id = FolderFilter.id(group)
            return if (id != null) tree.enabled && tree.folder(id) != null && (tree.counts[id] ?: 0) > 0
                else channels.any { it.group == group }
        }
        var group = saved.group?.takeIf(::exists)
        if (saved.group != null && group == null && tree.enabled) {
            group = saved.rootFolderId?.let(FolderFilter::of)?.takeIf(::exists)
        }
        if (group != null && tree.enabled && FolderFilter.id(group) == null) {
            val members = channels.filter { it.group == group }
            val common = members.firstOrNull()?.folderIds.orEmpty()
                .takeWhile { id -> members.all { id in it.folderIds } }
            group = common.lastOrNull()?.let(FolderFilter::of)
        }
        var favorites = saved.favoritesOnly
        var visible = PlaybackQueue.filterChannels(channels, group, favoritesOnly = favorites)
        if (visible.isEmpty() && favorites) {
            favorites = false
            visible = PlaybackQueue.filterChannels(channels, group)
        }
        if (visible.isEmpty() && group != null) { group = null; visible = channels }
        val ids = visible.mapTo(HashSet(visible.size)) { it.id }
        val focused = saved.focusedChannelId?.takeIf { it in ids }
            ?: saved.position.anchorId?.takeIf { it in ids } ?: visible.firstOrNull()?.id
        val anchor = saved.position.anchorId?.takeIf { it in ids }
        val position = saved.position.copy(anchorId = anchor ?: focused,
            offsetPx = if (anchor == null) 0 else saved.position.offsetPx.coerceIn(0, 100_000))
        val root = FolderFilter.id(group)?.let { tree.path(it).firstOrNull()?.id }
        return ResolvedHomeSession(HomeBrowseSession(group, root, favorites, focused, position), visible)
    }
}