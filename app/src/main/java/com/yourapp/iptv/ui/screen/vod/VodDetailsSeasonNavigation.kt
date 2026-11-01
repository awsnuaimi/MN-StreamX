package com.yourapp.iptv.ui.screen.vod

import com.yourapp.iptv.domain.model.VodSeason

internal data class VodDetailsPosition(val season: Int, val episodeId: String? = null)

/** Stable, non-playback navigation for a real provider's season list. */
internal object VodDetailsSeasonNavigation {
    fun keys(seasons: List<VodSeason>): List<String> =
        if (seasons.size < 2) emptyList() else seasons.map { "season-jump:${it.number}" }

    /** Only restore entries that still exist in the provider's refreshed season list. */
    fun validPosition(seasons: List<VodSeason>, saved: VodDetailsPosition?): VodDetailsPosition? {
        if (saved == null) return null
        val season = seasons.firstOrNull { it.number == saved.season } ?: return null
        val episode = saved.episodeId?.takeIf { id -> season.episodes.any { it.id == id } }
        return VodDetailsPosition(season.number, episode)
    }

    fun preferredKey(seasons: List<VodSeason>, saved: VodDetailsPosition?): String? {
        val position = validPosition(seasons, saved) ?: return null
        position.episodeId?.let { return "episode:${position.season}:$it" }
        return "season-jump:${position.season}".takeIf { keys(seasons).contains(it) }
    }

    /** LazyColumn index includes each season heading followed by its episodes. */
    fun rowIndex(seasons: List<VodSeason>, number: Int): Int? {
        var index = 0
        for (season in seasons) {
            if (season.number == number) return index
            index += 1 + season.episodes.size
        }
        return null
    }
}