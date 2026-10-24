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
}