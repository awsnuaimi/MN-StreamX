package com.yourapp.iptv.data.datastore

import com.yourapp.iptv.domain.model.WatchHistoryEntry
import org.junit.Assert.*
import org.junit.Test

class WatchHistoryCodecTest {
    @Test fun roundTripKeepsOrderAndContainsOnlyIdsAndTimes() {
        val rows = listOf(WatchHistoryEntry("عربي-1", 20), WatchHistoryEntry("polish-2", 10))
        val text = WatchHistoryCodec.encode(rows, 100)
        assertEquals(rows, WatchHistoryCodec.decode(text))
        assertEquals(100L, WatchHistoryCodec.updated(text))
        assertFalse(text.contains("streamUrl")); assertFalse(text.contains("password"))
    }
    @Test fun invalidFutureAndOversizedRecordsAreIgnored() {
        val text = WatchHistoryCodec.encode(listOf(WatchHistoryEntry("a", 1)), 1)
        assertTrue(WatchHistoryCodec.decode(null).isEmpty())
        assertTrue(WatchHistoryCodec.decode("broken").isEmpty())
        assertTrue(WatchHistoryCodec.decode(text.replace("\"version\":1", "\"version\":2")).isEmpty())
        assertTrue(WatchHistoryCodec.decode("x".repeat(65 * 1024)).isEmpty())
        assertTrue(WatchHistoryCodec.decode(text.replace("\"at\":1", "\"at\":-1")).isEmpty())
    }
    @Test fun invalidDuplicateIdsCannotBecomeAValidSavedList() {
        val rows = listOf(WatchHistoryEntry("a", 2), WatchHistoryEntry("a", 1))
        assertTrue(runCatching { WatchHistoryCodec.encode(rows, 2) }.isFailure)
        assertTrue(WatchHistoryCodec.decode("{\"version\":1,\"rows\":[{\"id\":\"a\",\"at\":2},{\"id\":\"a\",\"at\":1}]}").isEmpty())
    }
}