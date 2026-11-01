package com.yourapp.iptv.ui.screen.vod

import com.yourapp.iptv.domain.model.VodEpisode
import com.yourapp.iptv.domain.model.VodSeason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VodDetailsSeasonNavigationTest {
    private fun season(n: Int, size: Int) = VodSeason(n, "Season $n", (1..size).map { i ->
        VodEpisode("$n-$i", "Episode $i", n, i, "mp4")
    })

    @Test fun singleSeasonNeedsNoExtraFocusTarget() {
        assertEquals(emptyList<String>(), VodDetailsSeasonNavigation.keys(listOf(season(1, 2))))
    }

    @Test fun seasonJumpAccountsForTheLazySeasonHeadersAndAllPriorEpisodes() {
        val seasons = listOf(season(1, 12), season(2, 3), season(7, 0))
        assertEquals(listOf("season-jump:1", "season-jump:2", "season-jump:7"),
            VodDetailsSeasonNavigation.keys(seasons))
        assertEquals(0, VodDetailsSeasonNavigation.rowIndex(seasons, 1))
        assertEquals(13, VodDetailsSeasonNavigation.rowIndex(seasons, 2))
        assertEquals(17, VodDetailsSeasonNavigation.rowIndex(seasons, 7))
        assertNull(VodDetailsSeasonNavigation.rowIndex(seasons, 99))
    }

    @Test fun emptyAndLargeSeasonsNeverProduceAnInvalidIndex() {
        assertNull(VodDetailsSeasonNavigation.rowIndex(emptyList(), 1))
        val seasons = (1..100).map { season(it, it % 12) }
        for (n in 1..100) {
            val expected = seasons.take(n - 1).sumOf { it.episodes.size + 1 }
            assertEquals(expected, VodDetailsSeasonNavigation.rowIndex(seasons, n))
        }
    }
}