package mnstudio.apps.mnstreamx.ui.screen.player

import mnstudio.apps.mnstreamx.domain.model.WatchHistory
import mnstudio.apps.mnstreamx.domain.model.WatchedChannel

/** Pure, stable TV-remote selection rules; never change the persisted history order. */
internal object RecentRailNavigation {
    fun entries(rows: List<WatchedChannel>): List<WatchedChannel> =
        rows.distinctBy { it.channel.id }.take(WatchHistory.LIMIT)

    /** Jump to the last different channel by default, so RIGHT then OK provides quick recall. */
    fun initial(ids: List<String>, playingId: String?): String? =
        ids.firstOrNull { it != playingId } ?: ids.firstOrNull()

    fun move(ids: List<String>, selectedId: String?, playingId: String?, step: Int): String? {
        if (ids.isEmpty()) return null
        val start = ids.indexOf(selectedId).takeIf { it >= 0 }
            ?: ids.indexOf(initial(ids, playingId)).takeIf { it >= 0 } ?: 0
        return ids[(start + step).coerceIn(0, ids.lastIndex)]
    }
}