package com.yourapp.iptv.domain.organization

import com.yourapp.iptv.domain.model.Channel
import java.util.concurrent.CancellationException
import org.junit.Assert.*
import org.junit.Test

class OrganizationRecoveryTest {
    private val old = OrganizationNames.groupKey("PL Original")
    private val replacement = OrganizationNames.groupKey("PL Replacement")
    private val groups = listOf(
        SourceGroup(replacement, "PL Replacement", 2, emptySet())
    )
    private val config = OrganizationConfig(
        sourceKey = "source-a",
        enabled = true,
        groupOverrides = mapOf(old to "german-news")
    )

    @Test fun disabledOrEmptyPlaylistsDoNotReportEveryRuleAsMissing() {
        assertTrue(
            OrganizationRecovery.missing(
                config.copy(enabled = false), groups
            ).isEmpty()
        )
        assertTrue(OrganizationRecovery.missing(config, emptyList()).isEmpty())
        assertEquals(
            listOf(old),
            OrganizationRecovery.missing(config, groups).map { it.key }
        )
    }

    @Test fun savedSourceFoldersCanSupplyNamesButUnknownNamesAreNotInvented() {
        assertNull(OrganizationRecovery.missing(config, groups).single().name)
        val folder = ChannelFolder("src-original", name = "PL Original")
        val named = config.copy(folders = config.folders + folder)
        assertEquals(
            "PL Original",
            OrganizationRecovery.missing(named, groups).single().name
        )
        assertTrue(
            OrganizationRecovery.missing(
                config,
                groups + SourceGroup(old, "PL Original", 1, emptySet())
            ).isEmpty()
        )
    }

    @Test fun relinkingKeepsRevisionFoldersChannelRulesAndOtherGroupRules() {
        val initial = config.copy(
            revision = 7,
            groupOverrides = config.groupOverrides + ("other" to "polish-news"),
            channelOverrides = mapOf("direct" to "arabic-syria")
        )
        val plan = OrganizationRecovery.plan(initial, groups, old, replacement)
        assertEquals(initial, plan.before)
        assertEquals(7L, plan.after.revision)
        assertEquals(initial.folders, plan.after.folders)
        assertEquals(initial.channelOverrides, plan.after.channelOverrides)
        assertEquals("german-news", plan.after.groupOverrides[replacement])
        assertEquals("polish-news", plan.after.groupOverrides["other"])
        assertFalse(plan.after.groupOverrides.containsKey(old))
    }

    @Test fun existingManualAssignmentsAreNeverOverwritten() {
        val occupied = config.copy(
            groupOverrides = config.groupOverrides + (replacement to "polish-news")
        )
        assertThrows(IllegalArgumentException::class.java) {
            OrganizationRecovery.plan(occupied, groups, old, replacement)
        }
        assertEquals("polish-news", occupied.groupOverrides[replacement])
        assertThrows(IllegalArgumentException::class.java) {
            OrganizationRecovery.plan(config, groups, old, "absent")
        }
    }

    @Test fun undoOnlyChangesItsOwnRulesAndRejectsAnotherSourceOrConflictingEdit() {
        val plan = OrganizationRecovery.plan(config, groups, old, replacement)
        val token = GroupRelinkUndo(
            config.sourceKey, old, replacement, "german-news"
        )
        val newer = plan.after.copy(
            revision = 8,
            channelOverrides = mapOf("extra" to "polish-news")
        )
        val restored = OrganizationRecovery.undo(newer, token)
        assertEquals(config.groupOverrides, restored.groupOverrides)
        assertEquals(8L, restored.revision)
        assertEquals(newer.channelOverrides, restored.channelOverrides)
        assertThrows(IllegalArgumentException::class.java) {
            OrganizationRecovery.undo(newer.copy(sourceKey = "source-b"), token)
        }
        assertThrows(IllegalArgumentException::class.java) {
            OrganizationRecovery.undo(
                newer.copy(groupOverrides = mapOf(replacement to "polish-news")),
                token
            )
        }
    }

    @Test fun projectionPreservesIndividualAssignmentsAfterRelinking() {
        val input = listOf(
            Channel(
                "direct", "TVP 1",
                streamUrl = "https://unit.test/1",
                group = "PL Replacement"
            ),
            Channel(
                "normal", "TVP 2",
                streamUrl = "https://unit.test/2",
                group = "PL Replacement"
            )
        )
        val initial = config.copy(
            channelOverrides = mapOf("direct" to "arabic-syria")
        )
        val plan = OrganizationRecovery.plan(initial, groups, old, replacement)
        val result = ChannelOrganizer.project(input, plan.after)
        assertEquals("arabic-syria", result.assignments.getValue("direct").folderId)
        assertEquals("german-news", result.assignments.getValue("normal").folderId)
    }

    @Test fun largeProjectionRetainsSourceFolderIdentityAndCounts() {
        val input = (0 until 10000).map { i ->
            Channel(
                "c$i", "TVP $i",
                streamUrl = "https://unit.test/$i",
                group = "PL Group ${i % 100}"
            )
        }
        val result = ChannelOrganizer.project(
            input, OrganizationConfig(enabled = true)
        )
        assertEquals(10000, result.channels.size)
        assertEquals(100, result.groups.size)
        assertEquals(10000, result.tree.counts["polish"])
        result.groups.forEach { group ->
            val id = "src-" +
                OrganizationNames.digest("polish" + group.key).take(32)
            assertEquals(setOf(id), group.folderIds)
            assertEquals(100, result.tree.counts[id])
        }
    }

    @Test fun cancelledProjectionCannotContinuePublishingObsoleteWork() {
        assertThrows(CancellationException::class.java) {
            ChannelOrganizer.project(emptyList(), config) {
                throw CancellationException("cancelled")
            }
        }
    }
}