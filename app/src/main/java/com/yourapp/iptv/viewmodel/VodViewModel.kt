package com.yourapp.iptv.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.domain.repository.SettingsRepository
import com.yourapp.iptv.domain.repository.VodRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VodUiState(
    val kind: VodKind = VodKind.MOVIE,
    val loading: Boolean = true,
    val unavailable: Boolean = false,
    val error: Boolean = false,
    val catalog: VodCatalog = VodCatalog(emptyList(), emptyList()),
    val categoryId: String? = null,
    val query: String = "",
    val selected: VodItem? = null,
    val details: VodDetails? = null,
    val detailLoading: Boolean = false,
    val detailError: Boolean = false
) {
    val visible: List<VodItem> get() = catalog.items.filter { item ->
        (categoryId == null || item.categoryId == categoryId) &&
            (query.isBlank() || item.name.contains(query.trim(), ignoreCase = true))
    }
}

@HiltViewModel
class VodViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val repository: VodRepository
) : ViewModel() {
    private val mutable = MutableStateFlow(VodUiState())
    val uiState = mutable.asStateFlow()
    private var catalogJob: Job? = null
    private var detailsJob: Job? = null
    private var loadedKind: VodKind? = null

    fun load(kind: VodKind, refresh: Boolean = false) {
        if (!refresh && loadedKind == kind) return
        catalogJob?.cancel()
        detailsJob?.cancel()
        loadedKind = kind
        mutable.value = VodUiState(kind = kind)
        catalogJob = viewModelScope.launch {
            try {
                val account = settings.observeSettings().first()
                if (account.source != PlaylistSource.XTREAM || !account.hasSelectedSource()) {
                    mutable.value = mutable.value.copy(loading = false, unavailable = true)
                    return@launch
                }
                val catalog = repository.catalog(account, kind)
                mutable.value = mutable.value.copy(loading = false, catalog = catalog)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                mutable.value = mutable.value.copy(loading = false, error = true)
            }
        }
    }

    fun filter(categoryId: String?) { mutable.value = mutable.value.copy(categoryId = categoryId) }
    fun search(query: String) { mutable.value = mutable.value.copy(query = query.take(100)) }

    fun open(item: VodItem) {
        detailsJob?.cancel()
        mutable.value = mutable.value.copy(selected = item, details = null, detailLoading = true, detailError = false)
        detailsJob = viewModelScope.launch {
            try {
                val account = settings.observeSettings().first()
                val details = repository.details(account, mutable.value.kind, item)
                if (mutable.value.selected?.id == item.id) {
                    mutable.value = mutable.value.copy(details = details, detailLoading = false)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                if (mutable.value.selected?.id == item.id) {
                    mutable.value = mutable.value.copy(detailLoading = false, detailError = true,
                        details = VodDetails(item))
                }
            }
        }
    }

    fun close() {
        detailsJob?.cancel()
        mutable.value = mutable.value.copy(selected = null, details = null,
            detailLoading = false, detailError = false)
    }
}