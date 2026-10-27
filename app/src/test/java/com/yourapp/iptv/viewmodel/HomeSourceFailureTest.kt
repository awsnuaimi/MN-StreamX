package com.yourapp.iptv.viewmodel

import androidx.lifecycle.ViewModelStore
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.domain.profile.ProfileScope
import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeSourceFailureTest {
    private val dispatcher = StandardTestDispatcher()
    private val owner = ViewModelStore()
    private val settings = HomeCatalogSettings()
    private val lists = HomeCatalogLists()
    private val startup = HomeCatalogSnapshots()
    private val saved = catalogSnapshot(CATALOG_A)
    private val organization = OrganizationMemory(saved.config)
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { owner.clear();dispatcher.scheduler.runCurrent();Dispatchers.resetMain() }
    private fun model() = HomeViewModel(settings,lists,dispatcher,FakeFavoriteRepository(),organization,startup)
        .also { owner.put("home",it) }

    @Test fun sourceReadFailureInvalidatesTheDeferredRefreshBeforeAnyHomeReadyCallback() = runTest(dispatcher) {
        startup.read = { saved };val model = model();runCurrent()
        assertEquals("saved",model.uiState.value.visibleChannels.single().id)
        settings.changes.value = Result.failure(IOException("Synthetic settings failure"));runCurrent()
        repeat(20) { model.onHomeReady() };runCurrent()
        assertTrue(lists.calls.isEmpty())
        assertTrue(model.uiState.value.channels.isEmpty());assertTrue(model.uiState.value.visibleChannels.isEmpty())
        assertEquals("",model.uiState.value.browseScope);assertFalse(model.uiState.value.isRefreshing)
        assertNotNull(model.uiState.value.errorMessage)
    }

    @Test fun sourceReadFailureCancelsAnOptionalSaveAlreadyWaitingInTheStartupRepository() = runTest(dispatcher) {
        var entered = false;var cancelled = false
        startup.beforeWrite = { entered = true;try { awaitCancellation() } finally { cancelled = true } }
        val model = model();runCurrent();assertTrue(entered)
        settings.changes.value = Result.failure(IOException("Synthetic settings failure"));runCurrent()
        assertTrue(cancelled);assertTrue(startup.writes.isEmpty())
        assertTrue(model.uiState.value.channels.isEmpty());assertFalse(model.uiState.value.isLoading)
    }

    @Test fun retryAfterSourceFailureOpensOnlyTheNewOwnerWithoutRevivingTheOldDeferredRefresh() = runTest(dispatcher) {
        startup.read = { source -> if (source == CATALOG_A) saved else catalogSnapshot(source,"new",System.currentTimeMillis()) }
        val model = model();runCurrent()
        settings.changes.value = Result.failure(IOException("Synthetic settings failure"));runCurrent()
        model.onHomeReady();runCurrent()
        settings.changes.value = Result.success(CATALOG_B);model.refresh();runCurrent()
        repeat(20) { model.onHomeReady() };runCurrent()
        assertEquals(ProfileScope.of(CATALOG_B),model.uiState.value.browseScope)
        assertEquals("new",model.uiState.value.visibleChannels.single().id)
        assertNull(model.uiState.value.errorMessage);assertTrue(lists.calls.isEmpty())
    }
}