package mnstudio.apps.mnstreamx.ui.screen.vod

import mnstudio.apps.mnstreamx.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class VodEpisodeWatchedTest {
    private val episodes = listOf(VodEpisode("21","One",1,1,"mp4"),VodEpisode("22","Two",1,2,"mp4"))
    @Test fun completedMarkersDoNotLeakAcrossSeriesOrUnknownEpisodeIds() {
        val entries = listOf(VodCompletedEpisode("21","800",20),
            VodCompletedEpisode("22","999",50),VodCompletedEpisode("23","800",50))
        assertEquals(setOf("21"),vodEpisodeWatched(episodes,"800",entries,emptyList()))
        assertTrue(vodEpisodeWatched(episodes,"999",listOf(entries.first()),emptyList()).isEmpty())
    }
    @Test fun aNewerResumeOverridesAnOlderCompletion() {
        val done = listOf(VodCompletedEpisode("21","800",20))
        val recent = listOf(VodProgressEntry(VodKind.SERIES,"21","One","mp4",40_000,120_000,30,"800"))
        assertTrue(vodEpisodeWatched(episodes,"800",done,recent).isEmpty())
        assertEquals(setOf("21"),vodEpisodeWatched(episodes,"800",done,recent.map { it.copy(updatedAtMs = 10) }))
    }
}