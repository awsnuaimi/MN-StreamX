package com.yourapp.iptv.ui.screen.settings

import com.yourapp.iptv.domain.model.HomeLanguage
import org.junit.Assert.*
import org.junit.Test

class SettingsCatalogTest {
    @Test fun emptySearchExposesEveryRealSectionInTheSameOrderForEveryLanguage() {
        HomeLanguage.entries.forEach { language ->
            val labels = SettingsLabels(language)
            assertEquals(SettingsCategory.entries.toList(), SettingsCatalog.search("", labels))
            SettingsCategory.entries.forEach { assertTrue(labels.title(it).isNotBlank());assertTrue(labels.scope(it).isNotBlank()) }
        }
    }
    @Test fun arabicMarksEnglishTermsAndGermanAccentsMatchOptionsWithoutReadingUserData() {
        val labels = SettingsLabels(HomeLanguage.ARABIC)
        assertEquals(listOf(SettingsCategory.APPEARANCE), SettingsCatalog.search("أَحْمر", labels))
        assertEquals(listOf(SettingsCategory.APPEARANCE), SettingsCatalog.search("grid columns", labels))
        assertEquals(listOf(SettingsCategory.CAPTIONS), SettingsCatalog.search("Übersetzung", labels))
        assertEquals(listOf(SettingsCategory.DATA), SettingsCatalog.search("نسخ", labels))
        assertTrue(SettingsCatalog.search("synthetic-secret-credential", labels).isEmpty())
    }
    @Test fun profileAndDeviceScopeLabelsDoNotPretendThatCaptionsAreProfileScoped() {
        val labels = SettingsLabels(HomeLanguage.ENGLISH)
        assertEquals("For this profile", labels.scope(SettingsCategory.APPEARANCE))
        assertEquals("For this profile", labels.scope(SettingsCategory.PLAYBACK))
        assertEquals("For this device", labels.scope(SettingsCategory.CAPTIONS))
        assertEquals("For this device", labels.scope(SettingsCategory.UPDATES))
    }
}