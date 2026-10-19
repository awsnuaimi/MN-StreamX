package com.yourapp.iptv.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.yourapp.iptv.domain.model.Channel
import com.yourapp.iptv.domain.repository.ChannelRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun switchingUsesLoadedChannelsWithoutReloadingOrImporting() = runTest(dispatcher) {
        val repository = FakeChannels()
        val saved = SavedStateHandle(mapOf("channelId" to "b"))
        val model = PlayerViewModel(saved, repository, dispatcher, FakeFavoriteRepository())
        model.nextChannel()
        advanceUntilIdle()
        assertEquals("b", model.uiState.value.channel?.id)
        repeat(100) { model.nextChannel() }
        assertEquals("c", model.uiState.value.channel?.id)
        assertEquals("c", saved.get<String>("channelId"))
        model.previousChannel()
        assertEquals("b", model.uiState.value.channel?.id)
        assertEquals(1, repository.loads)
    }

    @Test fun selectionStaysWithinTheHomeCategoryAndSearch() = runTest(dispatcher) {
        val model = PlayerViewModel(SavedStateHandle(mapOf(
            "channelId" to "b", "group" to "News", "query" to "news"
        )), FakeChannels(), dispatcher, FakeFavoriteRepository())
        advanceUntilIdle()
        assertEquals(listOf("a", "b"), model.uiState.value.queue.channels.map { it.id })
        model.nextChannel()
        assertEquals("a", model.uiState.value.channel?.id)
        model.previousChannel()
        assertEquals("b", model.uiState.value.channel?.id)
        model.selectChannel("c")
        assertEquals("b", model.uiState.value.channel?.id)
    }

    @Test fun restoredStateOpensTheLastSelectedChannel() = runTest(dispatcher) {
        val saved = SavedStateHandle(mapOf("channelId" to "a"))
        val repository = FakeChannels()
        val first = PlayerViewModel(saved, repository, dispatcher, FakeFavoriteRepository())
        advanceUntilIdle()
        first.selectChannel("c")
        val restored = PlayerViewModel(saved, repository, dispatcher, FakeFavoriteRepository())
        advanceUntilIdle()
        assertEquals("c", restored.uiState.value.channel?.id)
    }

    @Test fun missingChannelCanBeReplacedUsingTheChannelPanel() = runTest(dispatcher) {
        val model = PlayerViewModel(SavedStateHandle(mapOf("channelId" to "gone")), FakeChannels(), dispatcher, FakeFavoriteRepository())
        advanceUntilIdle()
        assertNull(model.uiState.value.channel)
        assertNotNull(model.uiState.value.errorMessage)
        model.nextChannel()
        assertNull(model.uiState.value.channel)
        model.selectChannel("a")
        assertEquals("a", model.uiState.value.channel?.id)
        assertNull(model.uiState.value.errorMessage)
    }

    @Test fun repositoryFailureEndsLoadingWithAReadableError() = runTest(dispatcher) {
        val model = PlayerViewModel(SavedStateHandle(mapOf("channelId" to "a")), FakeChannels(fail = true), dispatcher, FakeFavoriteRepository())
        advanceUntilIdle()
        assertFalse(model.uiState.value.isLoading)
        assertNotNull(model.uiState.value.errorMessage)
        assertNull(model.uiState.value.channel)
    }

    private class FakeChannels(private val fail: Boolean = false) : ChannelRepository {
        var loads = 0
        override suspend fun getChannels(): List<Channel> {
            loads++
            delay(20)
            if (fail) error("Synthetic read failure")
            return listOf(
                Channel("a", "Alpha News", streamUrl = "https://unit.test/a", group = "News"),
                Channel("b", "Beta News", streamUrl = "https://unit.test/b", group = "News"),
                Channel("c", "Sports", streamUrl = "https://unit.test/c", group = "Sports")
            )
        }
        override suspend fun getChannel(id: String): Channel? = error("Unexpected single-channel reload")
        override suspend fun loadFromM3u(url: String): List<Channel> = error("Unexpected network import")
        override suspend fun loadFromXtream(serverUrl: String, username: String, password: String): List<Channel> =
            error("Unexpected network import")
        override suspend fun search(query: String): List<Channel> = error("Unexpected search")
        override suspend fun getFavorites(): List<Channel> = emptyList()
        override suspend fun addFavorite(channel: Channel) = Unit
        override suspend fun removeFavorite(channel: Channel) = Unit
    }
}