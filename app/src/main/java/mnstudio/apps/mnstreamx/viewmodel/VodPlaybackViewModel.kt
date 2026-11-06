package mnstudio.apps.mnstreamx.viewmodel

import mnstudio.apps.mnstreamx.domain.profile.ProfileScope

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import mnstudio.apps.mnstreamx.domain.model.*
import mnstudio.apps.mnstreamx.domain.repository.SettingsRepository
import mnstudio.apps.mnstreamx.domain.repository.VodProgressRepository
import mnstudio.apps.mnstreamx.domain.repository.VodRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import mnstudio.apps.mnstreamx.data.diagnostics.DiagnosticFailures
import mnstudio.apps.mnstreamx.domain.diagnostics.*
import mnstudio.apps.mnstreamx.domain.performance.PerformanceJournal
import mnstudio.apps.mnstreamx.domain.performance.PerformanceStage

@HiltViewModel
class VodPlaybackViewModel @Inject constructor(
    private val saved: SavedStateHandle,
    private val settings: SettingsRepository,
    private val repository: VodRepository,
    private val progress: VodProgressRepository
) : ViewModel() {
    private val mutable = MutableStateFlow<VodPlaybackState>(VodPlaybackState.Loading)
    val state = mutable.asStateFlow()
    val autoNext = saved.getStateFlow("autoNextEpisode", false)

    /** Observe only the active profile/source; an inactive screen has no active collector. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val episodeProgress: StateFlow<List<VodProgressEntry>> = state
        .map { (it as? VodPlaybackState.Ready)?.sourceKey }
        .distinctUntilChanged()
        .flatMapLatest { key -> if (key == null) flowOf(emptyList()) else progress.observe(key) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    @OptIn(ExperimentalCoroutinesApi::class)
    val episodeCompleted = state.map { (it as? VodPlaybackState.Ready)?.sourceKey }
        .distinctUntilChanged().flatMapLatest { key ->
            if (key == null) flowOf(emptyList()) else progress.observeCompleted(key)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<VodCompletedEpisode>())

    private var account: AppSettings? = null
    private var source: String? = null
    private var blocked = false
    private var generation = 0L
    private var selection: Job? = null
    private val metadata = VodMetadataRequest(viewModelScope)
    private var episodeSeries: String? = null
    private var episodes: List<VodEpisode>? = null

    init {
        viewModelScope.launch {
            try {
                settings.observeSettings().map { it to ProfileScope.of(it) }
                    .distinctUntilChangedBy { it.second }.collect { (value, key) ->
                        if (account == null) {
                            require(repository.supports(value))
                            require(saved.get<String>("sourceKey").let { it == null || it == key })
                            val kind = when (saved.get<String>("kind")) {
                                "movie" -> VodKind.MOVIE
                                "series" -> VodKind.SERIES
                                else -> error("Invalid media kind")
                            }
                            val id = requireNotNull(saved.get<String>("id"))
                            val ext = requireNotNull(saved.get<String>("ext"))
                            require(VodEpisodeOrder.validId(id))
                            account = value; source = key; saved["sourceKey"] = key
                            select(kind, id, ext, saved.get<String>("title").orEmpty(), saved["seriesId"])
                        } else if (source != key) invalidate()
                    }
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { invalidate() }
        }
    }

    private fun invalidate() {
        blocked = true; generation++
        selection?.cancel(); metadata.cancel(); episodes = null
        mutable.value = VodPlaybackState.Error
    }

    private fun select(kind: VodKind, id: String, ext: String, title: String, series: String?, refresh: Boolean = false) {
        val credentials = account ?: return
        val key = source ?: return
        if (blocked) return
        val ticket = ++generation
        selection?.cancel()
        mutable.value = VodPlaybackState.Loading
        selection = viewModelScope.launch {
            try {
                val (playback, previous) = PerformanceJournal.measure(PerformanceStage.VOD_PLAYBACK_RESOLVE) { coroutineScope {
                    val target = async { if (refresh) repository.refreshPlayback(credentials,kind,id,ext)
                        else repository.resolvePlayback(credentials,kind,id,ext) }
                    val history = async { progress.observe(key).first() }
                    target.await() to history.await()
                } }
                val resume = VodProgress.find(previous, kind, id)
                val parent = if (kind == VodKind.SERIES) (series ?: resume?.seriesId)
                    ?.takeIf(VodEpisodeOrder::validId) else null
                if (ticket != generation || blocked) return@launch
                saved["kind"] = if (kind == VodKind.MOVIE) "movie" else "series"
                saved["id"] = id; saved["ext"] = playback.extension; saved["title"] = title.take(150); saved["seriesId"] = parent
                mutable.value = VodPlaybackState.Ready(playback.url,title.take(150),key,kind,id,playback.extension,resume?.positionMs ?: 0,
                    parent, if (episodeSeries == parent) episodes?.let { VodEpisodeOrder.next(it,id) } else null,
                    episodes = if (episodeSeries == parent) episodes.orEmpty() else emptyList(), attempt = ticket)
                if (parent != null && (episodeSeries != parent || episodes == null)) loadEpisodes(parent)
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { if (ticket == generation && !blocked) mutable.value = VodPlaybackState.Error }
        }
    }

    private fun loadEpisodes(series: String) {
        if (blocked) return
        val credentials = account ?: return
        val key = source ?: return
        metadata.launch {
            mutable.update { value -> if (value is VodPlaybackState.Ready) value.copy(episodesLoading = true, episodesFailed = false) else value }
            try {
                val ordered = withTimeout(30_000L) {
                    val details = withContext(Dispatchers.IO) {
                        repository.details(credentials, VodKind.SERIES, VodItem(series, series, null))
                    }
                    withContext(Dispatchers.Default) {
                        val context = currentCoroutineContext()
                        VodEpisodeOrder.build(details.seasons) { context.ensureActive() }
                    }
                }
                if (blocked || source != key) return@launch
                episodeSeries = series; episodes = ordered
                complete {
                    mutable.update { value -> if (value is VodPlaybackState.Ready && value.seriesId == series)
                        value.copy(next = VodEpisodeOrder.next(ordered,value.id),episodesLoading = false,
                            episodesFailed = false,episodes = ordered) else value }
                }
            } catch (_: TimeoutCancellationException) {
                complete {
                    if (!blocked) DiagnosticJournal.record(DiagnosticArea.DETAILS,DiagnosticIssue.TIMEOUT,key)
                    episodesFailed(series)
                }
            }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                complete {
                    if (!blocked) DiagnosticFailures.record(DiagnosticArea.DETAILS,error,ProfileScope.source(key))
                    episodesFailed(series)
                }
            }
        }
    }

    private fun episodesFailed(series: String) {
        if (blocked) return
        mutable.update { value -> if (value is VodPlaybackState.Ready && value.seriesId == series)
            value.copy(episodesLoading = false, episodesFailed = true) else value }
    }

    fun retryEpisodes() { (mutable.value as? VodPlaybackState.Ready)?.seriesId?.let(::loadEpisodes) }

    /** A manual retry resolves metadata again instead of reusing an expired signed URL. */
    fun retryPlayback(expected: VodPlaybackState.Ready) {
        val current = mutable.value as? VodPlaybackState.Ready ?: return
        if (blocked || current.sourceKey != expected.sourceKey || current.kind != expected.kind ||
            current.id != expected.id || current.attempt != expected.attempt) return
        select(current.kind,current.id,current.extension,current.title,current.seriesId, refresh = true)
    }

    fun playNext(expected: VodPlaybackState.Ready? = null) {
        val current = mutable.value as? VodPlaybackState.Ready ?: return
        if (expected != null && (expected.id != current.id || expected.sourceKey != current.sourceKey || expected.kind != current.kind)) return
        val next = current.next ?: return
        if (!blocked) select(VodKind.SERIES,next.id,next.extension,next.name,current.seriesId)
    }

    /** Only a verified episode from the active profile and loaded series can be selected. */
    fun playEpisode(expected: VodPlaybackState.Ready, episode: VodEpisode) {
        val current = mutable.value as? VodPlaybackState.Ready ?: return
        if (blocked || current.sourceKey != expected.sourceKey || current.id != expected.id ||
            current.kind != VodKind.SERIES || current.seriesId == null ||
            episodeSeries != current.seriesId) return
        val verified = episodes?.firstOrNull { it.id == episode.id &&
            it.extension == episode.extension } ?: return
        select(VodKind.SERIES, verified.id, verified.extension, verified.name, current.seriesId)
    }

    fun setAutoNext(enabled: Boolean) { saved["autoNextEpisode"] = enabled }

    fun capture(positionMs: Long, durationMs: Long, completed: Boolean = false) {
        (mutable.value as? VodPlaybackState.Ready)?.let { capture(it,positionMs,durationMs,completed) }
    }

    fun restartProgress(item: VodPlaybackState.Ready, durationMs: Long) {
        if (durationMs <= 0) return
        progress.record(item.sourceKey,VodProgressEntry(item.kind,item.id,item.title.ifBlank { item.id }.take(150),
            item.extension,0,durationMs,System.currentTimeMillis(),item.seriesId),completed = false)
    }

    /** The disposed player must record its own episode, even after next selection has changed state. */
    fun capture(item: VodPlaybackState.Ready, positionMs: Long, durationMs: Long, completed: Boolean = false) {
        if (positionMs < 0 || durationMs <= 0 || (!completed && positionMs < 10_000L)) return
        progress.record(item.sourceKey, VodProgressEntry(item.kind,item.id,item.title.ifBlank { item.id }.take(150),
            item.extension,positionMs.coerceAtMost(durationMs),durationMs,System.currentTimeMillis(),item.seriesId),completed)
    }
}