package com.yourapp.iptv.ui.screen.home

import org.junit.Assert.*
import org.junit.Test

class HomeImageSourceTest {
    @Test fun validSourceLogosAreUsedAsCardArtwork() {
        assertEquals("https://example.org/logo.png", HomeImageSource.remote(" https://example.org/logo.png "))
        assertEquals("http://example.org/logo.png", HomeImageSource.remote("http://example.org/logo.png"))
    }

    @Test fun emptyOrUnsafeSourcesUseGradientFallback() {
        listOf(null, "", "file:///secret", "data:image/png;base64,abc", "javascript:alert(1)",
            "https://example.org/not encoded.png").forEach {
            assertNull(HomeImageSource.remote(it))
        }
    }
}