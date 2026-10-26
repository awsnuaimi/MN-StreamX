package com.yourapp.iptv.viewmodel

import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.domain.repository.UnifiedSearchRepository
import com.yourapp.iptv.domain.search.*
import com.yourapp.iptv.ui.state.SearchVodState
import kotlinx.coroutines.*
import com.yourapp.iptv.data.diagnostics.DiagnosticFailures
import com.yourapp.iptv.domain.diagnostics.DiagnosticArea

/** Holds one kind's immutable index; category scans retain only matching rows. */
internal class SearchVodController(
    private val kind: VodKind,
    private val scope: CoroutineScope,
    private val repository: UnifiedSearchRepository,
    private val background: CoroutineDispatcher,
    private val publish: (SearchVodState) -> Unit
) {
    private var state = SearchVodState()
    private var source: AppSettings? = null
    private var catalog: SearchVodCatalog? = null
    private var query = ""
    private var binding = 0L
    private var request = 0L
    private var loadJob: Job? = null
    private var findJob: Job? = null
    private var scanJob: Job? = null
    private val searched = LinkedHashSet<String>()
    private val failed = LinkedHashSet<String>()

    fun bind(value: AppSettings?) {
        binding++
        pause()
        source = value
        catalog = null
        query = ""
        searched.clear(); failed.clear()
        emit(SearchVodState(unavailable = value != null && !repository.supportsVod(value)))
    }

    fun updateQuery(value: String) {
        if (query == value) return
        query = value
        request++
        findJob?.cancel(); scanJob?.cancel()
        searched.clear(); failed.clear()
        emit(state.copy(results = emptyList(), more = false, scanning = false,
            searching = SearchText.normalized(value).isNotBlank() && catalog?.categoryScoped == false,
            categoriesSearched = 0, categoriesFailed = 0, indexLimited = catalog?.index?.limited == true))
    }

    fun search() {
        val account = source ?: return
        if (SearchText.normalized(query).isBlank() || state.unavailable) return
        val loaded = catalog
        if (loaded == null) {
            if (loadJob?.isActive == true || state.error) return
            val generation = binding
            emit(state.copy(loading = true, error = false))
            loadJob = scope.launch {
                try {
                    val result = repository.catalog(account, kind)
                    currentCoroutineContext().ensureActive()
                    if (generation != binding) return@launch
                    catalog = result
                    emit(state.copy(loading = false, loaded = true, categoryScoped = result.categoryScoped,
                        categoriesTotal = result.categories.size, indexLimited = result.index.limited))
                    search()
                } catch (e: CancellationException) { throw e }
                catch (error: Exception) {
                    DiagnosticFailures.record(if (kind == VodKind.MOVIE) DiagnosticArea.MOVIE_CATALOG else DiagnosticArea.SERIES_CATALOG,error,PlaylistCacheKey.of(account))
                    if (generation == binding) emit(state.copy(loading = false, error = true))
                }
            }
        } else if (!loaded.categoryScoped) {
            findJob?.cancel()
            val generation = binding
            val token = ++request
            val text = query
            emit(state.copy(searching = true))
            findJob = scope.launch {
                val matches = withContext(background) { loaded.index.find(text) }
                if (generation == binding && token == request) emit(state.copy(results = matches.items, more = matches.more, searching = false))
            }
        }
    }

    fun retry() {
        if (!state.error || loadJob?.isActive == true) return
        emit(state.copy(error = false))
        search()
    }

    fun scanMore() {
        val account = source ?: return
        val loaded = catalog ?: return
        if (!state.canScan || SearchText.normalized(query).isBlank()) return
        val untouched = loaded.categories.filterNot { it.id in searched || it.id in failed }
        val batch = (if (untouched.isNotEmpty()) untouched else loaded.categories.filter { it.id in failed }).take(BATCH_SIZE)
        if (batch.isEmpty()) return
        val generation = binding
        val token = ++request
        val text = query
        emit(state.copy(scanning = true))
        scanJob = scope.launch {
            try {
                for (category in batch) {
                    try {
                        val matches = repository.category(account, kind, category.id, text)
                        currentCoroutineContext().ensureActive()
                        if (generation != binding || token != request) return@launch
                        searched.add(category.id); failed.remove(category.id)
                        val combined = (state.results + matches.items).distinctBy { it.id }
                        emit(state.copy(results = combined.take(SearchIndex.RESULT_LIMIT),
                            categoriesSearched = searched.size, categoriesFailed = failed.size,
                            indexLimited = state.indexLimited || matches.limited,
                            more = state.more || matches.more || combined.size >= SearchIndex.RESULT_LIMIT))
                        if (state.more) break
                    } catch (e: CancellationException) { throw e }
                    catch (error: Exception) {
                        DiagnosticFailures.record(if (kind == VodKind.MOVIE) DiagnosticArea.MOVIE_CATALOG else DiagnosticArea.SERIES_CATALOG,error,PlaylistCacheKey.of(account))
                        if (generation != binding || token != request) return@launch
                        failed.add(category.id)
                        emit(state.copy(categoriesFailed = failed.size))
                    }
                }
            } finally {
                if (generation == binding && token == request) emit(state.copy(scanning = false))
            }
        }
    }

    fun stopScan() {
        if (!state.scanning) return
        request++
        scanJob?.cancel()
        emit(state.copy(scanning = false))
    }

    fun pause() {
        request++
        loadJob?.cancel(); findJob?.cancel(); scanJob?.cancel()
        emit(state.copy(loading = false, scanning = false, searching = false))
    }

    private fun emit(value: SearchVodState) { state = value; publish(value) }
    companion object { const val BATCH_SIZE = 10 }
}