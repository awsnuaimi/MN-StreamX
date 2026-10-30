package com.yourapp.iptv.ui.component.common

import org.junit.Assert.*
import org.junit.Test

class FocusBookmarksTest {
    @Test fun exactIdentitySurvivesReordering() {
        val memory = FocusBookmarks()
        memory.record("films", "b", 1)
        assertEquals(0, memory.index("films", listOf("b", "a", "c")))
    }
    @Test fun removalChoosesNextAtTheSameSlotAndPreviousAtTheEnd() {
        val memory = FocusBookmarks()
        memory.record("films", "b", 1)
        assertEquals(1, memory.index("films", listOf("a", "c")))
        memory.record("films", "c", 2)
        assertEquals(1, memory.index("films", listOf("a", "b")))
        assertNull(memory.index("films", emptyList()))
    }
    @Test fun explicitDestinationWinsOverRememberedIdentity() {
        val memory = FocusBookmarks()
        memory.record("films", "a", 0)
        assertEquals(1, memory.index("films", listOf("a", "b"), "b"))
    }
    @Test fun independentCategoriesAndMediaKindsKeepTheirPlaces() {
        val memory = FocusBookmarks()
        memory.record("films:a", "same-id", 8)
        memory.record("series:a", "episode", 2)
        assertEquals("same-id", memory.get("films:a")?.key)
        assertNull(memory.get("films:b"))
    }
    @Test fun historyIsBoundedAndRevisitingKeepsAContextRecent() {
        val memory = FocusBookmarks()
        repeat(FocusBookmarks.LIMIT) { memory.record("context-$it", "key-$it", it) }
        memory.record("context-0", "key-0", 0)
        memory.record("new", "key", 0)
        assertEquals(FocusBookmarks.LIMIT, memory.entries().size)
        assertNotNull(memory.get("context-0")); assertNull(memory.get("context-1"))
    }
    @Test fun malformedOrOversizedIdentifiersAreIgnored() {
        val memory = FocusBookmarks()
        memory.record("", "a", 0); memory.record("context", "", 0)
        memory.record("x".repeat(1025), "a", 0); memory.record("context", "x".repeat(513), 0)
        assertTrue(memory.entries().isEmpty())
        memory.record("valid", "key", -20)
        assertEquals(0, memory.get("valid")?.index)
    }
    @Test fun newIntentReplacesOldIntentAndUserInputCancelsIt() {
        val focus = ContextFocus(FocusBookmarks())
        focus.request("films"); val first = focus.pending
        focus.request("settings"); assertNotEquals(first, focus.pending)
        focus.cancel(); assertNull(focus.pending)
        focus.request("films"); focus.record("controls", "query", 1)
        assertNull(focus.pending)
    }
    @Test fun pendingIntentKeepsItsOriginalDestinationDespiteLazyFocusHandoff() {
        val focus = ContextFocus(FocusBookmarks())
        focus.record("films", "original", 2)
        focus.request("films")
        focus.bookmarks.record("films", "temporary", 8)
        assertEquals(2, FocusBookmarks.resolve(focus.pending?.place, listOf("a", "b", "original", "temporary")))
    }

}