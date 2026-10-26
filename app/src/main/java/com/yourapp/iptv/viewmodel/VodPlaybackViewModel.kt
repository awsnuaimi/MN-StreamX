package com.yourapp.iptv.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.domain.repository.SettingsRepository
import com.yourapp.iptv.domain.repository.VodProgressRepository
import com.yourapp.iptv.domain.repository.VodRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import com.yourapp.iptv.data.diagnostics.DiagnosticFailures
import com.yourapp.iptv.domain.diagnostics.*

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
    private var account: AppSettings? = null
    private var source: String? = null
    private var blocked = false
    private var generation = 0L
    private var selection: Job? = null
    private var metadata: Job? = null
    private var episodeSeries: String? = null
    private var episodes: List<VodEpisode>? = null

    init {
        viewModelScope.launch {
            try {
                settings.observeSettings().map { it to PlaylistCacheKey.of(it) }
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
        selection?.cancel(); metadata?.cancel(); episodes = null
        mutable.value = VodPlaybackState.Error
    }

    private fun select(kind: VodKind, id: String, ext: String, title: String, series: String?) {
        val credentials = account ?: return
        val key = source ?: return
        if (blocked) return
        val ticket = ++generation
        selection?.cancel()
        mutable.value = VodPlaybackState.Loading
        selection = viewModelScope.launch {
            try {
                val url = repository.playbackUrl(credentials, kind, id, ext)
                val previous = progress.observe(key).first()
                val resume = VodProgress.find(previous, kind, id)
                val parent = if (kind == VodKind.SERIES) (series ?: resume?.seriesId)
                    ?.takeIf(VodEpisodeOrder::validId) else null
                if (ticket != generation || blocked) return@launch
                saved["kind"] = if (kind == VodKind.MOVIE) "movie" else "series"
                saved["id"] = id; saved["ext"] = ext; saved["title"] = title.take(150); saved["seriesId"] = parent
                mutable.value = VodPlaybackState.Ready(url,title.take(150),key,kind,id,ext,resume?.positionMs ?: 0,
                    parent, if (episodeSeries == parent) episodes?.let { VodEpisodeOrder.next(it,id) } else null)
                if (parent != null && (episodeSeries != parent || episodes == null)) loadEpisodes(parent)
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { if (ticket == generation && !blocked) mutable.value = VodPlaybackState.Error }
        }
    }

    private fun loadEpisodes(series: String) {
        if (metadata?.isActive == true || blocked) return
        val credentials = account ?: return
        val key = source ?: return
        mutable.update { value -> if (value is VodPlaybackState.Ready) value.copy(episodesLoading = true, episodesFailed = false) else value }
        metadata = viewModelScope.launch {
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
                mutable.update { value -> if (value is VodPlaybackState.Ready && value.seriesId == series)
                    value.copy(next = VodEpisodeOrder.next(ordered,value.id),episodesLoading = false, episodesFailed = false) else value }
            } catch (_: TimeoutCancellationException) {
                if (!blocked) DiagnosticJournal.record(DiagnosticArea.DETAILS,DiagnosticIssue.TIMEOUT,key)
                episodesFailed(series)
            }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) { if (!blocked) DiagnosticFailures.record(DiagnosticArea.DETAILS,error,key);episodesFailed(series) }
        }
    }

    private fun episodesFailed(series: String) {
        if (blocked) return
        mutable.update { value -> if (value is VodPlaybackState.Ready && value.seriesId == series)
            value.copy(episodesLoading = false, episodesFailed = true) else value }
    }

    fun retryEpisodes() { (mutable.value as? VodPlaybackState.Ready)?.seriesId?.let(::loadEpisodes) }

    fun playNext(expected: VodPlaybackState.Ready? = null) {
        val current = mutable.value as? VodPlaybackState.Ready ?: return
        if (expected != null && (expected.id != current.id || expected.sourceKey != current.sourceKey || expected.kind != current.kind)) return
        val next = current.next ?: return
        if (!blocked) select(VodKind.SERIES,next.id,next.extension,next.name,current.seriesId)
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