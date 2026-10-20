package com.yourapp.iptv.domain.organization

import com.yourapp.iptv.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class ChannelOrganizerTest {
    private fun c(id: String, group: String?, name: String = id, favorite: Boolean = false) =
        Channel(id, name, "https://unit.test/logo/$id", "https://unit.test/live/$id", group, favorite)
    private val config = OrganizationConfig(enabled = true)
    private fun sourceFolder(result: OrganizedChannels, id: String) =
        result.tree.folder(result.assignments.getValue(id).folderId)!!

    @Test fun arabicCountryAliasesAndQualityGroupsShareParentsWithoutLosingTheirSources() {
        val input = listOf(c("s1", "AR | SYRIA FHD"), c("s2", "Syria HD"), c("s3", "القنوات السورية"),
            c("e", "AR EGYPT"), c("l", "LB LEBANON"), c("q", "AR Qatar"))
        val result = ChannelOrganizer.project(input, config)
        assertEquals(6, result.tree.counts["arabic"])
        listOf("s1", "s2", "s3", "e", "l", "q").forEach { id ->
            val folder = sourceFolder(result, id)
            assertEquals("arabic", folder.parentId)
            assertEquals(input.single { it.id == id }.group, folder.name)
            assertNull(folder.label)
        }
        assertEquals(0, result.tree.counts["arabic-syria"] ?: 0)
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
        assertEquals("german", sourceFolder(result, "d").parentId)
        assertEquals("polish", sourceFolder(result, "p").parentId)
        assertEquals("polish", sourceFolder(result, "k").parentId)
        assertNotEquals(sourceFolder(result, "p").id, sourceFolder(result, "k").id)
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
        assertEquals(FolderFilter.of(result.assignments.getValue("s").folderId), result.resolveFilter("SYRIA"))
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
        assertEquals(5000, result.tree.counts[sourceFolder(result, "id1").id])
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

    @Test fun multilingualSourceGroupsKeepTheirOriginalNamesUnderStableRoots() {
        val cases = mapOf("PL Ogólnotematyczne" to "polish", "PL Kanały" to "polish",
            "DE Sport" to "german", "AR أخبار" to "arabic")
        val result = ChannelOrganizer.project(cases.keys.mapIndexed { i, group -> c("case$i", group) }, config)
        cases.entries.forEachIndexed { i, (group, root) ->
            val folder = sourceFolder(result, "case$i")
            assertEquals(root, folder.parentId)
            assertEquals(group, folder.name)
            assertNull(folder.label)
            assertEquals(folder.id, result.channels.single { it.id == "case$i" }.folderIds.last())
        }
    }

    @Test fun changingActualHomeLanguageSettingDoesNotReassignAnyChannel() {
        val original = com.yourapp.iptv.domain.model.HomeAppearance(
            language = com.yourapp.iptv.domain.model.HomeLanguage.GERMAN)
        val changed = original.copy(language = com.yourapp.iptv.domain.model.HomeLanguage.ARABIC)
        assertNotEquals(original.language, changed.language)
        val input = listOf(c("p", "PL Kanały"), c("n", "AR أخبار"), c("d", "DE Sport"))
        val previous = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.GERMANY)
            val german = ChannelOrganizer.project(input, config)
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("ar"))
            val arabic = ChannelOrganizer.project(input, config)
            assertEquals(german.assignments.mapValues { it.value.folderId },
                arabic.assignments.mapValues { it.value.folderId })
            assertEquals(german.channels.associate { it.id to it.folderIds },
                arabic.channels.associate { it.id to it.folderIds })
        } finally { java.util.Locale.setDefault(previous) }
    }

    @Test fun missingChannelOrGroupDestinationUsesAutoFallbackWithoutCrash() {
        val missingGroup = OrganizationNames.groupKey("DE Sport")
        val corrupt = config.copy(channelOverrides = mapOf("p" to "missing-folder"),
            groupOverrides = mapOf(missingGroup to "also-missing"))
        val result = ChannelOrganizer.project(listOf(c("p", "PL Kanały"), c("d", "DE Sport")), corrupt)
        assertEquals("polish", sourceFolder(result, "p").parentId)
        assertEquals("german", sourceFolder(result, "d").parentId)
        assertTrue(result.channels.all { it.folderIds.isNotEmpty() })
        assertEquals("missing-folder", corrupt.channelOverrides.getValue("p"))
    }

    @Test fun invalidDirectOverrideStillPermitsValidGroupOverride() {
        val group = "PL Kanały"
        val manual = config.copy(channelOverrides = mapOf("p" to "missing-folder"),
            groupOverrides = mapOf(OrganizationNames.groupKey(group) to "polish-news"))
        val result = ChannelOrganizer.project(listOf(c("p", group)), manual)
        assertEquals("polish-news", result.assignments.getValue("p").folderId)
        assertEquals(AssignmentReason.GROUP_RULE, result.assignments.getValue("p").reason)
    }
}