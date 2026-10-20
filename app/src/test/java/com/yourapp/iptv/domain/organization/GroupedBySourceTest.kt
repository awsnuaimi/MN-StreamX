package com.yourapp.iptv.domain.organization

import com.yourapp.iptv.data.datastore.OrganizationCodec
import com.yourapp.iptv.domain.model.Channel
import com.yourapp.iptv.ui.presentation.FolderDisplayOrder
import com.yourapp.iptv.ui.presentation.OrganizationSearchIndex
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class GroupedBySourceTest {
    private val config = OrganizationConfig(enabled = true)
    private fun channel(id: String, group: String?, name: String) =
        Channel(id, name, streamUrl = "https://unit.test/$id", group = group)
    private fun folder(result: OrganizedChannels, id: String) =
        result.tree.folder(result.assignments.getValue(id).folderId)!!

    @Test fun fortyFourAndTwelveChannelsGetTwoOriginalNamedFoldersWithoutGeneralMerging() {
        val input = (1..44).map { channel("a$it", "PL Ogólnotema", "TVP $it") } +
            (1..12).map { channel("b$it", "kanały", "TVN$it") }
        val result = ChannelOrganizer.project(input, config)
        val first = folder(result, "a1"); val second = folder(result, "b1")
        assertNotEquals(first.id, second.id)
        assertEquals("PL Ogólnotema", first.name); assertEquals("kanały", second.name)
        listOf(first, second).forEach { assertEquals("polish", it.parentId); assertNull(it.label) }
        assertEquals(44, result.tree.counts[first.id]); assertEquals(12, result.tree.counts[second.id])
        assertEquals(56, result.tree.counts["polish"])
        assertEquals(0, result.tree.counts["polish-general"] ?: 0)
    }

    @Test fun allRootsUseProviderFoldersAndTheSameSourceRetainsItsIdentity() {
        val input = listOf(channel("p", "PL Ogólnotema", "TVP 1"), channel("d", "DE Sport", "Sky Bundesliga"),
            channel("a", "AR أخبار", "الجزيرة"), channel("u", "Mystery", "Unknown"))
        val result = ChannelOrganizer.project(input, config)
        val expected = mapOf("p" to "polish", "d" to "german", "a" to "arabic", "u" to "unclassified")
        input.forEach { original ->
            val f = folder(result, original.id)
            assertEquals(original.group, f.name); assertNull(f.label); assertEquals(expected[original.id], f.parentId)
            assertEquals(original, result.channels.single { it.id == original.id }.copy(folderIds = emptyList()))
        }
        val next = ChannelOrganizer.project(input.reversed(), config)
        assertEquals(result.assignments, next.assignments)
    }

    @Test fun explicitGroupRootWinsOverChannelVotesAndNoIndividualChannelEscapesTheConsensus() {
        val explicit = ChannelOrganizer.project(listOf(channel("x", "PL Mixed", "ZDF")), config)
        assertEquals("polish", folder(explicit, "x").parentId)
        val input = listOf(channel("p1", "Mixed", "TVP 1"), channel("p2", "Mixed", "Polsat"),
            channel("p3", "Mixed", "TVN7"), channel("d", "Mixed", "ZDF"), channel("u", "Mixed", "Unknown"))
        val result = ChannelOrganizer.project(input, config)
        assertEquals(1, result.assignments.values.map { it.folderId }.distinct().size)
        assertEquals("polish", folder(result, "d").parentId)
    }

    @Test fun insufficientConsensusAndDuplicateChannelIdsCannotManufactureSixtyPercent() {
        val p = channel("p", "Mixed", "TVP 1")
        val result = ChannelOrganizer.project(listOf(p, p, p, channel("u", "Mixed", "Unknown")), config)
        assertEquals(2, result.channels.size)
        assertEquals("unclassified", folder(result, "p").parentId)
        val inconclusive = ChannelOrganizer.project(listOf(channel("a", "kanały", "TVP Sport"),
            channel("b", "kanały", "Canal+ Sport")), config)
        assertEquals("unclassified", folder(inconclusive, "a").parentId)
    }

    @Test fun manualGroupAndChannelRulesSurviveReopeningAndBeatNetworksAndDetection() {
        val custom = ChannelFolder("custom-sport", name = "My sport")
        val saved = config.copy(folders = config.folders + custom,
            groupOverrides = mapOf(OrganizationNames.groupKey("Polsat Sport") to custom.id),
            channelOverrides = mapOf("direct" to "german-news"))
        val reopened = OrganizationCodec.decode(OrganizationCodec.encode(saved), "")
        val input = listOf(channel("group", "Polsat Sport", "beIN Sports 1"),
            channel("direct", "Polsat Sport", "Polsat Sport"), channel("new", "Polsat Sport", "TVP Sport"))
        val result = ChannelOrganizer.project(input, reopened)
        assertEquals("german-news", result.assignments.getValue("direct").folderId)
        assertEquals(AssignmentReason.CHANNEL_RULE, result.assignments.getValue("direct").reason)
        listOf("group", "new").forEach {
            assertEquals(custom.id, result.assignments.getValue(it).folderId)
            assertEquals(AssignmentReason.GROUP_RULE, result.assignments.getValue(it).reason)
        }
        assertEquals(saved, reopened)
    }

    @Test fun legacyGeneralDestinationsAndUnmatchedRulesRemainAvailable() {
        val previous = config.copy(groupOverrides = mapOf(OrganizationNames.groupKey("Polsat Sport") to "polish-general",
            "unmatched-key" to "german-general"))
        val result = ChannelOrganizer.project(listOf(channel("p", "Polsat Sport", "Polsat")), previous)
        assertEquals("polish-general", result.assignments.getValue("p").folderId)
        assertEquals(1, result.tree.counts["polish-general"])
        assertEquals(2, previous.groupOverrides.size)
        assertNotNull(result.tree.folder("german-general"))
    }

    @Test fun beinSportsAreUnifiedButEntertainmentKeepsTheExistingException() {
        val result = ChannelOrganizer.project(listOf(channel("b1", "PL Sport", "beIN SPORTS 1"),
            channel("b2", "AR BEIN SPORTS", "beINSPORTS2"), channel("m", "AR BEIN SPORTS", "beIN Movies 1")), config)
        assertEquals("bein", result.assignments.getValue("b1").folderId)
        assertEquals("bein", result.assignments.getValue("b2").folderId)
        assertEquals(2, result.tree.counts["bein"])
        assertEquals("arabic", folder(result, "m").parentId)
    }

    @Test fun matchingFoldDoesNotMergeDistinctPersistedGroupKeysOrRewriteNames() {
        val input = listOf(channel("a", "PL Kanały", "TVP 1"), channel("b", "PL Kanaly", "TVP 2"),
            channel("c", "  PL Other  ", "TVP 3"))
        val result = ChannelOrganizer.project(input, config)
        assertNotEquals(folder(result, "a").id, folder(result, "b").id)
        input.forEach { assertEquals(it.group, folder(result, it.id).name) }
        assertEquals("c7a6f34966877c6177801ed608a9330b", OrganizationNames.groupKey("PL Kanały HD"))
    }

    @Test fun qualityAliasesKeepTheExistingSourceGroupAndManualKeyContract() {
        val result = ChannelOrganizer.project(listOf(channel("a", "PL Kanały HD", "TVP 1 HD"),
            channel("b", "PL Kanały FHD", "TVP 1 FHD")), config)
        assertEquals(1, result.groups.size)
        assertEquals(folder(result, "a").id, folder(result, "b").id)
        assertEquals("PL Kanały HD", folder(result, "a").name)
        assertEquals(2, result.tree.counts[folder(result, "a").id])
    }

    @Test fun savedNameAndCountSortingAndOriginalNameSearchRemainUsable() {
        val input = listOf(channel("a", "PL Zulu", "TVP 1"), channel("b", "PL Alpha", "TVP 2"),
            channel("c", "PL Alpha", "TVP 3"))
        for (sort in FolderSort.entries) {
            val result = ChannelOrganizer.project(input, config.copy(folderSort = sort))
            assertEquals(sort, result.tree.folderSort)
            val rows = result.tree.children("polish").filter { (result.tree.counts[it.id] ?: 0) > 0 }
            val ordered = FolderDisplayOrder.folders(rows, result.tree, rows.associate { it.id to it.name }, Locale.GERMAN)
            val expected = if (sort == FolderSort.SAVED) listOf("PL Zulu", "PL Alpha") else listOf("PL Alpha", "PL Zulu")
            assertEquals(expected, ordered.map { it.name })
            val labels = FolderLabel.entries.associateWith { it.name }
            assertEquals(setOf("b", "c"), OrganizationSearchIndex(result, labels, Locale.GERMAN)
                .find("Alpha").channels.map { it.id }.toSet())
        }
    }

    @Test fun unchangedOldFolderCustomizationsAreNotDeletedOrRewritten() {
        val oldSourceId = "src-" + OrganizationNames.digest("unclassified" + OrganizationNames.groupKey("Unknown")).take(32)
        val custom = ChannelFolder("custom-family", name = "Family")
        val old = ChannelFolder(oldSourceId, custom.id, name = "My existing folder", order = 17)
        val saved = config.copy(folders = config.folders + custom + old)
        val result = ChannelOrganizer.project(listOf(channel("u", "Unknown", "Unknown station")), saved)
        assertEquals(old, result.tree.folder(oldSourceId))
        assertEquals(listOf(custom.id, oldSourceId), result.channels.single().folderIds)
        assertEquals(config.folders + custom + old, saved.folders)
    }

    @Test fun largeGroupsRemainCompleteAndUnnamedSourcesHaveASafeFallback() {
        val input = (1..10000).map { channel("id$it", "kanały", if (it <= 6000) "TVP $it" else "Unknown $it") }
        val result = ChannelOrganizer.project(input, config)
        assertEquals(10000, result.channels.size)
        assertEquals(10000, result.tree.counts["polish"])
        assertEquals(1, result.assignments.values.map { it.folderId }.distinct().size)
        val unnamed = ChannelOrganizer.project(listOf(channel("u", null, "Unknown")), config)
        assertEquals(FolderLabel.OTHER, folder(unnamed, "u").label)
        FolderEdits.validate(config.copy(folders = unnamed.tree.folders))
    }
}