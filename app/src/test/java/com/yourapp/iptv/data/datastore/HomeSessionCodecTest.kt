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
        assertNull(HomeSessionCodec.decode(text.replace("\"version\":1", "\"version\":99")))
        assertNull(HomeSessionCodec.decode(text.replace("\"offset\":0", "\"offset\":-1")))
        assertNull(HomeSessionCodec.decode(text.replace("\"favorites\":false", "\"favorites\":\"no\"")))
        assertNull(HomeSessionCodec.decode("x".repeat(16 * 1024 + 1)))
    }

    @Test fun normalizesOffsetsAndNarrowLayoutsWithoutChangingIdentifiers() {
        val input = HomeBrowseSession(focusedChannelId = "stable", position = HomeGridPosition("stable", -20, 1))
        assertEquals(input.copy(position = input.position.copy(offsetPx = 0)),
            HomeSessionCodec.decode(HomeSessionCodec.encode(input, 1)))
    }
}