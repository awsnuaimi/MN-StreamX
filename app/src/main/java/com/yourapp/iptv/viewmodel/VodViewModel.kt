package com.yourapp.iptv.viewmodel

import com.yourapp.iptv.domain.profile.ProfileScope

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.domain.repository.SettingsRepository
import com.yourapp.iptv.domain.repository.VodRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.yourapp.iptv.data.diagnostics.DiagnosticFailures
import com.yourapp.iptv.domain.diagnostics.DiagnosticArea

data class VodUiState(
    val kind: VodKind = VodKind.MOVIE,
    val loading: Boolean = true,
    val unavailable: Boolean = false,
    val error: Boolean = false,
    val catalog: VodCatalog = VodCatalog(emptyList(), emptyList()),
    val categoryId: String? = null,
    val categoryLoading: Boolean = false,
    val categoryError: Boolean = false,
    val query: String = "",
    val selected: VodItem? = null,
    val details: VodDetails? = null,
    val detailLoading: Boolean = false,
    val detailError: Boolean = false,
    val sourceKey: String? = null,
    val filteredItems: List<VodItem>? = null,
    val filtering: Boolean = false,
    val refreshing: Boolean = false,
    val refreshError: Boolean = false,
    val categoryRefreshing: Boolean = false,
    val categoryRefreshError: Boolean = false
) {
    // Synthetic UI/test states can still derive visibility without a ViewModel.
    val visible: List<VodItem> get() = filteredItems
        ?: if (categoryId == null && query.isBlank()) catalog.items
        else VodCatalogFilter.match(catalog.items, categoryId, query)
}

