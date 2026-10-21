package com.yourapp.iptv.data.datastore

import androidx.datastore.preferences.core.*
import com.yourapp.iptv.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class HomeAppearanceDataStoreTest {
    @get:Rule val temporary = TemporaryFolder()
    @Test fun directionsFollowInterfaceLanguageAndManualPositionWins() {
        assertTrue(HomeAppearance().sidebarOnRight)
        assertFalse(HomeAppearance(language = HomeLanguage.ENGLISH).sidebarOnRight)
        assertFalse(HomeAppearance(language = HomeLanguage.GERMAN).sidebarOnRight)
        assertTrue(HomeAppearance(language = HomeLanguage.GERMAN, sidebarSide = SidebarSide.RIGHT).sidebarOnRight)
        val arabicLeft = HomeAppearance(sidebarSide = SidebarSide.LEFT)
        assertFalse(arabicLeft.sidebarOnRight)
        assertTrue(arabicLeft.isRtl)
    }
    @Test fun malformedValuesFallbackWithoutAffectingOtherPreferences() {
        val values = mutablePreferencesOf(intPreferencesKey("language") to 2,
            stringPreferencesKey("sidebar_side") to "invalid", intPreferencesKey("columns") to 100,
            stringPreferencesKey("expanded_sidebar") to "yes")
        assertEquals(HomeAppearance(), HomeAppearanceCodec.decode(values))
        assertEquals(3, HomeAppearance(columns = -1).normalized().columns)
    }
    @Test fun roundTripKeepsUnrelatedKeysAndEverySideLanguageCombination() {
        val values = mutablePreferencesOf(stringPreferencesKey("future") to "preserve")
        for (language in HomeLanguage.entries) for (side in SidebarSide.entries) for (columns in 2..4) {
            val expected = HomeAppearance(language, side, true, columns)
            HomeAppearanceCodec.encode(values, expected)
            assertEquals(expected, HomeAppearanceCodec.decode(values))
        }
        assertEquals("preserve", values[stringPreferencesKey("future")])
    }
    @Test fun choicesSurviveReopeningAndCannotChangeSourceOrPlayerSettings() = runBlocking {
        val file = temporary.root.resolve("home.preferences_pb")
        val expected = HomeAppearance(HomeLanguage.GERMAN, SidebarSide.LEFT, true, 4)
        var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val source = temporary.root.resolve("settings.preferences_pb").apply { writeText("unchanged source fixture") }
        val player = temporary.root.resolve("player_appearance.preferences_pb").apply { writeText("unchanged player fixture") }
        val sourceBefore = source.readBytes(); val playerBefore = player.readBytes()
        try { HomeAppearanceDataStore(PreferenceDataStoreFactory.create(scope = scope) { file }).save(expected) }
        finally { scope.coroutineContext[Job]!!.cancelAndJoin() }
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            assertEquals(expected, HomeAppearanceDataStore(PreferenceDataStoreFactory.create(scope = scope) { file }).observe().first())
            assertArrayEquals(sourceBefore, source.readBytes()); assertArrayEquals(playerBefore, player.readBytes())
        } finally { scope.coroutineContext[Job]!!.cancelAndJoin() }
    }
    @Test fun sessionPreferenceDefaultsOnAndPersistsWithoutChangingExistingChoices() {
        val values = mutablePreferencesOf(stringPreferencesKey("future") to "keep")
        assertTrue(HomeAppearanceCodec.decode(values).restoreBrowseSession)
        val expected = HomeAppearance(HomeLanguage.GERMAN, SidebarSide.LEFT, true, 4, restoreBrowseSession = false)
        HomeAppearanceCodec.encode(values, expected)
        assertEquals(expected, HomeAppearanceCodec.decode(values))
        assertEquals("keep", values[stringPreferencesKey("future")])
    }
}