package com.yourapp.iptv.viewmodel

import com.yourapp.iptv.domain.repository.FavoriteRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScopedFavoriteControllerTest {
    private class Repository : FavoriteRepository {
        val values = MutableStateFlow(mapOf("A" to setOf("first"), "B" to setOf("second")))
        val writes = mutableListOf<String?>()
        override fun observeFavoriteIds(sourceKey: String?) = values.map { it[sourceKey].orEmpty() }
        override suspend fun toggleFavorite(channelId: String, sourceKey: String?) {
            writes += sourceKey
            withContext(NonCancellable) { delay(100) }
            val ids = values.value[sourceKey].orEmpty()
            values.value = values.value + (requireNotNull(sourceKey) to
                if (channelId in ids) ids - channelId else ids + channelId)
        }
        override suspend fun setFavorite(channelId: String, selected: Boolean, sourceKey: String?) {
            val key = requireNotNull(sourceKey)
            values.value = values.value + (key to if (selected) values.value[key].orEmpty() + channelId else values.value[key].orEmpty() - channelId)
        }
    }
    @Test fun rebindingImmediatelyDropsOldIdsAndObservesOnlyTheSelectedSubscription() = runTest {
        val repository = Repository(); var ids: Set<String>? = null
        val controller = FavoriteController(backgroundScope, repository, onReset = { ids = null }) { ids = it }
        controller.bind("A"); runCurrent(); assertEquals(setOf("first"), ids)
        controller.bind("B"); assertNull(ids); assertFalse(controller.uiState.value.canToggle)
        runCurrent(); assertEquals(setOf("second"), ids)
        repository.values.value = repository.values.value + ("A" to setOf("changed old account"))
        runCurrent(); assertEquals(setOf("second"), ids)
    }
    @Test fun aWriteAlreadyAcceptedForTheOldSubscriptionCannotToggleTheNewOneOrClearItsUiState() = runTest {
        val repository = Repository(); var ids = emptySet<String>()
        val controller = FavoriteController(backgroundScope, repository) { ids = it }
        controller.bind("A"); runCurrent(); controller.toggle("shared"); runCurrent()
        controller.bind("B"); runCurrent()
        assertEquals(setOf("second"), ids); assertFalse(controller.uiState.value.isSaving)
        advanceTimeBy(101); runCurrent()
        assertEquals(listOf("A"), repository.writes)
        assertEquals(setOf("first", "shared"), repository.values.value["A"])
        assertEquals(setOf("second"), repository.values.value["B"])
        assertEquals(setOf("second"), ids); assertTrue(controller.uiState.value.canToggle)
    }
}