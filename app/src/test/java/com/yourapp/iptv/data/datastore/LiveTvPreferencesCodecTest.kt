package com.yourapp.iptv.data.datastore

import com.yourapp.iptv.domain.model.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import kotlin.random.Random

class LiveTvPreferencesCodecTest {
    @Test fun unicodeProviderGroupsAndOpaqueIdsRoundTripWithoutNormalization() {
        val original = LiveTvPreferences(LiveTvSort.FAVORITES, setOf("provider:42/HD"), setOf("AR| أخبار", "PL| OGÓLNE"),
            setOf("arabic-news"), mapOf("f".repeat(64) to "preferred-id"))
        assertEquals(original, LiveTvPreferencesCodec.decode(LiveTvPreferencesCodec.encode(original)))
        assertEquals(LiveTvPreferences(), LiveTvPreferencesCodec.decode(null))
    }
    @Test fun malformedTypesDuplicateIdsAndExcessiveDepthAreRejected() {
        val valid = LiveTvPreferencesCodec.encode(LiveTvPreferences())
        for (bad in listOf(valid.replace("\"channels\":[]", "\"channels\":[1]"),
            valid.replace("\"channels\":[]", "\"channels\":[\"a\",\"a\"]"),
            valid.replace("PROVIDER", "FUTURE"), "[".repeat(50) + "0" + "]".repeat(50), " ".repeat(LiveTvPreferencesCodec.LIMIT + 1))) {
            assertThrows(IOException::class.java) { LiveTvPreferencesCodec.decode(bad) }
        }
    }
    @Test fun boundedRandomDataRoundTripsAndMutatedRecordsNeverEscapeAsUncheckedParserErrors() {
        val random = Random(35)
        repeat(100) {
            val ids = (0 until random.nextInt(1, 40)).map { "id:${random.nextLong()}" }.toSet()
            val value = LiveTvPreferences(LiveTvSort.entries[random.nextInt(3)], hiddenChannels = ids)
            val text = LiveTvPreferencesCodec.encode(value)
            assertEquals(value, LiveTvPreferencesCodec.decode(text))
            val cut = text.take(random.nextInt(text.length))
            try { LiveTvPreferencesCodec.decode(cut); fail("Truncated JSON must fail") } catch (_: IOException) { }
        }
    }
    @Test fun limitsRejectOversizedSetsAndEncodingRatherThanTruncatingUserSelections() {
        assertThrows(IllegalArgumentException::class.java) { LiveTvPreferences(hiddenChannels = (0..10_000).map(Int::toString).toSet()).validate() }
        assertThrows(IllegalArgumentException::class.java) { LiveTvPreferences(hiddenGroups = setOf("g".repeat(257))).validate() }
        val large = LiveTvPreferences(hiddenChannels = (0 until 3_000).map { "$it" + "a".repeat(500) }.toSet())
        assertThrows(IllegalArgumentException::class.java) { LiveTvPreferencesCodec.encode(large) }
    }
}