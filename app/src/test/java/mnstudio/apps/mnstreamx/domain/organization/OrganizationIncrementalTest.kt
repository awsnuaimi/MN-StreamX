package mnstudio.apps.mnstreamx.domain.organization

import mnstudio.apps.mnstreamx.domain.model.Channel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class OrganizationIncrementalTest {
    private val config = OrganizationConfig(sourceKey = "synthetic", enabled = true)
    private val rows = listOf(Channel("1", "TVP 2 HD", streamUrl = "old:1", group = "PL News"),
        Channel("2", "TVP 1 FHD", streamUrl = "old:2", group = "PL News"),
        Channel("3", "ZDF", streamUrl = "old:3", group = "DE News"))

    @Test fun unchangedRefreshReusesTheEntireVerifiedProjection() = runTest {
        val cache = OrganizationProjectionCache()
        val first = cache.project(rows, config) {}
        val repeated = cache.project(rows.map { it.copy() }, config) {}
        assertSame(first, repeated)
    }

    @Test fun streamAndLogoDeltaReusesAssignmentsAndUntouchedChannelObjects() = runTest {
        val cache = OrganizationProjectionCache()
        val first = cache.project(rows, config) {}
        val updated = rows.map { if (it.id == "1") it.copy(streamUrl = "new:1", logoUrl = "new:logo") else it.copy() }
        val second = cache.project(updated, config) {}
        assertSame(first.tree, second.tree);assertSame(first.assignments, second.assignments)
        assertSame(first.groups, second.groups)
        assertSame(first.channels.first { it.id == "2" }, second.channels.first { it.id == "2" })
        assertEquals("new:1", second.channels.first { it.id == "1" }.streamUrl)
        assertEquals(ChannelOrganizer.project(updated, config).channels, second.channels)
    }

    @Test fun changedNamesGroupsMembershipAndOrderingMatchAFreshProjection() = runTest {
        val cache = OrganizationProjectionCache()
        val snapshots = listOf(rows, rows.reversed(), rows.drop(1), rows + rows[0].copy(id = "4", name = "TVP 10 UHD"),
            rows.map { it.copy(group = "Unknown", name = "MBC ${it.id}") }, rows)
        snapshots.forEach { source ->
            val actual = cache.project(source, config) {}
            val expected = ChannelOrganizer.project(source, config)
            assertEquals(expected.channels, actual.channels)
            assertEquals(expected.tree.counts, actual.tree.counts)
            assertEquals(expected.tree.folders, actual.tree.folders)
            assertEquals(expected.assignments, actual.assignments)
        }
    }

    @Test fun aChangedGroupVoteCannotKeepOldLanguageAssignments() = runTest {
        val cache = OrganizationProjectionCache()
        val unknown = rows.map { it.copy(group = "Synthetic") }
        cache.project(unknown, config) {}
        val german = unknown.map { it.copy(name = "ZDF ${it.id}") }
        val changed = cache.project(german, config) {}
        assertTrue(changed.channels.all { "german" in it.folderIds })
    }
}