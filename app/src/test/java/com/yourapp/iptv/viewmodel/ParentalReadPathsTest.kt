package com.yourapp.iptv.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import com.yourapp.iptv.data.parental.*
import com.yourapp.iptv.data.repository.PlaylistRepositoryImpl
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.domain.parental.*
import com.yourapp.iptv.domain.repository.*
import com.yourapp.iptv.testing.SearchAccounts
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class ParentalReadPathsTest {
    private val owner = ViewModelStore()
    private val jobs = mutableListOf<Job>()
    private val settings = SearchAccounts()
    private val raw = ParentalChannels()
    private val access = Access(ParentalState(ready = true,configured = true,
        rules = ParentalRules(groups = mapOf(settings.key to setOf("Protected")))))
    private val channels = ProtectedChannels(raw,settings,access)
    private val favorites = object : FavoriteRepository {
        override fun observeFavoriteIds(sourceKey: String?) = flowOf(setOf("1","2"))
        override suspend fun toggleFavorite(channelId: String, sourceKey: String?) = Unit
        override suspend fun setFavorite(channelId: String, selected: Boolean, sourceKey: String?) = Unit
    }
    private val history = object : WatchHistoryRepository {
        override fun observe(sourceKey: String) = flowOf(listOf(WatchHistoryEntry("2",200),WatchHistoryEntry("1",100)))
        override fun recordStarted(sourceKey: String, entry: WatchHistoryEntry) = Unit
        override suspend fun remove(sourceKey: String, channelId: String) = Unit
        override suspend fun clear(sourceKey: String) = Unit
    }
    @Before fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun cleanup() {
        owner.clear()
        try { runBlocking { withTimeout(5000) { jobs.forEach { it.join() } } } }
        finally { Dispatchers.resetMain() }
    }
    private fun <T : ViewModel> keep(model: T) = model.also {
        owner.put(java.util.UUID.randomUUID().toString(),it);jobs.add(requireNotNull(it.viewModelScope.coroutineContext[Job]))
    }
    @Test fun homeFavoritesAndDashboardProjectionsContainOnlyAccessibleChannels() = runBlocking {
        val model = keep(HomeViewModel(settings,PlaylistRepositoryImpl(channels),Dispatchers.Default,favorites,parental = access))
        val state = withTimeout(5000) { model.uiState.first { !it.isLoading && it.channels.isNotEmpty() } }
        assertEquals(listOf("1"),state.channels.map { it.id });model.toggleFavoritesFilter()
        val recent = HomeDashboardSelection.project(state.channels,listOf("2","1"))
        assertEquals(listOf("1"),recent.recent.map { it.id });assertEquals(listOf("1"),recent.favorites.map { it.id })
    }
    @Test fun directPlayerRouteCannotOpenTheLockedIdAndItsZappingQueueExcludesIt() = runBlocking {
        val model = keep(PlayerViewModel(SavedStateHandle(mapOf("channelId" to "2")),channels,Dispatchers.Default,
            favorites,history = history,settings = settings,parental = access))
        val state = withTimeout(5000) { model.uiState.first { !it.isLoading && it.queue.size == 1 } }
        assertNull(state.channel);assertEquals(listOf("1"),state.queue.channels.map { it.id })
    }
    @Test fun guideAndHistoryResolveTheirMetadataThroughTheSameProtectedRepository() = runBlocking {
        val guide = keep(EpgViewModel(SavedStateHandle(),channels,Dispatchers.Default,favorites,settings = settings,parental = access))
        val guideState = withTimeout(5000) { guide.uiState.first { !it.isLoading && !it.isFiltering && it.queue.size > 0 } }
        assertEquals(listOf("1"),guideState.queue.channels.map { it.id })
        val model = keep(WatchHistoryViewModel(settings,history,channels,Dispatchers.Default))
        val state = withTimeout(5000) { model.uiState.first { !it.isLoading && it.rows.isNotEmpty() } }
        assertEquals(listOf("1"),state.rows.map { it.channel.id });assertNull(model.playbackChannel(settings.key,"2"))
    }
}