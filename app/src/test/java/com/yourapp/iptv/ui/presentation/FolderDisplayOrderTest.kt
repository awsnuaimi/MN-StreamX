package com.yourapp.iptv.ui.presentation

import com.yourapp.iptv.domain.organization.*
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class FolderDisplayOrderTest {
    private val rows = listOf(ChannelFolder("a", name = "Zulu", order = 0),
        ChannelFolder("b", name = "Äpfel", order = 1), ChannelFolder("c", name = "Bravo", order = 2))
    private val names = rows.associate { it.id to it.name }
    @Test fun savedOrderStaysIntactAndNameOrderUsesTheCurrentLanguage() {
        val tree = OrganizationTree(true, rows)
        assertSame(rows, FolderDisplayOrder.folders(rows, tree, names, Locale.GERMAN))
        val sorted = FolderDisplayOrder.folders(rows, OrganizationTree(true, rows, folderSort = FolderSort.NAME), names, Locale.GERMAN)
        assertEquals(listOf("b", "c", "a"), sorted.map { it.id })
        assertEquals(listOf("a", "b", "c"), rows.map { it.id })
    }
    @Test fun countOrderBreaksTiesByNameWithoutChangingParentsOrManualOrders() {
        val tree = OrganizationTree(true, rows, mapOf("a" to 50, "b" to 9, "c" to 50), folderSort = FolderSort.COUNT)
        val sorted = FolderDisplayOrder.folders(rows, tree, names, Locale.GERMAN)
        assertEquals(listOf("c", "a", "b"), sorted.map { it.id })
        sorted.forEach { assertEquals(rows.single { old -> old.id == it.id }, it) }
    }
    @Test fun sourceGroupSortingPreservesStableGroupKeysAndCounts() {
        val rows = listOf(SourceGroup("z", "Zulu", 4, setOf("a")), SourceGroup("b", "Bravo", 90, setOf("b")))
        val sorted = FolderDisplayOrder.groups(rows, FolderSort.COUNT, Locale.ENGLISH)
        assertEquals(listOf("b", "z"), sorted.map { it.key })
        assertEquals(94, sorted.sumOf { it.count })
        assertSame(rows, FolderDisplayOrder.groups(rows, FolderSort.SAVED, Locale.ENGLISH))
    }
}