@HiltViewModel
class VodViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val repository: VodRepository
) : ViewModel() {
    private val mutable = MutableStateFlow(VodUiState())
    val uiState = mutable.asStateFlow()
    private var catalogJob: Job? = null
    private var categoryJob: Job? = null
    private var detailsJob: Job? = null
    private var visibleJob: Job? = null
    private var visibleRequest = 0L
    private var loadedKind: VodKind? = null
    private var account: AppSettings? = null
    private var generation = 0L
    private var categoryRequest = 0L
    private var detailRequest = 0L

    fun load(kind: VodKind, refresh: Boolean = false) {
        if (!refresh && loadedKind == kind && catalogJob?.isActive == true) return
        catalogJob?.cancel()
        cancelSelection()
        cancelVisible()
        loadedKind = kind
        mutable.value = VodUiState(kind = kind)
        catalogJob = viewModelScope.launch {
            try {
                settings.observeSettings().distinctUntilChangedBy(ProfileScope::of).collectLatest { source ->
                    cancelSelection()
                    cancelVisible()
                    val current = ++generation
                    account = source
                    mutable.value = VodUiState(kind = kind, sourceKey = ProfileScope.of(source))
                    if (!repository.supports(source)) {
                        mutable.value = mutable.value.copy(loading = false, unavailable = true)
                        return@collectLatest
                    }
                    try {
                        repository.catalogUpdates(source, kind, force = refresh).collect { event ->
                            currentCoroutineContext().ensureActive()
                            if (current != generation) return@collect
                            val state = mutable.value
                            val catalog = event.value
                            // Updating category metadata must not discard an active category's items.
                            val keepCategory = catalog.categoryScoped && state.catalog.categoryScoped &&
                                state.categoryId != null && catalog.categories.any { it.id == state.categoryId }
                            val keepFilter = !catalog.categoryScoped && !state.catalog.categoryScoped &&
                                state.categoryId != null && catalog.categories.any { it.id == state.categoryId }
                            val shown = if (keepCategory) catalog.copy(items = state.catalog.items) else catalog
                            val next = state.copy(loading = false, catalog = shown,
                                refreshing = event.refreshing, refreshError = event.refreshFailed,
                                categoryId = if (keepCategory || keepFilter) state.categoryId else null,
                                categoryLoading = if (keepCategory) state.categoryLoading else false,
                                categoryRefreshing = if (keepCategory) state.categoryRefreshing else false)
                            if (shown.items === state.catalog.items && next.categoryId == state.categoryId) {
                                mutable.value = next
                            } else updateVisible(next)
                            if (catalog.categoryScoped && !keepCategory) {
                                catalog.categories.firstOrNull()?.let { filter(it.id, retry = refresh) }
                            }
                        }
                    } catch (error: CancellationException) { throw error }
                    catch (error: Exception) {
                        DiagnosticFailures.record(if (kind == VodKind.MOVIE) DiagnosticArea.MOVIE_CATALOG else DiagnosticArea.SERIES_CATALOG,error,PlaylistCacheKey.of(source))
                        if (current == generation) {
                            val existing = mutable.value
                            mutable.value = existing.copy(loading = false, refreshing = false,
                                error = existing.catalog.items.isEmpty() && existing.catalog.categories.isEmpty(),
                                refreshError = existing.catalog.items.isNotEmpty() || existing.catalog.categories.isNotEmpty())
                        }
                    }
                }
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { mutable.value = mutable.value.copy(loading = false, error = true) }
        }
    }

    fun filter(categoryId: String?, retry: Boolean = false) {
        val state = mutable.value
        if (!state.catalog.categoryScoped) {
            updateVisible(state.copy(categoryId = categoryId))
            return
        }
        val source = account ?: return
        val category = state.catalog.categories.firstOrNull { it.id == categoryId } ?: return
        if (!retry && state.categoryId == category.id && !state.categoryError) return
        categoryJob?.cancel()
        close()
        val request = ++categoryRequest
        val current = generation
        updateVisible(mutable.value.copy(categoryId = category.id, categoryLoading = true,
            categoryError = false, categoryRefreshError = false, categoryRefreshing = false,
            catalog = state.catalog.copy(items = emptyList())))
        categoryJob = viewModelScope.launch {
            try {
                repository.categoryUpdates(source, state.kind, category.id, force = retry).collect { event ->
                    currentCoroutineContext().ensureActive()
                    if (current == generation && request == categoryRequest) {
                        updateVisible(mutable.value.copy(categoryLoading = false,
                            categoryRefreshing = event.refreshing,
                            categoryRefreshError = event.refreshFailed,
                            catalog = mutable.value.catalog.copy(items = event.value)))
                    }
                }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                DiagnosticFailures.record(if (state.kind == VodKind.MOVIE) DiagnosticArea.MOVIE_CATALOG else DiagnosticArea.SERIES_CATALOG,error,PlaylistCacheKey.of(source))
                if (current == generation && request == categoryRequest) {
                    mutable.value = mutable.value.copy(categoryLoading = false,
                        categoryRefreshing = false, categoryError = true)
                }
            }
        }
    }

    fun search(query: String) {
        val clipped = query.take(100)
        if (mutable.value.query != clipped) updateVisible(mutable.value.copy(query = clipped))
    }

    fun open(item: VodItem) {
        val source = account ?: return
        detailsJob?.cancel()
        val request = ++detailRequest
        val current = generation
        val kind = mutable.value.kind
        mutable.value = mutable.value.copy(selected = item, details = null, detailLoading = true, detailError = false)
        detailsJob = viewModelScope.launch {
            try {
                val details = repository.details(source, kind, item)
                currentCoroutineContext().ensureActive()
                if (current == generation && request == detailRequest) {
                    mutable.value = mutable.value.copy(details = details, detailLoading = false)
                }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                DiagnosticFailures.record(DiagnosticArea.DETAILS,error,PlaylistCacheKey.of(source))
                if (current == generation && request == detailRequest) {
                    mutable.value = mutable.value.copy(detailLoading = false, detailError = true,
                        details = VodDetails(item))
                }
            }
        }
    }

    fun close() {
        detailsJob?.cancel()
        detailRequest++
        mutable.value = mutable.value.copy(selected = null, details = null,
            detailLoading = false, detailError = false)
    }

    private fun cancelVisible() {
        visibleJob?.cancel()
        visibleRequest++
    }

    /** Keep expensive catalog scans off the UI thread, without changing small-list focus timing. */
    private fun updateVisible(next: VodUiState) {
        cancelVisible()
        val entries = next.catalog.items
        if (entries.size < VodCatalogFilter.BACKGROUND_THRESHOLD ||
            (next.categoryId == null && next.query.isBlank())) {
            mutable.value = next.copy(filteredItems = null, filtering = false)
            return
        }
        val previous = mutable.value
        val candidates = VodCatalogFilter.refinedCandidates(
            entries, next.categoryId, next.query, previous.catalog.items,
            previous.categoryId, previous.query,
            if (previous.filtering) null else previous.filteredItems
        )
        val token = visibleRequest
        val category = next.categoryId
        val query = next.query
        mutable.value = next.copy(filteredItems = emptyList(), filtering = true)
        visibleJob = viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                VodCatalogFilter.matchCancellable(candidates, category, query)
            }
            currentCoroutineContext().ensureActive()
            val state = mutable.value
            if (token == visibleRequest && state.catalog.items === entries &&
                state.categoryId == category && state.query == query) {
                mutable.value = state.copy(filteredItems = result, filtering = false)
            }
        }
    }

    private fun cancelSelection() {
        categoryJob?.cancel()
        categoryRequest++
        close()
    }
}