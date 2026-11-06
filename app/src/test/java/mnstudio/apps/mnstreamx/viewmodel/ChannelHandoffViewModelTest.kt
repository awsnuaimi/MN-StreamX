package mnstudio.apps.mnstreamx.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import mnstudio.apps.mnstreamx.domain.model.*
import mnstudio.apps.mnstreamx.domain.parental.*
import mnstudio.apps.mnstreamx.domain.playback.ChannelPlaybackHandoff
import mnstudio.apps.mnstreamx.domain.profile.ProfileScope
import mnstudio.apps.mnstreamx.domain.repository.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class ChannelHandoffViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val owner = ViewModelStore()
    private val account = AppSettings(m3uUrl = "https://synthetic.invalid/list")
    private val row = Channel("1","Synthetic channel",streamUrl = "synthetic:selected")
    private val settings = object : SettingsRepository {
        override fun observeSettings() = flowOf(account)
        override suspend fun saveSettings(settings: AppSettings) = Unit
    }
    private val playlists = object : PlaylistRepository {
        override suspend fun loadPlaylist(url: String) = Playlist("Synthetic",listOf(row))
        override suspend fun loadXtream(serverUrl: String,username: String,password: String) = loadPlaylist(serverUrl)
    }
    private val access = MutableStateFlow(ParentalControl.Disabled.state.value)
    private val parental = object : ParentalControl by ParentalControl.Disabled { override val state = access }
    private val handoff = ChannelPlaybackHandoff { 0 }
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { owner.clear();Dispatchers.resetMain() }
    private fun home() = HomeViewModel(settings,playlists,dispatcher,FakeFavoriteRepository(),
        parental = parental,playbackHandoff = handoff).also { owner.put("home",it) }

    @Test fun clickingAVisibleChannelReachesThePlayerBeforeDatabaseAndQueueWork() = runTest(dispatcher) {
        val home = home();runCurrent()
        assertTrue(home.preparePlayback(row.id,home.uiState.value.browseScope))
        var lookups = 0;val queue = CompletableDeferred<List<Channel>>()
        val repository = object : ChannelRepository {
            override suspend fun getPlaybackChannel(source: AppSettings,id: String): Channel? { lookups++;error("Unexpected database read") }
            override suspend fun getCachedChannels(source: AppSettings) = queue.await()
            override suspend fun getChannels() = queue.await()
            override suspend fun getChannel(id: String): Channel? = null
            override suspend fun loadFromM3u(url: String) = emptyList<Channel>()
            override suspend fun loadFromXtream(serverUrl: String,username: String,password: String) = emptyList<Channel>()
            override suspend fun search(query: String) = emptyList<Channel>()
            override suspend fun getFavorites() = emptyList<Channel>()
            override suspend fun addFavorite(channel: Channel) = Unit
            override suspend fun removeFavorite(channel: Channel) = Unit
        }
        val player = PlayerViewModel(SavedStateHandle(mapOf("channelId" to row.id,"sourceKey" to ProfileScope.of(account))),
            repository,dispatcher,FakeFavoriteRepository(),settings = settings,parental = parental,playbackHandoff = handoff)
            .also { owner.put("player",it) }
        runCurrent()
        assertEquals(row.streamUrl,player.uiState.value.channel?.streamUrl)
        assertFalse(player.uiState.value.isLoading);assertEquals(0,lookups)
        assertEquals(0,player.uiState.value.queue.size)
        queue.complete(listOf(row,row.copy(id = "2")));runCurrent()
        assertEquals(2,player.uiState.value.queue.size)
        player.nextChannel();assertEquals("2",player.uiState.value.channel?.id)
    }
    @Test fun staleClicksUnknownChannelsAndChangedAccessDoNotCreateAPlaybackHandoff() = runTest(dispatcher) {
        val home = home();runCurrent()
        val key = home.uiState.value.browseScope
        assertFalse(home.preparePlayback(row.id,"stale-owner"))
        assertFalse(home.preparePlayback("missing",key))
        access.value = access.value.copy(profileId = "11111111-1111-4111-8111-111111111111",epoch = 1)
        assertFalse(home.preparePlayback(row.id,key))
        assertNull(handoff.take(key,row.id,1))
    }
}