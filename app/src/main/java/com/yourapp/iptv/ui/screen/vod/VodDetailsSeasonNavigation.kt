package com.yourapp.iptv.ui.screen.vod

import com.yourapp.iptv.domain.model.VodSeason

/** Stable, non-playback navigation for a real provider's season list. */
internal object VodDetailsSeasonNavigation {
    fun keys(seasons: List<VodSeason>): List<String> =
        if (seasons.size < 2) emptyList() else seasons.map { "season-jump:${it.number}" }

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