package com.yourapp.iptv.domain.model

/** Only small identifiers are persisted; never channel lists, stream URLs or credentials. */
data class HomeGridPosition(val anchorId: String? = null, val offsetPx: Int = 0, val columns: Int = 0)

data class HomeBrowseSession(
    val group: String? = null,
    val rootFolderId: String? = null,
    val favoritesOnly: Boolean = false,
    val focusedChannelId: String? = null,
    val position: HomeGridPosition = HomeGridPosition(),
    val bookmarks: List<HomeBrowseBookmark> = emptyList()
)

data class HomeGridRestore(val token: Long, val position: HomeGridPosition, val takeFocus: Boolean = true)

data class HomeBrowseBookmark(val group: String? = null, val rootFolderId: String? = null,
    val favoritesOnly: Boolean = false, val focusedChannelId: String? = null,
    val position: HomeGridPosition = HomeGridPosition()) {
    fun session() = HomeBrowseSession(group, rootFolderId, favoritesOnly, focusedChannelId, position)
}