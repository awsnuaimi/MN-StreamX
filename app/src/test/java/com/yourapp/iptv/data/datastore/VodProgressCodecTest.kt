package com.yourapp.iptv.data.datastore

import com.yourapp.iptv.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class VodProgressCodecTest {
    private fun item(kind: VodKind = VodKind.MOVIE, id: String = "42") =
        VodProgressEntry(kind, id, "Árabe / اسم", "mp4", 20_000, 80_000, 1234)

    @Test fun roundTripDoesNotSerializeCredentialsOrUrls() {
        val rows = listOf(item(), item(VodKind.SERIES))
        val json = VodProgressCodec.encode(rows)
        assertEquals(rows, VodProgressCodec.decode(json))
        assertFalse(json.contains("xtreamPassword"))
        assertFalse(json.contains("streamUrl"))
    }

    @Test fun rejectsCorruptionDuplicateIdsAndOversizedInput() {
        assertTrue(VodProgressCodec.decode("bad-json").isEmpty())
        assertTrue(VodProgressCodec.decode("x".repeat(42_000)).isEmpty())
        assertTrue(VodProgressCodec.decode("{\"version\":2,\"entries\":[]}").isEmpty())
        val dup = "{\"version\":1,\"entries\":[" +
            VodProgressCodec.encode(listOf(item())).substringAfter("[" ).substringBeforeLast("]") + "," +
            VodProgressCodec.encode(listOf(item())).substringAfter("[").substringBeforeLast("]") + "]}"
        assertTrue(VodProgressCodec.decode(dup).isEmpty())
    }

    @Test fun seriesContextRoundTripsWhileExistingRowsStillDecodeWithoutIt() {
        val episode = item(VodKind.SERIES).copy(seriesId = "55")
        assertEquals(listOf(episode),VodProgressCodec.decode(VodProgressCodec.encode(listOf(episode))))
        val legacy = VodProgressCodec.encode(listOf(item(VodKind.SERIES)))
        assertFalse(legacy.contains("series"))
        assertNull(VodProgressCodec.decode(legacy).single().seriesId)
    }
    @Test fun malformedOrMovieParentIdsCannotBeRecorded() {
        assertFalse(VodProgress.isValid(item().copy(seriesId = "55")))
        assertFalse(VodProgress.isValid(item(VodKind.SERIES).copy(seriesId = "../55")))
        val json = VodProgressCodec.encode(listOf(item(VodKind.SERIES))).replace("\"at\":1234","\"at\":1234,\"series\":55")
        assertTrue(VodProgressCodec.decode(json).isEmpty())
    }
}