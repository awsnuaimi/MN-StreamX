package com.yourapp.iptv.ui.screen.home

import com.yourapp.iptv.domain.model.Channel
import com.yourapp.iptv.domain.model.HomeDashboardSelection

/** Which channel strip is displayed first, above Hero on compact TV screens. */
internal enum class HomeFeaturedKind { RECENT, FAVORITES, DISCOVER }

internal data class HomeFeaturedResult(
    val channels: List<Channel>,
    val kind: HomeFeaturedKind
)

internal object HomeFeaturedChannels {
    /** Pure projection, bounded to five and never fetches a stream or image. */
    fun select(
        selection: HomeDashboardSelection,
        suggestedChannels: List<Channel>,
        limit: Int = 5
    ): HomeFeaturedResult {
        require(limit in 1..12)
        val (candidate, kind) = when {
            selection.recent.isNotEmpty() -> selection.recent to HomeFeaturedKind.RECENT
            selection.favorites.isNotEmpty() -> selection.favorites to HomeFeaturedKind.FAVORITES
            else -> suggestedChannels to HomeFeaturedKind.DISCOVER
        }
        return HomeFeaturedResult(candidate.distinctBy(Channel::id).take(limit), kind)
    }
}