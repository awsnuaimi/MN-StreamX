package com.yourapp.iptv.domain.organization

import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class ChannelLanguageDetectorTest {
    @Test fun requestedExamplesDetectTheThreeSupportedRoots() {
        assertEquals("polish", ChannelLanguageDetector.detect(listOf("PL TVP 1", "TVN7", "Polsat Sport", "TVP Kultura")))
        assertEquals("german", ChannelLanguageDetector.detect(listOf("RTL", "ZDF", "ProSieben")))
        assertEquals("arabic", ChannelLanguageDetector.detect(listOf("MBC 2", "Rotana Cinema")))
        assertNull(ChannelLanguageDetector.detect(listOf("Unknown 1", "Unknown 2")))
        assertNull(ChannelLanguageDetector.detect(emptyList()))
    }

    @Test fun unknownAndBlankNamesStayInTheSixtyPercentDenominator() {
        assertEquals("polish", ChannelLanguageDetector.detect(listOf("TVP 1", "TVN7", "Polsat", "Unknown", "")))
        assertNull(ChannelLanguageDetector.detect(listOf("TVP 1", "TVN7", "Unknown", "", "Other")))
        assertNull(ChannelLanguageDetector.detect(listOf("TVP Sport", "Canal+ Sport")))
        assertEquals("polish", ChannelLanguageDetector.detect(listOf("TVP Sport", "Polsat Sport", "Canal+ Sport")))
    }

    @Test fun tiesAndConflictingSignalsDoNotPickWhicheverRootAppearsFirst() {
        val names = listOf("TVP 1", "ZDF")
        assertNull(ChannelLanguageDetector.detect(names))
        assertNull(ChannelLanguageDetector.detect(names.reversed()))
        assertNull(ChannelLanguageDetector.detect(listOf("TVP ZDF", "MBC Polsat")))
    }

    @Test fun diacriticsScriptsAndCompactBrandNamesAreRecognized() {
        assertEquals("polish", ChannelLanguageDetector.detect(listOf("Łódź", "Wiadomości", "nc+ Sport")))
        assertEquals("german", ChannelLanguageDetector.detect(listOf("München", "SAT.1", "RTL2")))
        assertEquals("arabic", ChannelLanguageDetector.detect(listOf("الجزيرة", "MBC2", "Al Jazeera")))
        assertNull(ChannelLanguageDetector.detect(listOf("TVPlanet", "RTLish", "MBCity", "Canal+")))
    }

    @Test fun explicitChannelPrefixIsStrongerThanAContradictingBrand() {
        assertEquals("polish", ChannelLanguageDetector.detect(listOf("PL Sky News", "PL ZDF")))
        assertEquals("german", ChannelLanguageDetector.detect(listOf("DE Polsat", "DE TVP 1")))
    }

    @Test fun networkAndExplicitGroupRootAreResolvedBeforeCallingTheDetector() {
        var calls = 0
        val classifier = AutomaticClassifier { calls++; "polish" }
        assertEquals("bein", classifier.classify("AR SPORTS", "beIN Sports 1").root)
        assertEquals("german", classifier.classify("DE Sport", "TVP 1").root)
        assertEquals(0, calls)
        assertEquals("polish", classifier.classify("Unknown", "TVP 1").root)
        assertEquals(1, calls)
        assertEquals("arabic", classifier.classify("AR BEIN SPORTS", "beIN Movies 1").root)
        assertEquals(1, calls)
    }

    @Test fun deviceLocaleDoesNotChangeVotes() {
        val old = Locale.getDefault()
        try {
            for (tag in listOf("tr-TR", "ar-EG", "de-DE")) {
                Locale.setDefault(Locale.forLanguageTag(tag))
                assertEquals("german", ChannelLanguageDetector.detect(listOf("RTL", "PROSIEBEN", "SAT.1")))
                assertEquals("polish", ChannelLanguageDetector.detect(listOf("WIADOMOŚCI", "TVN7", "TVP 1")))
            }
        } finally { Locale.setDefault(old) }
    }
}