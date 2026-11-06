package mnstudio.apps.mnstreamx.ui.component.common

import org.junit.Assert.*
import org.junit.Test

/** A stable lazy-grid index must avoid O(catalog size) scans during DPAD navigation. */
class VodFocusLoadStressTest {
    @Test fun tenThousandUnchangedRestoresRequireConstantIndexedReads() {
        var reads = 0
        val keys = object : AbstractList<String>() {
            override val size = 60_000
            override fun get(index: Int): String {
                reads++
                return "poster-$index"
            }
        }
        val saved = FocusBookmarks.Place("poster-43210", 43210)
        repeat(10_000) { assertEquals(43210, FocusBookmarks.resolve(saved, keys)) }
        assertTrue("Restoration scanned the library ($reads indexed reads)", reads <= 20_000)
    }

    @Test fun reorderedOrRemovedItemsRespectIdentityAndNearestSlot() {
        val keys = (0 until 60_000).map { "poster-$it" }
        val bookmarks = FocusBookmarks()
        bookmarks.record("movies:category-1", "poster-50000", 50000)
        val swapped = keys.toMutableList().apply {
            this[100] = "poster-50000"
            this[50000] = "poster-100"
        }
        assertEquals(100, bookmarks.index("movies:category-1", swapped))
        val removed = swapped.filterNot { it == "poster-50000" }
        assertEquals(50000, bookmarks.index("movies:category-1", removed))
    }

    @Test fun manyAccountContextsStayBoundedAndRemainIndependent() {
        val bookmarks = FocusBookmarks()
        repeat(200) { index ->
            bookmarks.record("movies:account-$index", "item-$index", index)
            assertTrue(bookmarks.entries().size <= FocusBookmarks.LIMIT)
        }
        assertNull(bookmarks.get("movies:account-0"))
        assertEquals("item-199", bookmarks.get("movies:account-199")?.key)
        assertNull(bookmarks.get("series:account-199"))
        assertEquals(FocusBookmarks.LIMIT, bookmarks.entries().size)
    }
}