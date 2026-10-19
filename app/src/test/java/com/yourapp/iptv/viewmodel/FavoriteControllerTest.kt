package com.yourapp.iptv.viewmodel

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FavoriteControllerTest {
    @Test fun observesExistingIdsAndIgnoresDuplicatePressesWhileSaving() = runTest {
        val repository = FakeFavoriteRepository(setOf("a"))
        var ids = emptySet<String>()
        val controller = FavoriteController(backgroundScope, repository) { ids = it }
        runCurrent()
        assertEquals(setOf("a"), ids)
        controller.toggle("b")
        controller.toggle("b")
        assertTrue(controller.uiState.value.isSaving)
        advanceTimeBy(11)
        runCurrent()
        assertEquals(1, repository.writes)
        assertEquals(setOf("a", "b"), ids)
        assertFalse(controller.uiState.value.isSaving)
    }

    @Test fun readFailureCanBeRetriedAndNeverErasesStoredIds() = runTest {
        val repository = FakeFavoriteRepository(setOf("a")).apply { failRead = true }
        var ids: Set<String>? = null
        val controller = FavoriteController(backgroundScope, repository) { ids = it }
        runCurrent()
        assertNull(ids)
        assertFalse(controller.uiState.value.canToggle)
        assertFalse(controller.uiState.value.errorMessage!!.contains("private-read-details"))
        controller.toggle("a")
        assertEquals(0, repository.writes)
        repository.failRead = false
        controller.retry()
        runCurrent()
        assertEquals(setOf("a"), ids)
        assertTrue(controller.uiState.value.canToggle)
        assertNull(controller.uiState.value.errorMessage)
    }

    @Test fun cancellingTheOwnerStopsObserving() = runTest {
        val repository = FakeFavoriteRepository()
        val owner = kotlinx.coroutines.CoroutineScope(coroutineContext + kotlinx.coroutines.Job())
        var calls = 0
        FavoriteController(owner, repository) { calls++ }
        advanceUntilIdle()
        owner.cancel()
        repository.ids.value = setOf("a")
        advanceUntilIdle()
        assertEquals(1, calls)
    }
}