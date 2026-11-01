package com.yourapp.iptv.domain.model

import org.junit.Assert.*
import org.junit.Test

class VodSeriesContinuationTest {
    private val series = "800"
    private fun ep(id: String, number: Int, season: Int = 1, ext: String = "mp4") =
        VodEpisode(id, "Folge $id", season, number, ext)
    private val seasons = listOf(VodSeason(2, "Two", listOf(ep("30",1,2))),
        VodSeason(1, "One", listOf(ep("20",2),ep("10",1))))
    private fun saved(id: String, at: Long, parent: String = series) =
        VodProgressEntry(VodKind.SERIES,id,"Folge","mp4",40_000,100_000,at,parent)
    private fun watched(id: String, at: Long, parent: String = series) =
        VodCompletedEpisode(id,parent,at)

    @Test fun newestResumableEpisodeWinsEvenWhenOtherEpisodesAreUnwatched() {
        val choice = VodSeriesContinuation.choose(series,seasons,listOf(saved("20",8), saved("30",9)),emptyList())!!
        assertEquals("30",choice.episode.id)
        assertEquals(VodSeriesChoiceKind.RESUME,choice.kind)
        assertEquals(40_000,choice.positionMs)
    }
    @Test fun firstUnwatchedEpisodeIsOrderedBySeasonAndEpisode() {
        val choice = VodSeriesContinuation.choose(series,seasons,emptyList(),listOf(watched("10",9)))!!
        assertEquals("20",choice.episode.id)
        assertEquals(VodSeriesChoiceKind.START,choice.kind)
    }
    @Test fun allCompletedRequiresExplicitReplay() {
        val choice = VodSeriesContinuation.choose(series,seasons,emptyList(),
            listOf(watched("10",1),watched("20",2),watched("30",3)))!!
        assertEquals("10",choice.episode.id)
        assertEquals(VodSeriesChoiceKind.REWATCH,choice.kind)
    }
    @Test fun aNewerCompletionSuppressesAStaleResume() {
        val choice = VodSeriesContinuation.choose(series,seasons,listOf(saved("20",5)),
            listOf(watched("10",2),watched("20",6)))!!
        assertEquals("30",choice.episode.id)
        assertEquals(VodSeriesChoiceKind.START,choice.kind)
    }
    @Test fun aNewerRestartWinsAgainstAnOlderCompletion() {
        val choice = VodSeriesContinuation.choose(series,seasons,listOf(saved("20",7)),
            listOf(watched("20",6)))!!
        assertEquals("20",choice.episode.id)
        assertEquals(VodSeriesChoiceKind.RESUME,choice.kind)
    }
    @Test fun crossAccountAndCrossSeriesRowsNeverAffectTheSuggestion() {
        val choice = VodSeriesContinuation.choose(series,seasons,listOf(saved("20",99,"other")),
            listOf(watched("10",88,"other")))!!
        assertEquals("10",choice.episode.id)
        assertEquals(VodSeriesChoiceKind.START,choice.kind)
    }
    @Test fun invalidProviderEpisodesAreNeverSuggested() {
        val choice = VodSeriesContinuation.choose(series,listOf(VodSeason(1,"One",listOf(
            ep("bad",1),ep("10",2,ext="exe"),ep("42",3)))),emptyList(),emptyList())!!
        assertEquals("42",choice.episode.id)
        assertNull(VodSeriesContinuation.choose(series,listOf(VodSeason(1,"One",listOf(ep("oops",1)))),
            emptyList(),emptyList()))
        assertNull(VodSeriesContinuation.choose("invalid",seasons,emptyList(),emptyList()))
    }
    @Test fun completionStatusAppliesOnlyToExactSeriesAndEpisode() {
        val choice = VodSeriesContinuation.choose(series,seasons,emptyList(),
            listOf(watched("30",10,"999"),watched("10",11,"800")))!!
        assertEquals("20",choice.episode.id)
    }
}