package com.yourapp.iptv.ui.state

import com.yourapp.iptv.domain.organization.OrganizationTree
import com.yourapp.iptv.domain.model.Channel
import com.yourapp.iptv.domain.model.HomeGridRestore

data class HomeUiState(
    val channels: List<Channel> = emptyList(),
    val visibleChannels: List<Channel> = emptyList(),
    val groups: List<String> = emptyList(),
    val query: String = "",
    val selectedGroup: String? = null,
    val favoritesOnly: Boolean = false,
    val selectedChannelId: String? = null,
    val isLoading: Boolean = true,
    val needsSource: Boolean = false,
    val errorMessage: String? = null,
    val organization: OrganizationTree = OrganizationTree(),
    val isRefreshing: Boolean = false,
    val browseScope: String = "",
    val sessionReady: Boolean = false,
    val gridRestore: HomeGridRestore? = null,
    val isFiltering: Boolean = false
)