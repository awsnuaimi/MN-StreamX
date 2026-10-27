package com.yourapp.iptv.data.datastore

import com.yourapp.iptv.domain.diagnostics.*
import com.yourapp.iptv.domain.model.*
import kotlinx.coroutines.test.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryQueueDiagnosticsTest {
    @Before fun setup() { DiagnosticJournal.clear() }
    @After fun cleanup() { DiagnosticJournal.clear() }
    @Test fun sourcePressureIsReportedWithoutChannelIdsCredentialsOrProfileIds() = runTest {
        val settings = HistoryPressureSettings();val repository = WatchHistoryDataStore(HistoryPressureStore(),settings,backgroundScope)
        repeat(HistoryRecordBatch.MAX_SOURCES + 1) { index ->
            repository.recordStarted(index.toString(16).padStart(64,'0'),WatchHistoryEntry("private-channel-name",index.toLong()))
        }
        val event = DiagnosticJournal.events.value.single()
        assertEquals(DiagnosticIssue.HISTORY_QUEUE,event.issue);assertEquals(DiagnosticArea.SYSTEM,event.area)
        assertFalse(event.toString().contains("private-channel-name"));assertFalse(event.toString().contains("https://"))
    }
    @Test fun backgroundWriteFailureIsVisibleAndCarriesOnlyTheRawSourceHash() = runTest {
        val settings = HistoryPressureSettings();val key = PlaylistCacheKey.of(settings.value.value)
        val repository = WatchHistoryDataStore(HistoryPressureStore().apply { fail = true },settings,backgroundScope)
        repository.recordStarted(key,WatchHistoryEntry("private-channel-name",1));runCurrent()
        val event = DiagnosticJournal.events.value.single()
        assertEquals(DiagnosticIssue.HISTORY_WRITE,event.issue);assertEquals(key,event.sourceKey)
        assertFalse(event.toString().contains("private-channel-name"))
    }
}