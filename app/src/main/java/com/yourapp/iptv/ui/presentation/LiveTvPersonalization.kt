package com.yourapp.iptv.ui.presentation

import com.yourapp.iptv.domain.model.LiveTvPreferences
import com.yourapp.iptv.domain.model.PlaybackQueue
import com.yourapp.iptv.domain.organization.FolderFilter
import com.yourapp.iptv.ui.state.HomeUiState

internal object LiveTvPersonalization {
    /** Input has already passed parental projection; restoring a hidden row cannot add permissions. */
    fun project(state: HomeUiState, preferences: LiveTvPreferences): HomeUiState {
        val channels = state.channels.filter(preferences::visible)
        val tree = if (channels.size == state.channels.size) state.organization else state.organization.withCounts(
            PlaybackQueue.filterChannels(channels, query = state.query, favoritesOnly = state.favoritesOnly))
        val availableGroups = channels.mapNotNull { it.group }.toSet()
        val groups = if (tree.enabled) state.groups.filter { key ->
            val id = FolderFilter.id(key)
            id !in preferences.hiddenFolders && (tree.counts[id] ?: 0) > 0
        } else state.groups.filter { group -> group !in preferences.hiddenGroups && group in availableGroups }
        return state.copy(channels = channels, visibleChannels = state.visibleChannels.filter(preferences::visible),
            organization = tree, groups = groups)
    }
}