package com.yourapp.iptv.domain.organization

import org.junit.Assert.*
import org.junit.Test

class FolderEditsTest {
    @Test fun reparentingKeepsStableIdsAndUpdatesAllDescendantPaths() {
        val config = OrganizationConfig(enabled = true)
        val withParent = FolderEdits.upsert(config, "custom-family", "Family", null)
        val moved = FolderEdits.upsert(withParent, "arabic", "Arabic family", "custom-family")
        val tree = OrganizationTree(true, moved.folders)
        assertEquals(listOf("custom-family", "arabic", "arabic-syria"), tree.path("arabic-syria").map { it.id })
    }
    @Test fun cyclesMissingParentsAndDeepNestingAreRejectedWithoutChangingTheOriginal() {
        val original = OrganizationConfig()
        assertThrows(IllegalArgumentException::class.java) { FolderEdits.upsert(original, "arabic", "Bad", "arabic-syria") }
        assertThrows(IllegalArgumentException::class.java) { FolderEdits.upsert(original, "custom-a", "Bad", "missing") }
        var chain = original
        repeat(6) { i -> chain = FolderEdits.upsert(chain, "custom-$i", "Level $i", if (i == 0) null else "custom-${i-1}") }
        val snapshot = chain
        assertThrows(IllegalArgumentException::class.java) { FolderEdits.upsert(snapshot, "custom-deep", "Too deep", "custom-5") }
        assertEquals(OrganizationDefaults.folders(), original.folders)
    }
    @Test fun reorderingChangesSiblingOrderWithoutMovingChildrenToAnotherParent() {
        val config = OrganizationConfig()
        val moved = FolderEdits.move(config, "arabic-egypt", -1)
        val tree = OrganizationTree(true, moved.folders)
        assertEquals("arabic-egypt", tree.children("arabic").first().id)
        assertTrue(tree.children("arabic").all { it.parentId == "arabic" })
        assertEquals(config, FolderEdits.move(config, "arabic", -1))
    }
    @Test fun batchMovesKeepIndividualRulesAndOrganizationIdentity() {
        val sy = OrganizationNames.groupKey("SYRIA"); val eg = OrganizationNames.groupKey("EGYPT")
        val original = OrganizationConfig(sourceKey = "source", enabled = true, revision = 9,
            folderSort = FolderSort.COUNT, channelOverrides = mapOf("s" to "polish-news"))
        val moved = FolderEdits.assignGroups(original, setOf(sy, eg), setOf(sy, eg), "german-general")
        assertEquals(mapOf(sy to "german-general", eg to "german-general"), moved.groupOverrides)
        assertEquals(original.channelOverrides, moved.channelOverrides)
        assertEquals(original.sourceKey, moved.sourceKey); assertEquals(9L, moved.revision)
        assertEquals(FolderSort.COUNT, moved.folderSort); assertTrue(original.groupOverrides.isEmpty())
        val channels = listOf(com.yourapp.iptv.domain.model.Channel("s", "Syria", streamUrl = "https://unit.test/s", group = "SYRIA"),
            com.yourapp.iptv.domain.model.Channel("e", "Egypt", streamUrl = "https://unit.test/e", group = "EGYPT"))
        val preview = ChannelOrganizer.project(channels, moved)
        assertEquals("polish-news", preview.assignments.getValue("s").folderId)
        assertEquals("german-general", preview.assignments.getValue("e").folderId)
    }
    @Test fun invalidBatchCannotPartiallyChangeAnExistingRule() {
        val key = OrganizationNames.groupKey("SYRIA")
        val original = OrganizationConfig(groupOverrides = mapOf(key to "arabic-syria"))
        assertThrows(IllegalArgumentException::class.java) {
            FolderEdits.assignGroups(original, setOf(key), setOf(key, "unknown"), "german-news")
        }
        assertThrows(IllegalArgumentException::class.java) {
            FolderEdits.assignGroups(original, setOf(key), setOf(key), "missing-folder")
        }
        assertEquals(mapOf(key to "arabic-syria"), original.groupOverrides)
    }
    @Test fun batchRuleRemovalLeavesUnselectedGroupsIntactAndEmptySelectionIsANoOp() {
        val original = OrganizationConfig(groupOverrides = mapOf("one" to "german-news", "two" to "polish-news"),
            channelOverrides = mapOf("channel" to "arabic-syria"))
        val changed = FolderEdits.assignGroups(original, setOf("one", "two"), setOf("one"), null)
        assertEquals(mapOf("two" to "polish-news"), changed.groupOverrides)
        assertEquals(original.channelOverrides, changed.channelOverrides)
        assertTrue(original.groupOverrides.containsKey("one"))
        assertSame(original, FolderEdits.assignGroups(original, emptySet(), emptySet(), "missing"))
    }
}