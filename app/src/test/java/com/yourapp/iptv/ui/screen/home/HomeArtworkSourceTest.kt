package com.yourapp.iptv.ui.screen.home

import com.yourapp.iptv.domain.model.Channel
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeArtworkSourceTest {
    private fun channel(name: String, logo: String? = null) = Channel(
        id = "id-1", name = name, streamUrl = "https://example.com/live.m3u8", logoUrl = logo
    )

    @Test fun remoteLogoIsPresentedAsLogoNotPhoto() {
        assertEquals(HomeArtworkSource.Logo("https://example.org/logo.png"),
            HomeArtworkSource.of(channel("MBC 2 HD", " https://example.org/logo.png ")))
    }

    @Test fun invalidOrMissingLogoUsesMonogram() {
        val expected = HomeArtworkSource.Monogram("MBC2")
        assertEquals(expected, HomeArtworkSource.of(channel("MBC 2 HD", "file:///secret")))
        assertEquals(expected, HomeArtworkSource.of(channel("MBC 2 HD")))
    }

    @Test fun networkFailureHasAReadableFallback() {
        assertEquals(HomeArtworkSource.Monogram("BEIN1"),
            HomeArtworkSource.fallback("beIN Sports 1"))
    }

    @Test fun emptyNameAndMissingLogoAreAllowed() {
        assertEquals(HomeArtworkSource.Empty, HomeArtworkSource.of(channel(" ")))
        assertEquals(HomeArtworkSource.Empty, HomeArtworkSource.fallback(""))
    }
}