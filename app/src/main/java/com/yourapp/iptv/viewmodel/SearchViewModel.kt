package com.yourapp.iptv.viewmodel

import com.yourapp.iptv.domain.profile.ProfileScope

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourapp.iptv.di.DefaultDispatcher
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.domain.repository.*
import com.yourapp.iptv.domain.search.*
import com.yourapp.iptv.ui.state.SearchUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val saved: SavedStateHandle,
    private val settings: SettingsRepository,
    private val repository: UnifiedSearchRepository,
    private val history: SearchHistoryRepository,
    @DefaultDispatcher private val background: CoroutineDispatcher
) : ViewModel() {
    private val mutable = MutableStateFlow(SearchUiState(query = saved.get<String>(QUERY).orEmpty().take(100)))
    val state = mutable.asStateFlow()
    private val movies = SearchVodController(VodKind.MOVIE, viewModelScope, repository, background) { lane ->
        mutable.update { it.copy(movies = lane) }
    }
    private val series = SearchVodController(VodKind.SERIES, viewModelScope, repository, background) { lane ->
        mutable.update { it.copy(series = lane) }
    }
    private val detailsController = SearchDetailsController(viewModelScope, repository)
    val details = detailsController.state
    private var source: AppSettings? = null
    private var channelIndex: SearchIndex<Channel>? = null
    private var generation = 0L
    private var queryRequest = 0L
    private var visible = true
    private var sourceJob: Job? = null
    private var channelsJob: Job? = null
    private var queryJob: Job? = null
    private var historyJob: Job? = null

    init { retrySource() }

    fun retrySource() {
        sourceJob?.cancel()
        sourceJob = viewModelScope.launch {
            try {
                settings.observeSettings().distinctUntilChangedBy(ProfileScope::of).collect { account -> bind(account) }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                bind(null)
                mutable.update { it.copy(sourceLoading = false, sourceError = true) }
            }
        }
    }

    private fun bind(account: AppSettings?) {
        generation++
        channelsJob?.cancel(); queryJob?.cancel(); historyJob?.cancel()
        source = account?.takeIf { it.hasSelectedSource() }
        channelIndex = null
        val key = source?.let(ProfileScope::of)
        val query = if (key != null && key == saved.get<String>(SOURCE)) mutable.value.query else ""
        saved[SOURCE] = key; saved[QUERY] = query
        mutable.value = SearchUiState(query = query, sourceKey = key, sourceLoading = false,
            needsSource = account != null && source == null)
        movies.bind(source); series.bind(source); detailsController.bind(source)
        if (key == null) return
        observeHistory(key)
        reloadChannels()
        updateQuery(query, immediate = true)
    }

    fun setVisible(value: Boolean) {
        if (visible == value) return
        visible = value
        if (!value) {
            queryJob?.cancel(); movies.pause(); series.pause()
            mutable.update { it.copy(channelsSearching = false) }
        } else updateQuery(mutable.value.query, immediate = true)
    }

    fun updateQuery(value: String) = updateQuery(value, immediate = false)
    private fun updateQuery(value: String, immediate: Boolean) {
        val text = value.filterNot { it.isISOControl() }.take(SearchText.QUERY_LIMIT)
        val changed = text != mutable.value.query
        saved[QUERY] = text
        queryRequest++
        queryJob?.cancel()
        movies.updateQuery(text); series.updateQuery(text)
        if (changed) mutable.update { it.copy(query = text, channels = emptyList(), channelsMore = false,
            channelsSearching = SearchText.normalized(text).isNotBlank() && !it.channelsMissing && !it.channelsError) }
        if (!visible || source == null || SearchText.normalized(text).isBlank()) return
        val current = generation
        val request = queryRequest
        queryJob = viewModelScope.launch {
            if (!immediate) delay(220)
            movies.search(); series.search()
            val index = channelIndex
            if (index != null) {
                val matches = withContext(background) { index.find(text) }
                if (current == generation && request == queryRequest) mutable.update {
                    it.copy(channels = matches.items, channelsMore = matches.more, channelsSearching = false)
                }
            }
        }
    }

    fun reloadChannels() {
        val account = source ?: return
        val current = generation
        channelsJob?.cancel()
        channelIndex = null
        mutable.update { it.copy(channelsLoading = true, channelsError = false, channelsMissing = false,
            channels = emptyList(), channelsMore = false) }
        channelsJob = viewModelScope.launch {
            try {
                val index = repository.channels(account)
                currentCoroutineContext().ensureActive()
                if (current != generation) return@launch
                channelIndex = index
                mutable.update { it.copy(channelsLoading = false, channelsSearching = false, channelsMissing = index == null,
                    channelsLimited = index?.limited == true) }
                updateQuery(mutable.value.query, immediate = true)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                if (current == generation) mutable.update { it.copy(channelsLoading = false, channelsSearching = false, channelsError = true) }
            }
        }
    }

    private fun observeHistory(key: String) {
        historyJob?.cancel()
        val current = generation
        historyJob = viewModelScope.launch {
            try {
                history.observe(key).collect { rows ->
                    if (current == generation) mutable.update { it.copy(history = rows, historyError = false) }
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                if (current == generation) mutable.update { it.copy(historyError = true) }
            }
        }
    }

    fun submit() {
        updateQuery(mutable.value.query, immediate = true)
        val key = mutable.value.sourceKey ?: return
        val query = SearchText.query(mutable.value.query)
        if (SearchText.normalized(query).isBlank()) return
        val current = generation
        viewModelScope.launch {
            try { history.record(key, query) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { if (current == generation) mutable.update { it.copy(historyError = true) } }
        }
    }

    fun useHistory(query: String) { updateQuery(query, immediate = true); submit() }
    fun clearHistory() {
        val key = mutable.value.sourceKey ?: return
        val current = generation
        viewModelScope.launch {
            try {
                history.clear(key)
                if (current == generation) observeHistory(key)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { if (current == generation) mutable.update { it.copy(historyError = true) } }
        }
    }

    fun retry(kind: VodKind) = lane(kind).retry()
    fun scanMore(kind: VodKind) { submit(); lane(kind).scanMore() }
    fun stopScan(kind: VodKind) = lane(kind).stopScan()
    private fun lane(kind: VodKind) = if (kind == VodKind.MOVIE) movies else series

    fun chooseChannel(channel: Channel, expectedSource: String): Boolean {
        if (mutable.value.sourceKey != expectedSource || mutable.value.channels.none {
            it.id == channel.id && it.streamUrl == channel.streamUrl }) return false
        submit(); movies.stopScan(); series.stopScan()
        return true
    }

    fun open(kind: VodKind, item: VodItem, expectedSource: String) {
        if (mutable.value.sourceKey != expectedSource) return
        val results = if (kind == VodKind.MOVIE) mutable.value.movies.results else mutable.value.series.results
        if (results.none { it.id == item.id && it.name == item.name }) return
        submit(); movies.stopScan(); series.stopScan()
        detailsController.open(kind, item)
    }
    fun closeDetails() = detailsController.close()
    fun retryDetails() = detailsController.retry()

    companion object {
        private const val QUERY = "unifiedSearchQuery"
        private const val SOURCE = "unifiedSearchSource"
    }
}