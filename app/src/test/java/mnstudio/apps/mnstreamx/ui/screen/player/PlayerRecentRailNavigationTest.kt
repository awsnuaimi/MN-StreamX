package mnstudio.apps.mnstreamx.ui.screen.player

import mnstudio.apps.mnstreamx.domain.model.Channel
import mnstudio.apps.mnstreamx.domain.model.WatchedChannel
import org.junit.Assert.*
import org.junit.Test

class PlayerRecentRailNavigationTest {
    private fun row(id: Int) = WatchedChannel(Channel(id.toString(), "Channel $id", streamUrl = "https://test.invalid/$id"), id.toLong())

    @Test fun limitIsTwentyAndDuplicateChannelsAreRemoved() {
        val entries = RecentRailNavigation.entries((1..27).map(::row) + row(1))
        assertEquals(20, entries.size)
        assertEquals((1..20).map(Int::toString), entries.map { it.channel.id })
        assertEquals(2, RecentRailNavigation.entries(listOf(row(1), row(1), row(2))).size)
    }
    @Test fun defaultHighlightAllowsOneClickReturnToPreviousChannel() {
        val ids = listOf("current", "previous", "older")
        assertEquals("previous", RecentRailNavigation.initial(ids, "current"))
        assertEquals("current", RecentRailNavigation.initial(ids, "other"))
    }
    @Test fun moveIsBoundedAndDoesNotWrapAtEdges() {
        val ids = listOf("1", "2", "3")
        assertEquals("1", RecentRailNavigation.move(ids, "1", "3", -1))
        assertEquals("3", RecentRailNavigation.move(ids, "3", "1", 1))
        assertEquals("3", RecentRailNavigation.move(ids, "2", "1", 1))
        assertEquals("1", RecentRailNavigation.move(ids, "2", "1", -1))
    }
    @Test fun emptyAndOneChannelStaySafe() {
        assertNull(RecentRailNavigation.initial(emptyList(), "missing"))
        assertNull(RecentRailNavigation.move(emptyList(), null, "playing", 1))
        assertEquals("one", RecentRailNavigation.initial(listOf("one"), "one"))
        assertEquals("one", RecentRailNavigation.move(listOf("one"), null, "one", -1))
    }
    @Test fun removedSelectionFallsBackToExistingChannel() {
        val ids = listOf("now", "old")
        assertEquals("old", RecentRailNavigation.move(ids, "deleted", "now", 0))
        assertEquals("now", RecentRailNavigation.move(ids, "deleted", "now", -1))
    }
}