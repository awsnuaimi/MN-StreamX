package mnstudio.apps.mnstreamx.ui.screen.vod

import mnstudio.apps.mnstreamx.domain.model.VodEpisode

/** Pure presentation policy. No provider lookup or playback side effect. */
internal fun vodSeasonNumbers(episodes: List<VodEpisode>): List<Int> =
    episodes.map(VodEpisode::season).distinct().sorted()

internal fun vodInitialSeason(episodes: List<VodEpisode>, currentId: String): Int? =
    episodes.firstOrNull { it.id == currentId }?.season ?: vodSeasonNumbers(episodes).firstOrNull()

internal fun vodEpisodesInSeason(episodes: List<VodEpisode>, season: Int): List<VodEpisode> =
    episodes.asSequence().filter { it.season == season }
        .sortedWith(compareBy<VodEpisode> { it.number }.thenBy { it.id }).toList()