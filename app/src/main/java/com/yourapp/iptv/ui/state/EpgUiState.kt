package com.yourapp.iptv.ui.state

import com.yourapp.iptv.domain.organization.OrganizationTree
import com.yourapp.iptv.domain.model.Channel
import com.yourapp.iptv.domain.model.PlaybackQueue

data class EpgUiState(
    val queue: PlaybackQueue = PlaybackQueue.empty(),
    val groups: List<String> = emptyList(),
    val selectedGroup: String? = null,
    val favoritesOnly: Boolean = false,
    val query: String = "",
    val selectedChannelId: String? = null,
    val selectedProgramKey: String? = null,
    val isLoading: Boolean = true,
    val isFiltering: Boolean = false,
    val errorMessage: String? = null,
    val organization: OrganizationTree = OrganizationTree()
) {
    val selectedIndex: Int get() = queue.indexOf(selectedChannelId)
    val channel: Channel? get() = queue.channels.getOrNull(selectedIndex)
}