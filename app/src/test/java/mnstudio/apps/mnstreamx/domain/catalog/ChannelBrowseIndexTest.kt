package mnstudio.apps.mnstreamx.domain.catalog

import mnstudio.apps.mnstreamx.domain.model.Channel
import mnstudio.apps.mnstreamx.domain.model.ChannelFavorites
import mnstudio.apps.mnstreamx.domain.model.PlaybackQueue
import mnstudio.apps.mnstreamx.domain.organization.*
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test

class ChannelBrowseIndexTest {
    private val source = List(600) { i -> Channel("$i", "Sender $i HD", streamUrl = "synthetic:$i",
        group = "Group ${i % 10}", folderIds = listOf("root", "folder-${i % 10}")) }
    private val tree = OrganizationTree(true, counts = mapOf("root" to source.size))

    @Test fun indexedFiltersMatchTheExistingQueueAndFolderCounts() {
        val ids = setOf("1", "22", "321")
        val marked = ChannelFavorites.apply(source, ids)
        val catalog = ChannelBrowseIndex().prepare("owner", source, tree, ids)
        for (group in listOf(null, "Group 1", FolderFilter.of("folder-2"), "missing")) {
            for (query in listOf("", "  Sender 2  ", "HD", "missing")) for (favorites in listOf(false, true)) {
                val result = catalog.select(group, query, favorites)
                assertEquals(PlaybackQueue.filterChannels(marked, group, query, favorites), result.visible)
                val matching = PlaybackQueue.filterChannels(marked, query = query, favoritesOnly = favorites)
                if (query.isBlank() && !favorites) assertSame(tree, result.tree)
                else assertEquals(tree.withCounts(matching).counts, result.tree.counts)
            }
        }
    }

    @Test fun categoryNavigationDoesNotReadTheWholeSourceAgain() {
        var reads = 0
        val watched = object : AbstractList<Channel>() {
            override val size get() = source.size
            override fun get(index: Int): Channel { reads++;return source[index] }
        }
        val worker = ChannelBrowseIndex()
        val catalog = worker.prepare("owner", watched, tree, emptySet())
        reads = 0
        val first = catalog.select("Group 1", "", false)
        catalog.select("Group 2", "", false)
        val again = worker.prepare("owner", watched, tree, emptySet()).select("Group 1", "", false)
        assertEquals(0, reads)
        assertSame(first.visible, again.visible)
        assertEquals(60, again.visible.size)
    }

    @Test fun favoriteEditsKeepOldSnapshotsAndUnaffectedListsIntact() {
        val worker = ChannelBrowseIndex()
        val catalog = worker.prepare("owner", source, tree, emptySet())
        val oldAll = catalog.channels
        val oldA = catalog.select("Group 1", "", false).visible
        val oldB = catalog.select("Group 2", "", false).visible
        worker.prepare("owner", source, tree, setOf("1", "321", "not-loaded"))
        val nextA = catalog.select("Group 1", "", false).visible
        assertFalse(oldAll[1].isFavorite)
        assertFalse(oldA.first().isFavorite)
        assertTrue(nextA.first().isFavorite)
        assertSame(oldB, catalog.select("Group 2", "", false).visible)
        assertSame(oldAll[0], catalog.channels[0])
        assertEquals(listOf("1", "321"), catalog.select(null, "", true).visible.map { it.id })
        assertEquals(2, catalog.select(null, "", true).tree.counts["root"])
        worker.prepare("owner", source, tree, setOf("22"))
        assertEquals(listOf("22"), catalog.select(null, "", true).visible.map { it.id })
        assertTrue(nextA.first().isFavorite)
    }

    @Test fun anotherProfileSourceMembershipOrTopologyGetsAFreshIndex() {
        val worker = ChannelBrowseIndex()
        val first = worker.prepare("account/profile-a", source, tree, setOf("1"))
        val second = worker.prepare("account/profile-b", source, tree, emptySet())
        assertNotSame(first, second);assertFalse(second.channels[1].isFavorite)
        val replacement = source.take(10).map { it.copy(streamUrl = "replacement:${it.id}", group = "New") }
        val third = worker.prepare("account/profile-b", replacement, tree, emptySet())
        assertTrue(third.select("Group 1", "", false).visible.isEmpty())
        assertEquals("replacement:1", third.channels[1].streamUrl)
        assertNotSame(third, worker.prepare("account/profile-b", replacement, OrganizationTree(), emptySet()))
    }

    @Test fun cancellationCannotMutatePublishedFavoritesOrPublishAPartialCatalog() {
        val worker = ChannelBrowseIndex()
        val first = worker.prepare("owner", source, tree, emptySet())
        val shown = first.channels
        var remaining = 3
        try {
            worker.prepare("owner", source, tree, source.mapTo(HashSet()) { it.id }) {
                if (--remaining == 0) throw CancellationException("synthetic cancellation")
            }
            fail("Expected cancellation")
        } catch (_: CancellationException) { }
        assertTrue(shown.none { it.isFavorite })
        assertTrue(first.channels.none { it.isFavorite })
        assertEquals(listOf("2"), worker.prepare("owner", source, tree, setOf("2"))
            .select(null, "", true).visible.map { it.id })
    }

    @Test fun incrementalSearchAndCacheEvictionKeepExactResults() {
        val catalog = ChannelBrowseIndex().prepare("owner", source, tree, emptySet())
        for (query in listOf("Sen", "Sender 2", "Sender 22", "HD", "sender 5", "", "Sender 2")) {
            assertEquals(PlaybackQueue.filterChannels(source, query = query), catalog.select(null, query, false).visible)
        }
        repeat(20) { i ->
            val group = "Group ${i % 10}"
            assertEquals(PlaybackQueue.filterChannels(source, group), catalog.select(group, "", false).visible)
        }
    }

    @Test fun streamMetadataDeltaPatchesOnlyAffectedViewsAndKeepsFavorites() {
        val worker = ChannelBrowseIndex()
        val first = worker.prepare("owner", source, tree, setOf("1"))
        val before = first.channels
        val unaffected = first.select("Group 2", "", false).visible
        val changed = source.map { if (it.id == "1") it.copy(streamUrl = "replacement:1") else it }
        val next = worker.prepare("owner", changed, tree, setOf("1"))
        assertSame(first,next)
        assertSame(unaffected,next.select("Group 2", "", false).visible)
        assertEquals("replacement:1",next.select("Group 1", "", false).visible.first().streamUrl)
        assertTrue(next.channels[1].isFavorite)
        assertEquals("synthetic:1",before[1].streamUrl)
        assertSame(before[0],next.channels[0])
    }
}