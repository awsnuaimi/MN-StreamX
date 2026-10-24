package com.yourapp.iptv.ui.component.player

import com.yourapp.iptv.domain.model.Channel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelFramePolicyTest {
    private fun channel(id: String, url: String) =
        Channel(id = id, name = "Test", streamUrl = url)

    @Test fun sameChannelKeepsItsCacheName() {
        val a = channel("42", "https://stream.example/42")
        assertEquals(ChannelFramePolicy.filename(a), ChannelFramePolicy.filename(a))
        assertTrue(ChannelFramePolicy.filename(a).matches(Regex("frame-[0-9a-f]{64}\\.jpg")))
    }

    @Test fun distinctProvidersWithSameChannelIdDoNotShareImages() {
        val a = channel("42", "https://one.example/42")
        val b = channel("42", "https://two.example/42")
        assertNotEquals(ChannelFramePolicy.filename(a), ChannelFramePolicy.filename(b))
    }

    @Test fun freshnessExpiresAndRejectsFutureTimestamps() {
        val now = 100_000L
        assertTrue(ChannelFramePolicy.isFresh(now - 1000L, now, 2000L))
        assertFalse(ChannelFramePolicy.isFresh(now - 2000L, now, 2000L))
        assertFalse(ChannelFramePolicy.isFresh(now + 1L, now, 2000L))
        assertFalse(ChannelFramePolicy.isFresh(0L, now, 2000L))
    }
}