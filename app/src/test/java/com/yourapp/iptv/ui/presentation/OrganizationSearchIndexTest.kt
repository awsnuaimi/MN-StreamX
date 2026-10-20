package com.yourapp.iptv.ui.presentation

import com.yourapp.iptv.domain.model.Channel
import com.yourapp.iptv.domain.organization.*
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class OrganizationSearchIndexTest {
    private val labels = FolderLabel.entries.associateWith { it.name }.toMutableMap().apply {
        put(FolderLabel.ARABIC, "القنوات العربية"); put(FolderLabel.SYRIA, "السورية")
    }
    private fun channel(id: String, group: String, name: String) = Channel(id, name, streamUrl = "https://unit.test/$id", group = group)
    private val source = listOf(channel("s", "AR SYRIA", "أخبار دمشق"), channel("e", "AR EGYPT", "News"),
        channel("p", "PL NEWS", "Wiadomości"), channel("u", "Unrecognized", "Unknown"))
    private fun index(config: OrganizationConfig = OrganizationConfig(enabled = true)) =
        OrganizationSearchIndex(ChannelOrganizer.project(source, config), labels, Locale.GERMAN)

    @Test fun searchMatchesAllWordsAcrossNamesAndLocalizedPathsIgnoringDiacritics() {
        val index = index()
        assertEquals(listOf("s"), index.find("اخبار سورية").channels.map { it.id })
        assertEquals(listOf("p"), index.find("Wiadomosci").channels.map { it.id })
        assertTrue(index.find("اخبار بولندا").channels.isEmpty())
        assertTrue(index.find("العربية").groups.any { it.name == "AR SYRIA" })
    }
    @Test fun unclassifiedAndFolderFiltersKeepSourceIdentitiesAndDoNotChangeThePreview() {
        val index = index(); val original = index.preview
        assertEquals(listOf("u"), index.find("", reviewOnly = true).channels.map { it.id })
        assertEquals(listOf("Unrecognized"), index.find("", reviewOnly = true).groups.map { it.name })
        assertEquals(setOf("s", "e"), index.find("", folder = "arabic").channels.map { it.id }.toSet())
        assertTrue(index.find("", folder = "arabic", reviewOnly = true).channels.isEmpty())
        assertSame(original, index.preview)
        assertTrue(source.all { it.folderIds.isEmpty() })
    }
    @Test fun rebuiltIndexFindsRenamedCustomPathsAndRemovesOldPathMatches() {
        val custom = ChannelFolder("custom-family", name = "Family")
        val config = OrganizationConfig(enabled = true, folders = OrganizationDefaults.folders() + custom,
            channelOverrides = mapOf("s" to custom.id))
        assertEquals(listOf("s"), index(config).find("Family").channels.map { it.id })
        val changed = FolderEdits.upsert(config, custom.id, "Friends", "polish")
        val next = index(changed)
        assertTrue(next.find("Family").channels.isEmpty())
        assertEquals(listOf("s"), next.find("Friends", "polish").channels.map { it.id })
    }
    @Test fun repeatedQueriesOnLargeListsKeepAllVariantsAndLeaveOrderingStable() {
        val source = (1..10000).map { channel("id-$it", "DE SPORT", "Station $it FHD") }
        val preview = ChannelOrganizer.project(source, OrganizationConfig(enabled = true))
        val index = OrganizationSearchIndex(preview, labels, Locale.ENGLISH)
        val before = index.find("").channels.map { it.id }
        repeat(4) {
            assertEquals("id-9876", index.find("Station 9876").channels.single().id)
            assertTrue(index.find("missing channel").channels.isEmpty())
        }
        assertEquals(before, index.find("").channels.map { it.id })
        assertEquals(10000, before.distinct().size)
    }
}