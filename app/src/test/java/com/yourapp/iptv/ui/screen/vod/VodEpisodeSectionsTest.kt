package com.yourapp.iptv.ui.screen.vod

import com.yourapp.iptv.domain.model.VodEpisode
import org.junit.Assert.*
import org.junit.Test

class VodEpisodeSectionsTest {
    private fun e(id: String, season: Int, number: Int) = VodEpisode(id,"Episode $number",season,number,"mp4")

    @Test fun availableSeasonsAreUniqueAndInNumericOrder() {
        val source = listOf(e("4",3,1),e("1",1,3),e("2",2,1),e("3",1,2),e("5",3,2))
        assertEquals(listOf(1,2,3),vodSeasonNumbers(source))
        assertEquals(listOf("3","1"),vodEpisodesInSeason(source,1).map { it.id })
    }

    @Test fun episodePanelOpensAtTheActiveEpisodesSeasonRatherThanTheFirstSeason() {
        val source = listOf(e("first",1,1),e("current",4,9),e("later",4,10))
        assertEquals(4,vodInitialSeason(source,"current"))
        assertEquals(listOf("current","later"),vodEpisodesInSeason(source,4).map { it.id })
    }

    @Test fun missingCurrentEpisodeFallsBackSafelyAndNeverManufacturesAnEpisode() {
        assertNull(vodInitialSeason(emptyList(),"missing"))
        assertEquals(emptyList<VodEpisode>(),vodEpisodesInSeason(emptyList(),1))
        val source = listOf(e("s3",3,1),e("s1",1,1))
        assertEquals(1,vodInitialSeason(source,"missing"))
        assertEquals(emptyList<VodEpisode>(),vodEpisodesInSeason(source,9))
    }

    @Test fun repeatedNumbersDoNotOverwriteDistinctProviderEpisodeIds() {
        val source = listOf(e("b",2,1),e("a",2,1))
        assertEquals(listOf("a","b"),vodEpisodesInSeason(source,2).map { it.id })
    }
}