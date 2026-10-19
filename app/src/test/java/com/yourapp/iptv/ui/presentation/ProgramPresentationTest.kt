package com.yourapp.iptv.ui.presentation

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

class ProgramPresentationTest {
    private fun instant(value: String) = Instant.parse(value).toEpochMilli()

    @Test fun programTimesRemainInStartThenEndOrder() {
        assertEquals("19:55 – 20:05", ProgramPresentation.timeRange(instant("2026-10-19T17:55:00Z"),
            instant("2026-10-19T18:05:00Z"), ZoneId.of("Europe/Berlin")))
    }

    @Test fun midnightAndDaylightSavingDoNotSortOrChangeSourceTimes() {
        assertEquals("23:50 – 00:10", ProgramPresentation.timeRange(instant("2026-10-19T23:50:00Z"),
            instant("2026-10-20T00:10:00Z"), ZoneId.of("UTC")))
        assertEquals("02:45 – 02:15", ProgramPresentation.timeRange(instant("2026-10-25T00:45:00Z"),
            instant("2026-10-25T01:15:00Z"), ZoneId.of("Europe/Berlin")))
    }

    @Test fun timeLabelsAreStableWhenSystemLocaleChanges() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("ar"))
            assertEquals("19:55 – 20:05", ProgramPresentation.timeRange(instant("2026-10-19T19:55:00Z"),
                instant("2026-10-19T20:05:00Z"), ZoneId.of("UTC")))
        } finally { Locale.setDefault(previous) }
    }

    @Test fun countsAreLabelledAndNumbersAreIsolatedFromArabicDirection() {
        val label = ProgramPresentation.channelCount(73, 7492)
        assertEquals("المعروض: \u206673\u2069 · الإجمالي: \u20667492\u2069", label)
        assertFalse(label.contains('/'))
    }

    @Test fun detachedFavoritePlaybackHasAnExplicitLabel() {
        assertEquals("القناة \u20665\u2069 من \u206673\u2069", ProgramPresentation.playerPosition(5, 73))
        assertTrue(ProgramPresentation.playerPosition(0, 0).startsWith("خارج القائمة"))
        assertFalse(ProgramPresentation.channelCount(-1, -1).contains('-'))
    }
}