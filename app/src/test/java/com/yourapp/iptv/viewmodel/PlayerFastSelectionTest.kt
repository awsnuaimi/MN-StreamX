package com.yourapp.iptv.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.yourapp.iptv.domain.model.Channel
import com.yourapp.iptv.domain.repository.ChannelRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerFastSelectionTest {
    private val dispatcher = StandardTestDispatcher()
    private val owner = ViewModelStore()
    private val selected = Channel("selected", "Selected", streamUrl = "https://unit.test/live")

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { owner.clear(); Dispatchers.resetMain() }

    @Test fun selectedChannelIsAvailableBeforeTheFullQueueFinishesLoading() = runTest(dispatcher) {
        val repository = Rows(selected)
        val model = model(repository)
        runCurrent()
        assertEquals(selected.id, model.uiState.value.channel?.id)
        assertFalse(model.uiState.value.isLoading)
        assertTrue(model.uiState.value.channel!!.isFavorite)
        assertEquals(0, model.uiState.value.queue.size)
        repository.all.complete(listOf(selected, selected.copy(id = "next")))
        runCurrent()
        assertEquals(selected.streamUrl, model.uiState.value.channel?.streamUrl)
        assertEquals(2, model.uiState.value.queue.size)
        model.nextChannel()
        assertEquals("next", model.uiState.value.channel?.id)
    }

    @Test fun queueReadFailureDoesNotCoverAPlayableSelectedChannelWithAnError() = runTest(dispatcher) {
        val repository = Rows(selected)
        val model = model(repository)
        runCurrent()
        repository.all.completeExceptionally(IllegalStateException("Queue unavailable"))
        runCurrent()
        assertEquals(selected.id, model.uiState.value.channel?.id)
        assertNull(model.uiState.value.errorMessage)
        assertFalse(model.uiState.value.isLoading)
    }

    @Test fun failedFastLookupFallsBackToTheFullList() = runTest(dispatcher) {
        val repository = Rows(selected).apply { failLookup = true }
        val model = model(repository)
        runCurrent()
        assertNull(model.uiState.value.channel)
        repository.all.complete(listOf(selected))
        runCurrent()
        assertEquals(selected.id, model.uiState.value.channel?.id)
        assertNull(model.uiState.value.errorMessage)
    }

    private fun model(repository: Rows) = PlayerViewModel(SavedStateHandle(mapOf("channelId" to selected.id)),
        repository, dispatcher, FakeFavoriteRepository(setOf(selected.id))).also { owner.put("player", it) }

    private class Rows(private val selected: Channel) : ChannelRepository {
        val all = CompletableDeferred<List<Channel>>()
        var failLookup = false
        override suspend fun getPlaybackChannel(id: String): Channel? {
            if (failLookup) error("Fast lookup unavailable")
            return selected.takeIf { it.id == id }
        }
        override suspend fun getChannels() = all.await()
        override suspend fun getChannel(id: String) = error("Unexpected lookup")
        override suspend fun loadFromM3u(url: String) = error("Unexpected import")
        override suspend fun loadFromXtream(serverUrl: String, username: String, password: String) = error("Unexpected import")
        override suspend fun search(query: String) = error("Unexpected search")
        override suspend fun getFavorites() = emptyList<Channel>()
        override suspend fun addFavorite(channel: Channel) = Unit
        override suspend fun removeFavorite(channel: Channel) = Unit
    }
}