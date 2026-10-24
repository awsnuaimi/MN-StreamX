package com.yourapp.iptv.ui.screen.home

import org.junit.Assert.assertEquals
import org.junit.Test

class ChannelMonogramTest {
    @Test fun siblingChannelsRetainDistinguishingNumbers() {
        assertEquals("MBC2", ChannelMonogram.of("MBC 2 HD"))
        assertEquals("MBC4", ChannelMonogram.of("MBC 4 HD"))
        assertEquals("BEIN1", ChannelMonogram.of("beIN Sports 1"))
        assertEquals("FR24", ChannelMonogram.of("France 24"))
        assertEquals("TVP1", ChannelMonogram.of("TVP 1"))
        assertEquals("RTL2", ChannelMonogram.of("RTL 2"))
    }

    @Test fun arabicPrefixIsRemovedOnlyWhenAppropriate() {
        assertEquals("جز", ChannelMonogram.of("قناة الجزيرة"))
        assertEquals("عر", ChannelMonogram.of("قناة العربية"))
        assertEquals("MBC", ChannelMonogram.of("MBC Action"))
    }

    @Test fun genericResolutionWordsDoNotBecomeTheBrand() {
        assertEquals("ZDF", ChannelMonogram.of("ZDF HD"))
        assertEquals("?", ChannelMonogram.of("HD"))
        assertEquals("?", ChannelMonogram.of("   "))
    }

    @Test fun polishAndGermanBrandNamesAreStable() {
        assertEquals("POLS", ChannelMonogram.of("Polsat HD"))
        assertEquals("DAS", ChannelMonogram.of("Das Erste"))
        assertEquals("TVN24", ChannelMonogram.of("TVN 24"))
    }
}