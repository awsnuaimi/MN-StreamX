package mnstudio.apps.mnstreamx.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import mnstudio.apps.mnstreamx.domain.model.*
import mnstudio.apps.mnstreamx.domain.parental.ParentalControl
import mnstudio.apps.mnstreamx.domain.parental.ParentalState
import mnstudio.apps.mnstreamx.domain.profile.ProfileScope
import mnstudio.apps.mnstreamx.domain.repository.LiveEpgRepository
import mnstudio.apps.mnstreamx.domain.repository.SettingsRepository
import mnstudio.apps.mnstreamx.ui.state.HomeGuideState
import mnstudio.apps.mnstreamx.ui.state.HomeProgramUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel as CoroutineChannel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject

/** Visible card guides only. Home and the browser use separate scoped instances; no video playback. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeGuideViewModel @Inject constructor(settings: SettingsRepository, repository: LiveEpgRepository,
    private val parental: ParentalControl) : ViewModel() {
    private data class Request(val channels: List<Channel>, val account: AppSettings, val access: ParentalState)
    private val selected = MutableStateFlow<List<Channel>>(emptyList())
    private val permits = Semaphore(2)
    val uiState = combine(selected, settings.observeSettings(), parental.state) { channels, account, access ->
        Request(channels, account, access)
    }.flatMapLatest { request ->
        val owner = ProfileScope.of(request.account)
        val source = PlaylistCacheKey.of(request.account)
        val access = request.access
        val allowed = request.channels.filter {
            request.account.hasSelectedSource() && access.profileId == request.account.profileId && access.allows(source, it)
        }
        channelFlow {
            send(HomeGuideState(owner, access.epoch))
            if (allowed.isEmpty()) return@channelFlow
            val rows = linkedMapOf<String, HomeProgramUiState>()
            val lock = Mutex()
            val limited = object : LiveEpgRepository {
                override suspend fun load(channel: Channel, settings: AppSettings): EpgSchedule? = permits.withPermit {
                    val before = parental.state.value
                    if (before.epoch != access.epoch || before.profileId != settings.profileId || !before.allows(source, channel)) return@withPermit null
                    val result = repository.load(channel, settings)
                    currentCoroutineContext().ensureActive()
                    val after = parental.state.value
                    if (after.epoch == access.epoch && after.profileId == settings.profileId && after.allows(source, channel)) result else null
                }
            }
            allowed.forEach { channel -> launch {
                observePlayerEpg(channel, request.account, limited).collect { guide ->
                    val now = parental.state.value
                    if (now.epoch != access.epoch || now.profileId != request.account.profileId || !now.allows(source, channel)) return@collect
                    lock.withLock {
                        rows[channel.id] = HomeProgramUiState(channel.id, channel.streamUrl, guide.schedule, guide.nowMillis)
                        trySend(HomeGuideState(owner, access.epoch, rows.values.toList()))
                    }
                }
            } }
        }.buffer(CoroutineChannel.CONFLATED)
    }.retryWhen { error, _ ->
        if (error is CancellationException) throw error
        emit(HomeGuideState()); delay(5_000); true
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 0, replayExpirationMillis = 0), HomeGuideState())

    fun bind(channels: List<Channel>) = bind(channels, MAX_CHANNELS)

    fun bind(channels: List<Channel>, maxChannels: Int) {
        selected.value = channels.asSequence().distinctBy { it.id to it.streamUrl }.take(maxChannels.coerceIn(1, MAX_BROWSER_CHANNELS))
            .map { it.copy(isFavorite = false, folderIds = emptyList()) }.toList()
    }
    companion object { const val MAX_CHANNELS = 8; const val MAX_BROWSER_CHANNELS = 40 }
}