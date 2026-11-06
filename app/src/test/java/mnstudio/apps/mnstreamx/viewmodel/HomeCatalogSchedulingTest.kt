package mnstudio.apps.mnstreamx.viewmodel

import mnstudio.apps.mnstreamx.domain.model.Playlist
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeCatalogSchedulingTest {
    @Test fun rawCacheDrawsBeforeAnyNetworkRefreshOrOptionalSnapshotWrite() = runTest {
        val lists = HomeCatalogLists().apply { cached = { Playlist("cache", listOf(catalogRow("cached"))) } }
        val startup = HomeCatalogSnapshots();val target = HomeCatalogTarget()
        val controller = HomeCatalogController(backgroundScope,lists,startup,StandardTestDispatcher(testScheduler),target)
        controller.bind(CATALOG_A);runCurrent()
        assertEquals("cached", target.shown.single().id)
        assertTrue(lists.calls.isEmpty());assertFalse(controller.isRefreshing)
        val saved = catalogSnapshot(CATALOG_A)
        controller.save(saved.config,saved.catalog);runCurrent()
        assertTrue(startup.writes.isEmpty())
        controller.onHomeReady();runCurrent()
        assertEquals(listOf(CATALOG_A.m3uUrl), lists.calls)
        assertEquals("network", target.shown.single().id)
        assertEquals(1, startup.writes.size)
        assertEquals(0L, startup.writes.single().refreshedAt)
    }

    @Test fun inactiveHomeDefersOptionalWorkAndResumesOnlyTheLatestSave() = runTest {
        val lists = HomeCatalogLists();val startup = HomeCatalogSnapshots();val target = HomeCatalogTarget()
        startup.read = { catalogSnapshot(it) }
        val controller = HomeCatalogController(backgroundScope,lists,startup,StandardTestDispatcher(testScheduler),target)
        controller.bind(CATALOG_A);runCurrent();controller.setActive(false)
        controller.onHomeReady()
        val first = catalogSnapshot(CATALOG_A,"old");val latest = catalogSnapshot(CATALOG_A,"latest")
        controller.save(first.config,first.catalog);controller.save(latest.config,latest.catalog);runCurrent()
        assertTrue(startup.writes.isEmpty());assertTrue(lists.calls.isEmpty())
        controller.setActive(true);runCurrent()
        assertEquals("latest", startup.writes.single().catalog.channels.single().id)
        assertEquals(1, lists.calls.size)
    }

    @Test fun manualRefreshDoesNotWaitForADrawSignal() = runTest {
        val lists = HomeCatalogLists().apply { cached = { Playlist("cache", listOf(catalogRow("cached"))) } }
        val controller = HomeCatalogController(backgroundScope,lists,HomeCatalogSnapshots(),StandardTestDispatcher(testScheduler),HomeCatalogTarget())
        controller.bind(CATALOG_A);runCurrent()
        assertTrue(lists.calls.isEmpty())
        controller.refresh();runCurrent()
        assertEquals(1, lists.calls.size)
    }

    @Test fun switchingSourcesDropsBothDeferredWritesAndRefreshes() = runTest {
        val lists = HomeCatalogLists();val startup = HomeCatalogSnapshots();val target = HomeCatalogTarget()
        startup.read = { catalogSnapshot(it,refreshedAt = 1000) }
        val controller = HomeCatalogController(backgroundScope,lists,startup,StandardTestDispatcher(testScheduler),target) { 1000 }
        controller.bind(CATALOG_A);runCurrent()
        val first = catalogSnapshot(CATALOG_A)
        controller.save(first.config,first.catalog)
        target.clear();controller.bind(CATALOG_B);runCurrent()
        controller.onHomeReady();runCurrent()
        assertTrue(startup.writes.isEmpty());assertTrue(lists.calls.isEmpty())
    }
}