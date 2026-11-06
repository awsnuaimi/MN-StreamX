package mnstudio.apps.mnstreamx.ui.presentation

import mnstudio.apps.mnstreamx.domain.model.LiveTvPreferences
import mnstudio.apps.mnstreamx.domain.model.PlaybackQueue
import mnstudio.apps.mnstreamx.domain.organization.FolderFilter
import mnstudio.apps.mnstreamx.ui.state.HomeUiState

internal object LiveTvPersonalization {
    fun project(state: HomeUiState, preferences: LiveTvPreferences, checkpoint: () -> Unit = {}): HomeUiState {
        val channels = state.channels.filter { checkpoint();preferences.visible(it) }
        val tree = if (channels.size == state.channels.size) state.organization else state.organization.withCounts(
            PlaybackQueue.filterChannels(channels, query = state.query, favoritesOnly = state.favoritesOnly))
        val availableGroups = channels.mapNotNull { checkpoint();it.group }.toSet()
        val groups = if (tree.enabled) state.groups.filter { key ->
            val id = FolderFilter.id(key)
            id !in preferences.hiddenFolders && (tree.counts[id] ?: 0) > 0
        } else state.groups.filter { group -> group !in preferences.hiddenGroups && group in availableGroups }
        return state.copy(channels = channels, visibleChannels = state.visibleChannels.filter { checkpoint();preferences.visible(it) },
            organization = tree, groups = groups)
    }
}