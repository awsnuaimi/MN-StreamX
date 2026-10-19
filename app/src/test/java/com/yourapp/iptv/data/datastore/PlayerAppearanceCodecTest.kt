package com.yourapp.iptv.data.datastore

import androidx.datastore.preferences.core.*
import com.yourapp.iptv.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class PlayerAppearanceCodecTest {
    @Test fun oldInstallationsReceiveUsableDefaults() {
        assertEquals(PlayerAppearance(), PlayerAppearanceCodec.decode(emptyPreferences()))
    }

    @Test fun allOptionsRoundTripWithoutDeletingUnrelatedKeys() {
        val value = PlayerAppearance(PlayerInfoStyle.CARDS, PlayerTextSize.LARGE, PlayerPanelShade.SOLID,
            displaySeconds = 8, showLogo = false, showGroup = false, showCounter = false,
            showNext = false, showDescription = false, showChannelButtons = false, showHints = true)
        val preferences = mutablePreferencesOf(stringPreferencesKey("future_option") to "preserve")
        PlayerAppearanceCodec.encode(preferences, value)
        assertEquals(value, PlayerAppearanceCodec.decode(preferences))
        assertEquals("preserve", preferences[stringPreferencesKey("future_option")])
    }

    @Test fun invalidEnumsAndTimeoutUseSafeDefaults() {
        val values = mutablePreferencesOf(stringPreferencesKey("info_style") to "removed-style",
            stringPreferencesKey("text_size") to "HUGE", stringPreferencesKey("panel_shade") to "unknown",
            intPreferencesKey("display_seconds") to Int.MAX_VALUE)
        assertEquals(PlayerAppearance(), PlayerAppearanceCodec.decode(values))
        assertEquals(5000L, PlayerAppearance(displaySeconds = -2).timeoutMillis)
        assertEquals(3000L, PlayerAppearance(displaySeconds = 3).timeoutMillis)
    }

    @Test fun incompatibleStoredTypesCannotCrashDecoding() {
        val values = mutablePreferencesOf(intPreferencesKey("info_style") to 4,
            stringPreferencesKey("display_seconds") to "8", stringPreferencesKey("show_logo") to "false")
        assertEquals(PlayerAppearance(), PlayerAppearanceCodec.decode(values))
    }

    @Test fun resettingAppearanceResetsOnlyItsOwnOptions() {
        val values = mutablePreferencesOf(stringPreferencesKey("future_option") to "preserve")
        PlayerAppearanceCodec.encode(values, PlayerAppearance(style = PlayerInfoStyle.CARDS, showHints = true))
        PlayerAppearanceCodec.encode(values, PlayerAppearance())
        assertEquals(PlayerAppearance(), PlayerAppearanceCodec.decode(values))
        assertEquals("preserve", values[stringPreferencesKey("future_option")])
    }

    @Test fun oldStoredStylesMigrateToCardsAndKeepOtherOptions() {
        for (old in listOf("COMPACT", "CLASSIC", "CARDS", "unknown")) {
            val values = mutablePreferencesOf(stringPreferencesKey("info_style") to old,
                intPreferencesKey("display_seconds") to 8, booleanPreferencesKey("show_logo") to false)
            val restored = PlayerAppearanceCodec.decode(values)
            assertEquals(PlayerInfoStyle.CARDS, restored.style)
            assertFalse(restored.showLogo)
            assertEquals(8000L, restored.timeoutMillis)
        }
        assertEquals(PlayerInfoStyle.CARDS, PlayerAppearance().style)
        assertEquals(PlayerInfoStyle.CARDS, PlayerAppearanceCodec.decode(emptyPreferences()).style)
    }
}