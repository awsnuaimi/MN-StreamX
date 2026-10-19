package com.yourapp.iptv.data.source.xtream

import com.yourapp.iptv.domain.model.PlaylistLoadException
import org.junit.Assert.*
import org.junit.Test
import java.io.StringReader
import java.util.Base64
import java.util.TimeZone
import java.util.concurrent.CancellationException

class XtreamEpgParserTest {
    @Test fun decodesUnicodeTitlesAndDescriptionsAndAcceptsNumericAndStringEpochs() {
        val title = encode("برنامج عربي – Übertragung")
        val description = encode("تفاصيل البرنامج")
        val result = parse("""{"epg_listings":[{"title":"$title","description":"$description",
            "start_timestamp":"1700000000","stop_timestamp":1700003600}]}""").single()
        assertEquals("برنامج عربي – Übertragung", result.title)
        assertEquals("تفاصيل البرنامج", result.description)
        assertEquals(1700000000000L, result.startTime)
        assertEquals(1700003600000L, result.endTime)
    }

    @Test fun acceptsPlainTextMillisecondsAndExplicitOffsetsWithoutGuessingServerLocalTime() {
        val result = parse("""{"epg_listings":[
            {"title":"Live News!","start_timestamp":1700000000000,"stop_timestamp":"1700003600000"},
            {"title":"Next!","start":"2023-11-15T00:13:20+01:00","end":"2023-11-15T01:13:20+01:00"},
            {"title":"Ambiguous!","start":"2023-11-14 22:13:20","end":"2023-11-14 23:13:20"}]}""")
        assertEquals(2, result.size)
        assertEquals("Live News!", result.first().title)
        assertEquals(listOf(1700000000000L, 1700003600000L), result.map { it.startTime })
    }

    @Test fun timestampsDoNotChangeWithTheDeviceTimeZone() {
        val previous = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Europe/Berlin"))
            val berlin = parse(valid()).single()
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
            assertEquals(berlin, parse(valid()).single())
        } finally { TimeZone.setDefault(previous) }
    }

    @Test fun skipsMalformedRowsAndDeduplicatesSortedIntervals() {
        val result = parse("""{"epg_listings":[null,{"title":"Missing time!"},
            {"title":"Invalid!","start_timestamp":1700000000,"stop_timestamp":1699999999},
            {"title":"Later!","start_timestamp":1700003600,"stop_timestamp":1700007200},
            {"title":"First!","start_timestamp":1700000000,"stop_timestamp":1700003600},
            {"title":"Duplicate!","start_timestamp":1700000000,"stop_timestamp":1700003600}]}""")
        assertEquals(listOf("First!", "Later!"), result.map { it.title })
    }

    @Test fun emptyAndNullListingsAreValidButAuthOrHtmlErrorsAreNotEmptySuccesses() {
        for (body in listOf("[]", """{"epg_listings":[]}""", """{"epg_listings":null}""")) {
            assertTrue(parse(body).isEmpty())
        }
        for (body in listOf("{}", """{"user_info":{"auth":0}}""", "<html>error</html>")) {
            assertTrue(runCatching { parse(body) }.isFailure)
        }
    }

    @Test fun malformedTrailingDataAndExcessiveRowsAreRejected() {
        assertTrue(runCatching { parse(valid() + "{}") }.isFailure)
        val row = """{"title":"Test!","start_timestamp":1700000000,"stop_timestamp":1700003600}"""
        assertTrue(runCatching { parse("{\"epg_listings\":[${List(33) { row }.joinToString()}]}") }
            .exceptionOrNull() is PlaylistLoadException)
    }

    @Test fun cancellationIsNotConvertedToAnEmptySchedule() {
        val error = runCatching { XtreamEpgParser.parse(StringReader(valid())) { false } }.exceptionOrNull()
        assertTrue(error is CancellationException)
    }

    private fun valid() = """{"epg_listings":[{"title":"News!","start_timestamp":1700000000,"stop_timestamp":1700003600}]}"""
    private fun parse(value: String) = XtreamEpgParser.parse(StringReader(value))
    private fun encode(value: String) = Base64.getEncoder().encodeToString(value.toByteArray(Charsets.UTF_8))
}