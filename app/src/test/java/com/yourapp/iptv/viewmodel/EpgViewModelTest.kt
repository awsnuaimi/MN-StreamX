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
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EpgViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { Dispatchers.resetMain() }

    @Test fun loadsStoredChannelsOnceWithoutImportingThePlaylistAgain() = runTest(dispatcher) {
        val repository = FakeChannels()
        val model = EpgViewModel(SavedStateHandle(mapOf("channelId" to "b")), repository, dispatcher, FakeFavoriteRepository())
        advanceUntilIdle()
        assertEquals("b", model.uiState.value.channel?.id)
        repeat(50) { model.selectChannel("a"); model.selectChannel("b") }
        assertEquals(1, repository.loads)
        assertEquals(listOf("News", "Sports"), model.uiState.value.groups)
    }

    @Test fun inheritsTheHomeCategorySearchAndSelection() = runTest(dispatcher) {
        val model = EpgViewModel(SavedStateHandle(mapOf("channelId" to "b", "group" to "News",
            "query" to " NEWS ")), FakeChannels(), dispatcher, FakeFavoriteRepository())
        advanceUntilIdle()
        assertEquals(listOf("a", "b"), model.uiState.value.queue.channels.map { it.id })
        assertEquals("b", model.uiState.value.channel?.id)
        model.selectChannel("c")
        assertEquals("b", model.uiState.value.channel?.id)
    }

    @Test fun channelAndProgramSelectionSurviveRecreationAndSameChannelReturns() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        val repository = FakeChannels()
        val first = EpgViewModel(saved, repository, dispatcher, FakeFavoriteRepository())
        advanceUntilIdle()
        first.selectChannel("b")
        first.selectProgram("1700000000000:1700003600000")
        first.restoreChannel("b")
        assertNotNull(first.uiState.value.selectedProgramKey)
        val restored = EpgViewModel(saved, repository, dispatcher, FakeFavoriteRepository())
        advanceUntilIdle()
        assertEquals("b", restored.uiState.value.channel?.id)
        assertEquals("1700000000000:1700003600000", restored.uiState.value.selectedProgramKey)
        restored.restoreChannel("c")
        assertEquals("c", restored.uiState.value.channel?.id)
        assertNull(restored.uiState.value.selectedProgramKey)
    }

    @Test fun aPlayerReturnBeforeTheDatabaseReadCompletesUsesTheReturnedChannel() = runTest(dispatcher) {
        val saved = SavedStateHandle(mapOf("channelId" to "a", "epgProgramKey" to "previous-program"))
        val model = EpgViewModel(saved, FakeChannels(), dispatcher, FakeFavoriteRepository())
        model.restoreChannel("c")
        advanceUntilIdle()
        assertEquals("c", model.uiState.value.channel?.id)
        assertNull(model.uiState.value.selectedProgramKey)
    }

    @Test fun rapidFilterChangesUseTheFinalQueryAndCanBeClearedAfterNoResults() = runTest(dispatcher) {
        val model = EpgViewModel(SavedStateHandle(), FakeChannels(), dispatcher, FakeFavoriteRepository())
        advanceUntilIdle()
        model.updateQuery("Beta")
        model.updateQuery("missing")
        model.selectGroup("Sports")
        advanceUntilIdle()
        assertNull(model.uiState.value.channel)
        assertEquals(0, model.uiState.value.queue.size)
        model.clearFilters()
        advanceUntilIdle()
        assertEquals(3, model.uiState.value.queue.size)
        assertNotNull(model.uiState.value.channel)
        assertNull(model.uiState.value.selectedGroup)
        assertEquals("", model.uiState.value.query)
    }

    @Test fun queriesEnteredDuringInitialLoadingAreNotLost() = runTest(dispatcher) {
        val model = EpgViewModel(SavedStateHandle(), FakeChannels(), dispatcher, FakeFavoriteRepository())
        model.updateQuery("Beta")
        advanceUntilIdle()
        assertEquals("b", model.uiState.value.channel?.id)
        assertEquals(1, model.uiState.value.queue.size)
    }

    @Test fun missingChannelsOrCategoriesFallBackAndDuplicateIdsDoNotBreakLazyKeys() = runTest(dispatcher) {
        val repository = FakeChannels()
        repository.items = repository.items + repository.items.first().copy(name = "Duplicate")
        val model = EpgViewModel(SavedStateHandle(mapOf("channelId" to "gone", "group" to "gone")),
            repository, dispatcher, FakeFavoriteRepository())
        advanceUntilIdle()
        assertEquals(3, model.uiState.value.queue.size)
        assertEquals("a", model.uiState.value.channel?.id)
        assertNull(model.uiState.value.selectedGroup)
    }

    @Test fun readFailuresHaveASafeErrorAndRetryOnlyReadsTheLocalChannels() = runTest(dispatcher) {
        val repository = FakeChannels().apply { fail = true }
        val model = EpgViewModel(SavedStateHandle(), repository, dispatcher, FakeFavoriteRepository())
        advanceUntilIdle()
        assertFalse(model.uiState.value.isLoading)
        assertNotNull(model.uiState.value.errorMessage)
        assertFalse(model.uiState.value.errorMessage!!.contains("private-error"))
        repository.fail = false
        model.reload()
        advanceUntilIdle()
        assertNull(model.uiState.value.errorMessage)
        assertEquals(3, model.uiState.value.queue.size)
        assertEquals(2, repository.loads)
    }

    @Test fun anEmptyLocalLibraryStopsLoadingWithoutSelectingAnInvalidChannel() = runTest(dispatcher) {
        val repository = FakeChannels().apply { items = emptyList() }
        val model = EpgViewModel(SavedStateHandle(), repository, dispatcher, FakeFavoriteRepository())
        advanceUntilIdle()
        assertFalse(model.uiState.value.isLoading)
        assertFalse(model.uiState.value.isFiltering)
        assertNull(model.uiState.value.channel)
        assertEquals(-1, model.uiState.value.selectedIndex)
    }

    private class FakeChannels : ChannelRepository {
        var loads = 0
        var fail = false
        var items = listOf(Channel("a", "Alpha News", streamUrl = "https://unit.test/a", group = "News"),
            Channel("b", "Beta News", streamUrl = "https://unit.test/b", group = "News"),
            Channel("c", "Sports", streamUrl = "https://unit.test/c", group = "Sports"))
        override suspend fun getChannels(): List<Channel> {
            loads++; delay(40)
            if (fail) error("private-error")
            return items
        }
        override suspend fun getChannel(id: String): Channel? = error("Unexpected lookup")
        override suspend fun loadFromM3u(url: String): List<Channel> = error("Unexpected import")
        override suspend fun loadFromXtream(serverUrl: String, username: String, password: String): List<Channel> =
            error("Unexpected import")
        override suspend fun search(query: String): List<Channel> = error("Unexpected search")
        override suspend fun getFavorites(): List<Channel> = emptyList()
        override suspend fun addFavorite(channel: Channel) = Unit
        override suspend fun removeFavorite(channel: Channel) = Unit
    }
}