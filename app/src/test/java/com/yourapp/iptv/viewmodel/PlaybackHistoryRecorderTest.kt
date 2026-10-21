package com.yourapp.iptv.viewmodel

import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.domain.repository.WatchHistoryRepository
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.*
import org.junit.Test

class PlaybackHistoryRecorderTest {
    private val a = Channel("a", "A", streamUrl = "https://unit.test/a")
    private val b = a.copy(id = "b", streamUrl = "https://unit.test/b")
    private val source = AppSettings(m3uUrl = "https://unit.test/list")
    private val repository = Repository()

    @Test fun selectionAndFocusDoNotRecordAnythingUntilAConfirmedFrameArrives() {
        var current = a
        val recorder = PlaybackHistoryRecorder(repository) { current }
        recorder.bind(source)
        current = b
        assertTrue(repository.events.isEmpty())
        recorder.onFirstFrame(b)
        assertEquals(PlaylistCacheKey.of(source), repository.events.single().first)
        assertEquals("b", repository.events.single().second.channelId)
    }
    @Test fun oldChannelAndOldUrlCallbacksCannotRecordAgainstTheCurrentSelection() {
        val recorder = PlaybackHistoryRecorder(repository) { b }
        recorder.bind(source)
        recorder.onFirstFrame(a)
        recorder.onFirstFrame(b.copy(streamUrl = "https://unit.test/old"))
        assertTrue(repository.events.isEmpty())
    }
    @Test fun missingSourceCannotCreateHistoryAndRepositoryFailuresDoNotEscapeToThePlayer() {
        val recorder = PlaybackHistoryRecorder(repository) { a }
        recorder.onFirstFrame(a); recorder.bind(AppSettings()); recorder.onFirstFrame(a)
        assertTrue(repository.events.isEmpty())
        repository.fail = true; recorder.bind(source); recorder.onFirstFrame(a)
        assertTrue(repository.events.isEmpty())
    }
    private class Repository : WatchHistoryRepository {
        val events = mutableListOf<Pair<String, WatchHistoryEntry>>()
        var fail = false
        override fun observe(sourceKey: String) = flowOf(emptyList<WatchHistoryEntry>())
        override fun recordStarted(sourceKey: String, entry: WatchHistoryEntry) {
            if (fail) error("Unavailable optional history")
            events += sourceKey to entry
        }
        override suspend fun remove(sourceKey: String, channelId: String) = Unit
        override suspend fun clear(sourceKey: String) = Unit
    }
}