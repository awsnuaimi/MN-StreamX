package com.yourapp.iptv.viewmodel

import com.yourapp.iptv.domain.repository.FavoriteRepository
import com.yourapp.iptv.ui.state.FavoriteUiState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

internal class FavoriteController(
    private val scope: CoroutineScope,
    private val repository: FavoriteRepository,
    private val onReset: () -> Unit = {},
    private val onIdsChanged: (Set<String>) -> Unit
) {
    private val state = MutableStateFlow(FavoriteUiState())
    val uiState = state.asStateFlow()
    private var observer: Job? = null
    private var writer: Job? = null
    private var sourceKey: String? = null
    private var generation = 0L
    private var bound = false
    init { retry() }

    fun bind(key: String?) {
        if (bound && sourceKey == key) return
        bound = true; sourceKey = key; generation++
        observer?.cancel(); writer?.cancel()
        state.value = FavoriteUiState()
        onReset()
        retry()
    }
    fun retry() {
        if (state.value.isSaving) return
        observer?.cancel()
        val request = ++generation
        val key = sourceKey
        state.update { it.copy(isLoading = true, errorMessage = null) }
        observer = scope.launch {
            try {
                repository.observeFavoriteIds(key).collect { ids ->
                    if (request != generation) return@collect
                    onIdsChanged(ids)
                    state.update { it.copy(isLoading = false) }
                }
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) {
                if (request == generation) state.update { it.copy(isLoading = true,
                    errorMessage = "تعذر قراءة المفضلة. حدّث المفضلة للمحاولة مجددًا.") }
            }
        }
    }
    fun toggle(channelId: String) {
        if (!state.value.canToggle || channelId.isBlank()) return
        val request = generation
        val key = sourceKey // Capture at the press, before a possible subscription change.
        state.update { it.copy(isSaving = true, errorMessage = null) }
        writer = scope.launch {
            try { repository.toggleFavorite(channelId, key) }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) {
                if (request == generation) state.update { it.copy(errorMessage = "تعذر حفظ تغيير المفضلة. حاول مجددًا.") }
            } finally {
                if (request == generation) state.update { it.copy(isSaving = false) }
            }
        }
    }
}