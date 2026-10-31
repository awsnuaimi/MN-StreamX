package com.yourapp.iptv.ui.screen.vod

import com.yourapp.iptv.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class VodEpisodeProgressTest {
    private val episodes = listOf(
        VodEpisode("21","First",1,1,"mp4"),
        VodEpisode("22","Second",1,2,"mp4")
    )
    private fun progress(id: String, series: String? = "800", kind: VodKind = VodKind.SERIES,
        position: Long = 60_000L, duration: Long = 120_000L) =
        VodProgressEntry(kind,id,"Episode", "mp4",position,duration,1_000L,series)

    @Test fun onlyRealResumableEpisodesOfTheActiveSeriesAreDisplayed() {
        val markers = vodEpisodeProgress(episodes,"800",listOf(
            progress("21"),progress("22","999"),progress("21","800",VodKind.MOVIE),
            progress("93"),progress("22",position = 118_000L)))
        assertEquals(mapOf("21" to .5f),markers)
    }

    @Test fun emptyAndUnavailableContextNeverProducesMarkers() {
        assertTrue(vodEpisodeProgress(emptyList(),"800",listOf(progress("21"))).isEmpty())
        assertTrue(vodEpisodeProgress(episodes,null,listOf(progress("21"))).isEmpty())
        assertTrue(vodEpisodeProgress(episodes,"800",emptyList()).isEmpty())
        assertTrue(vodEpisodeProgress(episodes,"800",listOf(progress("22",series = null))).isEmpty())
    }

    @Test fun rejectShortInvalidOrCompletedResumeEntries() {
        val result = vodEpisodeProgress(episodes,"800",listOf(
            progress("21",position = 5_000L),progress("22",duration = 0L)))
        assertTrue(result.isEmpty())
    }

    @Test fun fractionIsFiniteAndSafeForLargeDurations() {
        val huge = vodEpisodeProgress(episodes,"800",listOf(progress("21",position = Long.MAX_VALUE/4,
            duration = Long.MAX_VALUE/2)))
        assertEquals(.5f,huge.getValue("21"),.0001f)
    }
}