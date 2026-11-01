package com.yourapp.iptv.ui.screen.vod

import com.yourapp.iptv.domain.model.VodEpisode
import com.yourapp.iptv.domain.model.VodKind
import com.yourapp.iptv.domain.model.VodProgress
import com.yourapp.iptv.domain.model.VodProgressEntry
import com.yourapp.iptv.domain.model.VodCompletedEpisode

/** Local resume markers only, scoped to the playing series and its known provider episodes.
 * Completed episodes are separate from resumable positions.
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
/** Only display completed episodes belonging to the active series and provider list. */
internal fun vodEpisodeWatched(episodes: List<VodEpisode>, seriesId: String?,
    completed: List<VodCompletedEpisode>, saved: List<VodProgressEntry>): Set<String> {
    if (seriesId.isNullOrEmpty() || episodes.isEmpty()) return emptySet()
    val allowed = episodes.mapTo(HashSet(episodes.size)) { it.id }
    val newerPositions = saved.asSequence().filter { it.kind == VodKind.SERIES && it.seriesId == seriesId &&
        VodProgress.resumable(it) }.associateBy { it.id }
    return completed.asSequence().filter { it.seriesId == seriesId && it.id in allowed &&
        (newerPositions[it.id]?.updatedAtMs ?: -1L) <= it.updatedAtMs }.map { it.id }.toSet()
}