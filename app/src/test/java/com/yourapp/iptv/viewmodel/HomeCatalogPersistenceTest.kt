package com.yourapp.iptv.viewmodel

import com.yourapp.iptv.domain.organization.ChannelOrganizer
import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeCatalogPersistenceTest {
    @Test fun queuedSavesDoNotSurviveProfileChangesIncludingReturningToTheOriginalProfile() = runTest {
        val a = CATALOG_A.copy(profileId = "11111111-1111-4111-8111-111111111111")
        val b = CATALOG_A.copy(profileId = "22222222-2222-4222-8222-222222222222")
        val lists = HomeCatalogLists();val startup = HomeCatalogSnapshots();val target = HomeCatalogTarget()
        startup.read = { catalogSnapshot(it,it.profileId,1000) }
        val controller = HomeCatalogController(backgroundScope,lists,startup,StandardTestDispatcher(testScheduler),target) { 1000 }
        val saved = catalogSnapshot(a,"owner",1000)
        controller.bind(a);runCurrent();controller.save(saved.config,saved.catalog)
        target.clear();controller.bind(b);runCurrent();controller.save(saved.config,saved.catalog)
        target.clear();controller.bind(a);runCurrent()
        assertTrue(startup.writes.isEmpty())
        controller.save(saved.config,saved.catalog);runCurrent()
        assertEquals(a,startup.writes.single().source);assertEquals(1000L,startup.writes.single().refreshedAt)
        assertTrue(lists.calls.isEmpty())
    }

    @Test fun unbindingCancelsAnInFlightOptionalSaveAndTheNextOwnerCanStillSave() = runTest {
        val lists = HomeCatalogLists();val startup = HomeCatalogSnapshots();val target = HomeCatalogTarget()
        var entered = false;var cancelled = false
        startup.read = { catalogSnapshot(it,"saved",1000) }
        startup.beforeWrite = { entered = true;try { awaitCancellation() } finally { cancelled = true } }
        val controller = HomeCatalogController(backgroundScope,lists,startup,StandardTestDispatcher(testScheduler),target) { 1000 }
        val a = catalogSnapshot(CATALOG_A);val b = catalogSnapshot(CATALOG_B)
        controller.bind(CATALOG_A);runCurrent();controller.save(a.config,a.catalog);runCurrent()
        assertTrue(entered);controller.bind(null);runCurrent()
        assertTrue(cancelled);assertTrue(startup.writes.isEmpty())
        startup.beforeWrite = {};target.clear();controller.bind(CATALOG_B);runCurrent()
        controller.save(b.config,b.catalog);runCurrent()
        assertEquals(CATALOG_B,startup.writes.single().source)
    }

    @Test fun aCacheWriteFailureDoesNotBreakTheNextRefreshOrItsVerifiedTimestamp() = runTest {
        val lists = HomeCatalogLists();val startup = HomeCatalogSnapshots();val target = HomeCatalogTarget()
        var now = 1000L;val saved = catalogSnapshot(CATALOG_A,"saved",now)
        startup.read = { saved };startup.beforeWrite = { throw IOException("Synthetic cache write failure") }
        val controller = HomeCatalogController(backgroundScope,lists,startup,StandardTestDispatcher(testScheduler),target) { now }
        controller.bind(CATALOG_A);runCurrent();controller.save(saved.config,saved.catalog);runCurrent()
        assertTrue(target.failed.isEmpty());assertTrue(startup.writes.isEmpty())
        startup.beforeWrite = {};now = 2000L;assertTrue(controller.refresh());runCurrent()
        controller.save(saved.config,ChannelOrganizer.project(target.shown,saved.config));runCurrent()
        assertEquals(2000L,startup.writes.single().refreshedAt)
        assertEquals("network",startup.writes.single().catalog.channels.single().id)
        assertFalse(controller.isRefreshing);assertTrue(target.failed.isEmpty())
    }
}