package com.yourapp.iptv.ui.presentation

import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.domain.organization.*
import com.yourapp.iptv.ui.state.HomeUiState
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class LiveTvPersonalizationTest {
    private val rows = listOf(Channel("fhd", "PL| News FHD", streamUrl = "fhd", group = "News", folderIds = listOf("root", "news")),
        Channel("hd", "PL| News HD", streamUrl = "hd", group = "News", folderIds = listOf("root", "news")),
        Channel("sport", "Sport HD", streamUrl = "sport", group = "Sports", folderIds = listOf("sport")))
    private val state = HomeUiState(channels = rows, visibleChannels = rows, groups = listOf("News", "Sports"))
    @Test fun hiddenChannelsAndRawGroupsAreFilteredWithoutEditingProviderDataOrFavorites() {
        val value = LiveTvPersonalization.project(state, LiveTvPreferences(hiddenGroups = setOf("News")))
        assertEquals(listOf("sport"), value.visibleChannels.map { it.id }); assertEquals(listOf("Sports"), value.groups)
        assertEquals(3, state.channels.size); assertEquals("News", rows.first().group)
        assertTrue(LiveTvPersonalization.project(state, LiveTvPreferences(hiddenChannels = rows.map { it.id }.toSet())).channels.isEmpty())
    }
    @Test fun hidingAParentFolderAlsoHidesDescendantsAndRestorationCannotAddProtectedChannels() {
        val tree = OrganizationTree(true, listOf(ChannelFolder("root", name = "Root"), ChannelFolder("news", "root", name = "News")))
        val before = state.copy(organization = tree)
        val hidden = LiveTvPersonalization.project(before, LiveTvPreferences(hiddenFolders = setOf("root")))
        assertEquals(listOf("sport"), hidden.channels.map { it.id }); assertEquals(0, hidden.organization.counts["news"] ?: 0)
        val protected = before.copy(channels = listOf(rows.last()), visibleChannels = listOf(rows.last()))
        assertEquals(listOf(rows.last()), LiveTvPersonalization.project(protected, LiveTvPreferences().showAll()).channels)
    }
    @Test fun preferredQualityWinsOverRestoredVariantAndMissingPreferenceFallsBackToAvailableQuality() {
        val family = LiveChannelRows.project(rows, LiveChannelSort.PROVIDER, Locale.GERMAN).first()
        val prefs = LiveTvPreferences().prefer(family.preferenceKey, "hd")
        assertEquals("hd", family.channel("fhd", prefs.preferredQualities).id)
        val replacement = prefs.prefer(family.preferenceKey, "fhd")
        assertEquals(mapOf(family.preferenceKey to "fhd"), replacement.preferredQualities)
        assertEquals("fhd", family.channel(null, mapOf(family.preferenceKey to "removed")).id)
        assertTrue(replacement.prefer(family.preferenceKey, null).preferredQualities.isEmpty())
    }
    @Test fun showAllPreservesSortQualityAndOnlyRemovesPersonalHiding() {
        val prefs = LiveTvPreferences(LiveTvSort.NAME, setOf("fhd"), setOf("News"), setOf("root"), mapOf("f".repeat(64) to "hd"))
        assertEquals(LiveTvPreferences(LiveTvSort.NAME, preferredQualities = mapOf("f".repeat(64) to "hd")), prefs.showAll())
    }
    @Test fun anUnchangedCatalogPreservesAuthoritativeFolderCountsAndTreeIdentity() {
        val tree = OrganizationTree(true, listOf(ChannelFolder("root", name = "Root")), mapOf("root" to 8))
        val catalog = state.copy(organization = tree)
        assertSame(tree, LiveTvPersonalization.project(catalog, LiveTvPreferences()).organization)
        assertEquals(8, LiveTvPersonalization.project(catalog, LiveTvPreferences(hiddenChannels = setOf("absent"))).organization.counts["root"])
    }

    @Test fun familyPreferenceSurvivesMissingVariantsProviderReorderingAndQualityReplacement() {
        val sd = rows.first().copy(id = "sd", name = "PL| News SD")
        fun project(values: List<Channel>) = LiveChannelRows.project(values, LiveChannelSort.PROVIDER, Locale.GERMAN).first()
        val complete = project(rows.take(2) + sd)
        val partial = project(listOf(sd))
        assertEquals(complete.preferenceKey, partial.preferenceKey)
        assertEquals(complete.preferenceKey, project(rows.take(2).reversed()).preferenceKey)
        val previous = LiveTvPreferences().prefer(complete.preferenceKey, "fhd")
        val changed = previous.prefer(partial.preferenceKey, "sd")
        assertEquals("sd", complete.channel(null, changed.preferredQualities).id)
        assertTrue(changed.prefer(partial.preferenceKey, null).preferredQualities.isEmpty())
    }

}