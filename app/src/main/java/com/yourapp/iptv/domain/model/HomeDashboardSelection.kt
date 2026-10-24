package com.yourapp.iptv.domain.model

/** A source-local, bounded projection. No catalog or stream data is persisted here. */
data class HomeDashboardSelection(
    val recent: List<Channel>,
    val favorites: List<Channel>,
    val highlighted: Channel?
) {
    companion object {
        fun project(channels: List<Channel>, recentIds: List<String>, limit: Int = 5): HomeDashboardSelection {
            require(limit in 1..12)
            val lookup = channels.associateBy { it.id }
            val recent = recentIds.asSequence().distinct().mapNotNull(lookup::get).take(limit).toList()
            val favorites = channels.asSequence().filter { it.isFavorite }.take(limit).toList()
            return HomeDashboardSelection(recent, favorites,
                recent.firstOrNull() ?: favorites.firstOrNull())
        }
    }
}