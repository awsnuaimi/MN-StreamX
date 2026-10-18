package com.yourapp.iptv.data.source.m3u

import com.yourapp.iptv.domain.model.PlaylistLoadException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.concurrent.CancellationException
import java.io.Reader
import org.junit.Assert.assertTrue

class M3uParserTest {
    @Test fun metadataAndInterveningDirectivesArePreserved() {
        val content = """
            #EXTM3U
            #EXTINF:-1 tvg-name="Fallback" tvg-logo="logos/a.png" group-title="News, Local",Channel, One
            #EXTVLCOPT:http-user-agent=SyntheticAgent

            streams/a.m3u8
        """.trimIndent()
        val channel = M3uParser.parse(content.reader(), "https://unit.test/list/index.m3u").single()
        assertEquals("Channel, One", channel.name)
        assertEquals("News, Local", channel.group)
        assertEquals("https://unit.test/list/logos/a.png", channel.logo)
        assertEquals("https://unit.test/list/streams/a.m3u8", channel.url)
    }

    @Test fun identitiesRemainStableAfterReordering() {
        val a = "#EXTINF:-1,A\nhttps://unit.test/a.ts"
        val b = "#EXTINF:-1,B\nhttps://unit.test/b.ts"
        val first = M3uParser.parse("#EXTM3U\n$a\n$b").associate { it.name to it.id }
        val second = M3uParser.parse("#EXTM3U\n$b\n$a").associate { it.name to it.id }
        assertEquals(first, second)
        assertNotEquals(first["A"], first["B"])
    }

    @Test fun bomAndExplicitGroupAreHandled() {
        val channel = M3uParser.parse("\uFEFF#EXTM3U\n#EXTINF:-1,Test\n#EXTGRP:Sports\nhttps://unit.test/a.ts").single()
        assertEquals("Sports", channel.group)
    }

    @Test fun duplicateStreamDoesNotCreateDuplicateGridKeys() {
        val channels = M3uParser.parse("#EXTM3U\n#EXTINF:-1,A\nhttps://unit.test/a.ts\n#EXTINF:-1,A2\nhttps://unit.test/a.ts")
        assertEquals(1, channels.size)
    }

    @Test fun invalidAndUnsupportedUrlsAreSkipped() {
        val channels = M3uParser.parse("#EXTM3U\n#EXTINF:-1,Invalid\nfile:///etc/hosts\n#EXTINF:-1,Valid\nhttps://unit.test/a.ts")
        assertEquals("Valid", channels.single().name)
    }

    @Test fun htmlAndEmptyResponsesFailExplicitly() {
        assertThrows(PlaylistLoadException::class.java) { M3uParser.parse("<html>Access denied</html>") }
        assertThrows(PlaylistLoadException::class.java) { M3uParser.parse("#EXTM3U\n") }
    }

    @Test fun hlsSegmentPlaylistIsNotImportedAsChannels() {
        assertThrows(PlaylistLoadException::class.java) {
            M3uParser.parse("#EXTM3U\n#EXT-X-TARGETDURATION:8\n#EXTINF:8,Segment\nhttps://unit.test/segment.ts")
        }
    }

    @Test fun aSingleOversizedLineIsRejectedBeforeReadingUnboundedInput() {
        var consumed = 0
        val source = object : Reader() {
            override fun read(buffer: CharArray, offset: Int, length: Int): Int {
                buffer.fill('x', offset, offset + length)
                consumed += length
                return length
            }
            override fun close() = Unit
        }
        assertThrows(PlaylistLoadException::class.java) { M3uParser.parse(source) }
        assertTrue(consumed <= 20 * 1024 * 1024 + 1)
    }

    @Test fun parsingCanBeCancelled() {
        assertThrows(CancellationException::class.java) {
            M3uParser.parse("#EXTM3U".reader(), isActive = { false })
        }
    }
}