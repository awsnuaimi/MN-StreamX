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
    val filtering: Boolean = false
) {
    // Synthetic UI/test states can still derive visibility without a ViewModel.
    val visible: List<VodItem> get() = filteredItems ?: VodCatalogFilter.match(catalog.items, categoryId, query)
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
                        val catalog = repository.catalog(source, kind)
                        currentCoroutineContext().ensureActive()
                        if (current != generation) return@collectLatest
                        updateVisible(mutable.value.copy(loading = false, catalog = catalog))
                        if (catalog.categoryScoped) catalog.categories.firstOrNull()?.let { filter(it.id) }
                    } catch (error: CancellationException) { throw error }
                    catch (error: Exception) {
                        DiagnosticFailures.record(if (kind == VodKind.MOVIE) DiagnosticArea.MOVIE_CATALOG else DiagnosticArea.SERIES_CATALOG,error,PlaylistCacheKey.of(source))
                        if (current == generation) mutable.value = mutable.value.copy(loading = false, error = true)
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
            categoryError = false, catalog = state.catalog.copy(items = emptyList())))
        categoryJob = viewModelScope.launch {
            try {
                val items = repository.category(source, state.kind, category.id)
                currentCoroutineContext().ensureActive()
                if (current == generation && request == categoryRequest) {
                    updateVisible(mutable.value.copy(categoryLoading = false,
                        catalog = mutable.value.catalog.copy(items = items)))
                }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                DiagnosticFailures.record(if (state.kind == VodKind.MOVIE) DiagnosticArea.MOVIE_CATALOG else DiagnosticArea.SERIES_CATALOG,error,PlaylistCacheKey.of(source))
                if (current == generation && request == categoryRequest) {
                    mutable.value = mutable.value.copy(categoryLoading = false, categoryError = true)
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
        if (entries.size < VodCatalogFilter.BACKGROUND_THRESHOLD) {
            mutable.value = next.copy(filteredItems = null, filtering = false)
            return
        }
        val token = visibleRequest
        val category = next.categoryId
        val query = next.query
        mutable.value = next.copy(filteredItems = emptyList(), filtering = true)
        visibleJob = viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                VodCatalogFilter.matchCancellable(entries, category, query)
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