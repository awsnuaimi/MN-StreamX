package com.yourapp.iptv.domain.model

import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class PlaybackTechnicalInfoTest {
    @Test fun fullHdFrameRateAndStereoComeFromKnownValues() {
        assertEquals(listOf("FULL HD", "25 FPS", "STEREO"), PlaybackTechnicalInfo(1920, 1080, 25f, 2).badges())
    }

    @Test fun unknownOrInvalidValuesDoNotCreateMadeUpBadges() {
        assertTrue(PlaybackTechnicalInfo().badges().isEmpty())
        assertTrue(PlaybackTechnicalInfo(-1, -1, Float.NaN, -1).badges().isEmpty())
        assertTrue(PlaybackTechnicalInfo(20000, 20000, Float.POSITIVE_INFINITY, 100).badges().isEmpty())
        assertTrue(PlaybackTechnicalInfo(0, 1080, 0f, 0).badges().isEmpty())
    }

    @Test fun nonstandardResolutionIsShownAsDimensions() {
        assertEquals(listOf("1920×800"), PlaybackTechnicalInfo(1920, 800).badges())
    }

    @Test fun standardResolutionsIncludePortraitAndUhd() {
        assertEquals(listOf("HD"), PlaybackTechnicalInfo(1280, 720).badges())
        assertEquals(listOf("FULL HD"), PlaybackTechnicalInfo(1080, 1920).badges())
        assertEquals(listOf("4K"), PlaybackTechnicalInfo(3840, 2160).badges())
    }

    @Test fun audioOnlyMetadataDoesNotImplyVideoOrStereo() {
        assertEquals(listOf("MONO"), PlaybackTechnicalInfo(audioChannels = 1).badges())
        assertEquals(listOf("5.1"), PlaybackTechnicalInfo(audioChannels = 6).badges())
        assertEquals(listOf("7.1"), PlaybackTechnicalInfo(audioChannels = 8).badges())
        assertEquals(listOf("4 CH"), PlaybackTechnicalInfo(audioChannels = 4).badges())
    }

    @Test fun frameRateFormattingIsBoundedAndLocaleIndependent() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMAN)
            assertEquals(listOf("29.9 FPS"), PlaybackTechnicalInfo(frameRate = 29.94f).badges())
            assertTrue(PlaybackTechnicalInfo(frameRate = 1000f).badges().isEmpty())
            assertTrue(PlaybackTechnicalInfo(frameRate = 0.01f).badges().isEmpty())
        } finally { Locale.setDefault(previous) }
    }
}