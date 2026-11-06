package mnstudio.apps.mnstreamx.ui.screen.home

import mnstudio.apps.mnstreamx.domain.model.Channel
import mnstudio.apps.mnstreamx.domain.model.HomeDashboardSelection

internal enum class HomeFeaturedKind { RECENT, FAVORITES, DISCOVER }

internal data class HomeFeaturedResult(
    val channels: List<Channel>,
    val kind: HomeFeaturedKind
)

internal object HomeFeaturedChannels {
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