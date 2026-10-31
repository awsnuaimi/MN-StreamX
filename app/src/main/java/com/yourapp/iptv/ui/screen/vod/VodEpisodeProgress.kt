package com.yourapp.iptv.ui.screen.vod

import com.yourapp.iptv.domain.model.VodEpisode
import com.yourapp.iptv.domain.model.VodKind
import com.yourapp.iptv.domain.model.VodProgress
import com.yourapp.iptv.domain.model.VodProgressEntry

/** Local resume markers only, scoped to the playing series and its known provider episodes.
 * Completed episodes are intentionally absent: the progress store does not retain them.
 */
internal fun vodEpisodeProgress(episodes: List<VodEpisode>, seriesId: String?,
    saved: List<VodProgressEntry>): Map<String,Float> {
    if (seriesId.isNullOrEmpty() || episodes.isEmpty() || saved.isEmpty()) return emptyMap()
    val allowed = episodes.mapTo(HashSet(episodes.size)) { it.id }
    return saved.asSequence()
        .filter { it.kind == VodKind.SERIES && it.seriesId == seriesId &&
            it.id in allowed && VodProgress.resumable(it) }
        .associate { it.id to (it.positionMs.toDouble() / it.durationMs).toFloat().coerceIn(0f,1f) }
}