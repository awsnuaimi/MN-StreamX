package com.yourapp.iptv.domain.organization

import com.yourapp.iptv.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class ChannelOrganizerTest {
    private fun c(id: String, group: String?, name: String = id, favorite: Boolean = false) =
        Channel(id, name, "https://unit.test/logo/$id", "https://unit.test/live/$id", group, favorite)
    private val config = OrganizationConfig(enabled = true)

    @Test fun arabicCountryAliasesAndQualityGroupsShareParentsWithoutLosingTheirSources() {
        val input = listOf(c("s1", "AR | SYRIA FHD"), c("s2", "Syria HD"), c("s3", "القنوات السورية"),
            c("e", "AR EGYPT"), c("l", "LB LEBANON"), c("q", "AR Qatar"))
        val result = ChannelOrganizer.project(input, config)
        assertEquals(6, result.tree.counts["arabic"])
        assertEquals(3, result.tree.counts["arabic-syria"])
        assertEquals("arabic-egypt", result.assignments.getValue("e").folderId)
        result.channels.forEach { current ->
            val original = input.single { it.id == current.id }
            assertEquals(original, current.copy(folderIds = emptyList()))
        }
        assertTrue(input.all { it.folderIds.isEmpty() })
    }
    @Test fun sportsNetworkOverridesCountryAndDoesNotCaptureBeinEntertainment() {
        val result = ChannelOrganizer.project(listOf(c("a", "AR SPORTS", "beIN SPORTS 1 FHD"),
            c("b", "FR BEIN SPORTS", "beINSPORTS2 HD"), c("m", "AR BEIN SPORTS", "beIN MOVIES 1")), config)
        assertTrue(result.channels.single { it.id == "a" }.folderIds.contains("bein"))
        assertTrue(result.channels.single { it.id == "b" }.folderIds.contains("bein"))
        assertFalse(result.channels.single { it.id == "m" }.folderIds.contains("bein"))
        assertEquals(2, result.tree.counts["bein"])
    }
    @Test fun germanAndPolishGenresAreRecognizedAndCountryCodesRespectWordBoundaries() {
        val result = ChannelOrganizer.project(listOf(c("d", "DE SPORT"), c("p", "PL|INFORMACYJNE"),
            c("k", "PL BAJKI"), c("f", "COTE DE IVOIRE")), config)
        assertEquals("german-sports", result.assignments.getValue("d").folderId)
        assertEquals("polish-news", result.assignments.getValue("p").folderId)
        assertEquals("polish-kids", result.assignments.getValue("k").folderId)
        assertTrue(result.channels.single { it.id == "f" }.folderIds.contains("unclassified"))
    }
    @Test fun channelRulesWinOverGroupRulesAndNewChannelsInTheGroupInheritItsRule() {
        val key = OrganizationNames.groupKey("SYRIA HD")
        val custom = ChannelFolder("custom-family", name = "Family")
        val settings = config.copy(folders = config.folders + custom, groupOverrides = mapOf(key to custom.id),
            channelOverrides = mapOf("s1" to "german-news"))
        val result = ChannelOrganizer.project(listOf(c("s1", "SYRIA HD"), c("s2", "SYRIA FHD"), c("new", "SYRIA HD")), settings)
        assertEquals("german-news", result.assignments.getValue("s1").folderId)
        assertEquals(AssignmentReason.CHANNEL_RULE, result.assignments.getValue("s1").reason)
        assertEquals(2, result.tree.counts[custom.id])
        val reverted = ChannelOrganizer.project(result.channels, settings.copy(channelOverrides = emptyMap()))
        assertEquals(3, reverted.tree.counts[custom.id])
    }
    @Test fun rootFiltersIncludeDescendantsAndWorkWithFavoritesAndSearch() {
        val result = ChannelOrganizer.project(listOf(c("s", "SYRIA", "News Syria", true),
            c("e", "EGYPT", "News Egypt", true), c("l", "LEBANON", "Music"), c("d", "DE NEWS", "News Germany", true)), config)
        val queue = PlaybackQueue.create(result.channels, FolderFilter.of("arabic"), "News", true)
        assertEquals(setOf("s", "e"), queue.channels.map { it.id }.toSet())
        assertEquals(FolderFilter.of("arabic-syria"), result.resolveFilter("SYRIA"))
        assertEquals(FolderFilter.of("arabic"), result.resolveFilter(FolderFilter.of("arabic")))
    }
    @Test fun disablingOrganizationPreservesProviderOrderAndLiteralGroupNames() {
        val input = listOf(c("z", "mnx-folder:source"), c("a", "SYRIA"))
        val result = ChannelOrganizer.project(input, config.copy(enabled = false))
        assertEquals(input, result.channels)
        assertEquals("mnx-folder:source", result.resolveFilter("mnx-folder:source"))
        assertEquals("z", PlaybackQueue.create(result.channels, "mnx-folder:source").channels.single().id)
        assertNull(result.resolveFilter(FolderFilter.of("arabic")))
    }
    @Test fun numericOrderIsNaturalAndEveryQualityKeepsItsOwnIdentity() {
        val result = ChannelOrganizer.project(listOf(c("10", "BEIN SPORTS", "beIN Sports 10 HD"),
            c("2h", "BEIN SPORTS", "beIN Sports 2 HD"), c("2f", "BEIN SPORTS", "beIN Sports 2 FHD"),
            c("1", "BEIN SPORTS", "beIN Sports 1 SD")), config)
        assertEquals(listOf("1", "2f", "2h", "10"), result.channels.map { it.id })
        assertEquals(4, result.channels.map { it.streamUrl }.distinct().size)
    }
    @Test fun sourceAndGroupIdentitiesAreStableWithoutPersistingPasswords() {
        val first = AppSettings(source = PlaylistSource.XTREAM, xtreamServer = "https://unit.test/", xtreamUsername = "user", xtreamPassword = "private-old")
        assertEquals(OrganizationNames.sourceKey(first), OrganizationNames.sourceKey(first.copy(xtreamPassword = "private-new", xtreamServer = "https://unit.test")))
        assertNotEquals(OrganizationNames.sourceKey(first), OrganizationNames.sourceKey(first.copy(xtreamUsername = "other")))
        assertEquals(64, OrganizationNames.sourceKey(first).length)
        assertEquals(OrganizationNames.groupKey("SYRIA FHD"), OrganizationNames.groupKey("Syria HD"))
        assertNotEquals(OrganizationNames.groupKey("SD"), OrganizationNames.groupKey(null))
    }
    @Test fun largeListsStayCompleteWithCountsMatchingEveryAncestor() {
        val input = (1..10000).map { c("id$it", if (it % 2 == 0) "DE SPORT" else "AR SYRIA", "Channel $it") }
        val result = ChannelOrganizer.project(input, config)
        assertEquals(10000, result.channels.size)
        assertEquals(5000, result.tree.counts["german"])
        assertEquals(5000, result.tree.counts["arabic-syria"])
        assertEquals(10000, result.channels.map { it.id }.distinct().size)
    }
    @Test fun automaticGroupsAtTheDepthLimitStayReachableThroughEveryAncestor() {
        var value = config
        repeat(5) { i -> value = FolderEdits.upsert(value, "custom-$i", "Level $i", if (i == 0) null else "custom-${i-1}") }
        value = FolderEdits.upsert(value, "unclassified", "Review", "custom-4")
        val result = ChannelOrganizer.project(listOf(c("unknown", "Unrecognized source group")), value)
        assertEquals(6, result.channels.single().folderIds.size)
        assertTrue(result.channels.single().folderIds.contains("custom-0"))
        assertEquals(1, PlaybackQueue.create(result.channels, FolderFilter.of("custom-0")).size)
    }
}