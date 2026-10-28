package com.yourapp.iptv.data.datastore

import androidx.datastore.preferences.core.*
import com.yourapp.iptv.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ChannelBrowserAppearanceTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun everyLayoutAndLogoChoiceRoundTripsWithoutChangingOtherPreferences() {
        val values = mutablePreferencesOf(stringPreferencesKey("future") to "preserved")
        for (language in HomeLanguage.entries) for (layout in HomeChannelLayout.entries)
            for (logos in listOf(false, true)) for (columns in 2..5) {
                val expected = HomeAppearance(language = language, columns = columns,
                    theme = HomeTheme.RED, isNightMode = true, channelLayout = layout, showChannelLogos = logos)
                HomeAppearanceCodec.encode(values, expected)
                assertEquals(expected, HomeAppearanceCodec.decode(values))
                assertEquals("preserved", values[stringPreferencesKey("future")])
            }
    }

    @Test fun malformedNewPreferencesUseSafeDefaultsAndDoNotChangeTheme() {
        for (bad in listOf(
            mutablePreferencesOf(stringPreferencesKey("channel_layout") to "future", stringPreferencesKey("channel_logos") to "no"),
            mutablePreferencesOf(intPreferencesKey("channel_layout") to 9, intPreferencesKey("channel_logos") to 0))) {
            bad[stringPreferencesKey("color_theme")] = "RED"
            val value = HomeAppearanceCodec.decode(bad)
            assertEquals(HomeChannelLayout.GRID, value.channelLayout)
            assertTrue(value.showChannelLogos); assertEquals(HomeTheme.RED, value.theme)
        }
        assertEquals(5, HomeAppearance(columns = 5).normalized().columns)
        assertEquals(3, HomeAppearance(columns = 6).normalized().columns)
    }

    @Test fun listHiddenLogosAndFiveColumnsSurviveARealDataStoreReopen() = runBlocking {
        val file = temporary.root.resolve("home.preferences_pb")
        val value = HomeAppearance(language = HomeLanguage.GERMAN, sidebarSide = SidebarSide.LEFT,
            columns = 5, restoreBrowseSession = false, channelLayout = HomeChannelLayout.LIST, showChannelLogos = false)
        var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try { HomeAppearanceDataStore(PreferenceDataStoreFactory.create(scope = scope) { file }).save(value) }
        finally { scope.coroutineContext[Job]!!.cancelAndJoin() }
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try { assertEquals(value, HomeAppearanceDataStore(PreferenceDataStoreFactory.create(scope = scope) { file }).observe().first()) }
        finally { scope.coroutineContext[Job]!!.cancelAndJoin() }
    }

    @Test fun returningToDefaultsRemovesOnlyNewKeysSoOldCanonicalBackupsStillMatch() {
        val prefs = mutablePreferencesOf(stringPreferencesKey("unrelated") to "keep")
        HomeAppearanceCodec.encode(prefs, HomeAppearance(channelLayout = HomeChannelLayout.LIST, showChannelLogos = false))
        assertTrue(prefs.contains(stringPreferencesKey("channel_layout")))
        HomeAppearanceCodec.encode(prefs, HomeAppearance())
        assertFalse(prefs.contains(stringPreferencesKey("channel_layout")))
        assertFalse(prefs.contains(booleanPreferencesKey("channel_logos")))
        assertEquals("keep", prefs[stringPreferencesKey("unrelated")])
        assertEquals(HomeAppearance(), HomeAppearanceCodec.decode(prefs))
    }

    @Test fun browseSessionsKeepListAndFiveColumnAnchorsAndRejectUnsupportedWidths() {
        for (columns in listOf(1, 5)) {
            val session = HomeBrowseSession(group = "أخبار", focusedChannelId = "stable-42",
                position = HomeGridPosition("stable-40", 14, columns))
            val text = HomeSessionCodec.encode(session, 100)
            assertEquals(session, HomeSessionCodec.decode(text))
            assertNull(HomeSessionCodec.decode(text.replace("\"columns\":$columns", "\"columns\":6")))
        }
    }
}