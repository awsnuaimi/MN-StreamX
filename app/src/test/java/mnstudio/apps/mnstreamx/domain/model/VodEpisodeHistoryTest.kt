package mnstudio.apps.mnstreamx.domain.model

import org.junit.Assert.*
import org.junit.Test

class VodEpisodeHistoryTest {
    private fun episode(id: String = "21", at: Long = 10, position: Long = 110_000) =
        VodProgressEntry(VodKind.SERIES,id,"Episode","mp4",position,120_000,at,"800")

    @Test fun completionIsSeparateFromResumeAndRestartClearsItsMarker() {
        val started = VodEpisodeHistory.record(VodProgressSnapshot(),episode(position = 20_000),false)
        assertEquals(1,started.entries.size);assertTrue(started.completed.isEmpty())
        val finished = VodEpisodeHistory.record(started,episode(at = 20),true)
        assertTrue(finished.entries.isEmpty());assertEquals("21",finished.completed.single().id)
        val restarted = VodEpisodeHistory.record(finished,episode(at = 30,position = 0),false)
        assertTrue(restarted.entries.isEmpty());assertTrue(restarted.completed.isEmpty())
        val continuing = VodEpisodeHistory.record(restarted,episode(at = 40,position = 40_000),false)
        assertEquals(1,continuing.entries.size);assertTrue(continuing.completed.isEmpty())
    }

    @Test fun unknownParentsAndMoviesNeverBecomeCompletedEpisodes() {
        assertTrue(VodEpisodeHistory.record(VodProgressSnapshot(),episode().copy(seriesId = null),true).completed.isEmpty())
        assertTrue(VodEpisodeHistory.record(VodProgressSnapshot(),episode().copy(kind = VodKind.MOVIE,seriesId = null),true).completed.isEmpty())
    }

    @Test fun anOlderQueuedWriteCannotUndoNewerCompletion() {
        val done = VodEpisodeHistory.record(VodProgressSnapshot(),episode(at = 500),true)
        val old = VodEpisodeHistory.record(done,episode(at = 200,position = 50_000),false)
        assertEquals(done,old)
    }

    @Test fun mergingRestoresTheMostRecentEventAndBoundsStorage() {
        val done = VodEpisodeHistory.record(VodProgressSnapshot(),episode(at = 15),true)
        val playing = VodProgressSnapshot(listOf(episode(at = 20,position = 50_000)))
        assertTrue(VodEpisodeHistory.merge(done,playing).completed.isEmpty())
        assertEquals(1,VodEpisodeHistory.merge(done,playing).entries.size)
        val newerDone = VodEpisodeHistory.record(VodProgressSnapshot(),episode(at = 30),true)
        assertTrue(VodEpisodeHistory.merge(playing,newerDone).entries.isEmpty())
        assertEquals(1,VodEpisodeHistory.merge(playing,newerDone).completed.size)
        val all = (1..300).fold(VodProgressSnapshot()) { state, index ->
            VodEpisodeHistory.record(state,episode(id = index.toString(),at = index.toLong()),true)
        }
        assertEquals(VodEpisodeHistory.LIMIT,all.completed.size)
        assertEquals("300",all.completed.first().id)
    }
}