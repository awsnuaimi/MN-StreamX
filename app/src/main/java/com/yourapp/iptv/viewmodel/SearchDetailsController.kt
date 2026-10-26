package com.yourapp.iptv.viewmodel

import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.domain.repository.UnifiedSearchRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class SearchDetailsController(private val scope: CoroutineScope,
    private val repository: UnifiedSearchRepository) {
    private val mutable = MutableStateFlow(VodUiState(loading = false))
    val state = mutable.asStateFlow()
    private var source: AppSettings? = null
    private var request = 0L
    private var job: Job? = null

    fun bind(value: AppSettings?) { close(); source = value }
    fun open(kind: VodKind, item: VodItem) {
        val account = source ?: return
        job?.cancel()
        val token = ++request
        mutable.value = VodUiState(kind = kind, loading = false, selected = item, detailLoading = true)
        job = scope.launch {
            try {
                val details = repository.details(account, kind, item)
                currentCoroutineContext().ensureActive()
                if (token == request) mutable.value = mutable.value.copy(details = details, detailLoading = false)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                if (token == request) mutable.value = mutable.value.copy(details = VodDetails(item),
                    detailLoading = false, detailError = true)
            }
        }
    }

    fun retry() { state.value.selected?.let { open(state.value.kind, it) } }
    fun close() {
        request++; job?.cancel()
        mutable.value = VodUiState(loading = false)
    }
}