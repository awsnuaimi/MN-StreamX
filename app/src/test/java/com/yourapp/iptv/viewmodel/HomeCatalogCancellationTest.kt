package com.yourapp.iptv.viewmodel

import com.yourapp.iptv.domain.model.*
import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeCatalogCancellationTest {
    @Test fun anUnboundOwnerCannotReceiveALateStartupSnapshotOrQueueItsRefresh() = runTest {
        val lists = HomeCatalogLists();val startup = HomeCatalogSnapshots();val target = HomeCatalogTarget()
        val pending = CompletableDeferred<HomeStartupSnapshot?>()
        startup.read = { withContext(NonCancellable) { pending.await() } }
        val controller = HomeCatalogController(backgroundScope,lists,startup,StandardTestDispatcher(testScheduler),target) { 1000 }
        try {
            controller.bind(CATALOG_A);runCurrent();controller.bind(null);runCurrent()
            pending.complete(catalogSnapshot(CATALOG_A));runCurrent()
            controller.onHomeReady();runCurrent()
            assertTrue(target.restored.isEmpty());assertTrue(target.shown.isEmpty())
            assertTrue(lists.calls.isEmpty());assertFalse(controller.isRefreshing);assertFalse(controller.refresh())
        } finally { pending.complete(null) }
    }

    @Test fun anUnboundOwnerCannotReceiveALateRawCacheOrStartItsNetworkRequest() = runTest {
        val lists = HomeCatalogLists();val startup = HomeCatalogSnapshots();val target = HomeCatalogTarget()
        val pending = CompletableDeferred<Playlist?>()
        lists.cached = { withContext(NonCancellable) { pending.await() } }
        val controller = HomeCatalogController(backgroundScope,lists,startup,StandardTestDispatcher(testScheduler),target)
        try {
            controller.bind(CATALOG_A);runCurrent();controller.bind(null);runCurrent()
            pending.complete(Playlist("late",listOf(catalogRow("old"))));runCurrent()
            assertTrue(target.shown.isEmpty());assertTrue(lists.calls.isEmpty());assertFalse(controller.isRefreshing)
        } finally { pending.complete(null) }
    }

    @Test fun lateNetworkSuccessCannotReplaceTheNewOwner() = lateNetwork(false)
    @Test fun lateNetworkFailureCannotReplaceTheNewOwnersStatus() = lateNetwork(true)
    private fun lateNetwork(fail: Boolean) = runTest {
        val lists = HomeCatalogLists();val startup = HomeCatalogSnapshots();val target = HomeCatalogTarget()
        val pending = CompletableDeferred<Playlist>()
        lists.network = { withContext(NonCancellable) { pending.await() } }
        startup.read = { if (it == CATALOG_B) catalogSnapshot(it,"new",1000) else null }
        val controller = HomeCatalogController(backgroundScope,lists,startup,StandardTestDispatcher(testScheduler),target) { 1000 }
        try {
            controller.bind(CATALOG_A);runCurrent();target.clear();controller.bind(CATALOG_B);runCurrent()
            assertEquals("new",target.shown.single().id)
            if (fail) pending.completeExceptionally(IOException("Synthetic old network failure"))
            else pending.complete(Playlist("old",listOf(catalogRow("old"))))
            runCurrent()
            assertEquals("new",target.shown.single().id);assertTrue(target.failed.isEmpty())
            assertEquals(listOf(CATALOG_A.m3uUrl),lists.calls);assertFalse(controller.isRefreshing)
        } finally { pending.complete(Playlist("released",emptyList())) }
    }

    @Test fun repeatedReadinessSignalsStartOneDeferredRefresh() = runTest {
        val lists = HomeCatalogLists();val startup = HomeCatalogSnapshots();val target = HomeCatalogTarget()
        val pending = CompletableDeferred<Playlist>()
        startup.read = { catalogSnapshot(it) };lists.network = { pending.await() }
        val controller = HomeCatalogController(backgroundScope,lists,startup,StandardTestDispatcher(testScheduler),target) { 1000 }
        try {
            controller.bind(CATALOG_A);runCurrent();repeat(100) { controller.onHomeReady() };runCurrent()
            assertEquals(listOf(CATALOG_A.m3uUrl),lists.calls);assertTrue(controller.isRefreshing)
            pending.complete(Playlist("fresh",listOf(catalogRow("fresh"))));runCurrent()
            assertEquals("fresh",target.shown.single().id);assertFalse(controller.isRefreshing)
        } finally { pending.complete(Playlist("released",emptyList())) }
    }
}