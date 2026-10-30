package com.yourapp.iptv.data.datastore

import com.yourapp.iptv.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class HomeSessionCodecTest {
    @Test fun roundTripKeepsUnicodeGroupsAndOnlySmallNavigationFields() {
        val value = HomeBrowseSession("PL | Kanały · أخبار", "polish", true, "id-40", HomeGridPosition("id-37", 14, 3))
        val encoded = HomeSessionCodec.encode(value, 123)
        assertEquals(value, HomeSessionCodec.decode(encoded))
        assertEquals(123L, HomeSessionCodec.updated(encoded))
        assertFalse(encoded.contains("streamUrl")); assertFalse(encoded.contains("password"))
        assertTrue(encoded.length < 1024)
    }

    @Test fun invalidAndFutureRecordsAreIgnoredWithoutCrashing() {
        val text = HomeSessionCodec.encode(HomeBrowseSession(), 1)
        assertNull(HomeSessionCodec.decode("broken"))
        assertNull(HomeSessionCodec.decode(text.replace("\"version\":2", "\"version\":99")))
        assertNull(HomeSessionCodec.decode(text.replace("\"offset\":0", "\"offset\":-1")))
        assertNull(HomeSessionCodec.decode(text.replace("\"favorites\":false", "\"favorites\":\"no\"")))
        assertNull(HomeSessionCodec.decode("x".repeat(16 * 1024 + 1)))
    }

    @Test fun normalizesOffsetsAndNarrowLayoutsWithoutChangingIdentifiers() {
        val input = HomeBrowseSession(focusedChannelId = "stable", position = HomeGridPosition("stable", -20, 1))
        assertEquals(input.copy(position = input.position.copy(offsetPx = 0)),
            HomeSessionCodec.decode(HomeSessionCodec.encode(input, 1)))
    }

    @Test fun versionOneRecordsRemainReadableAfterTheUpgrade() {
        val original = HomeBrowseSession("News", focusedChannelId = "old")
        val legacy = HomeSessionCodec.encode(original, 10).replace("\"version\":2", "\"version\":1")
        assertEquals(original, HomeSessionCodec.decode(legacy))
    }

    @Test fun bookmarksRoundTripAndOversizedHistoryStaysWithinTheExistingStorageBudget() {
        val values = (0 until 32).map { HomeBrowseBookmark("Group $it", focusedChannelId = "c$it", position = HomeGridPosition("c$it", it, 1)) }
        val original = HomeBrowseSession("Group 31", focusedChannelId = "c31", bookmarks = values)
        assertEquals(original, HomeSessionCodec.decode(HomeSessionCodec.encode(original, 10)))
        val large = original.copy(bookmarks = values.map { it.copy(group = "x".repeat(4096) + it.group) }
            .map { it.copy(group = it.group?.take(4096)) })
        val text = HomeSessionCodec.encode(large, 11)
        assertTrue(text.length <= 16 * 1024)
        assertTrue(HomeSessionCodec.decode(text)!!.bookmarks.size < 32)
        assertEquals("c31", HomeSessionCodec.decode(text)!!.focusedChannelId)
    }
}