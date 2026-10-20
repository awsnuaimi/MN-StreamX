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
}