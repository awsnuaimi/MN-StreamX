package com.yourapp.iptv.viewmodel

import com.yourapp.iptv.domain.repository.FavoriteRepository
import com.yourapp.iptv.ui.state.FavoriteUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class FavoriteController(
    private val scope: CoroutineScope,
    private val repository: FavoriteRepository,
    private val onIdsChanged: (Set<String>) -> Unit
) {
    private val state = MutableStateFlow(FavoriteUiState())
    val uiState = state.asStateFlow()
    private var observer: Job? = null

    init { retry() }

    fun retry() {
        if (state.value.isSaving) return
        observer?.cancel()
        state.update { it.copy(isLoading = true, errorMessage = null) }
        observer = scope.launch {
            try {
                repository.observeFavoriteIds().collect { ids ->
                    onIdsChanged(ids)
                    state.update { it.copy(isLoading = false) }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                state.update { it.copy(isLoading = true,
                    errorMessage = "تعذر قراءة المفضلة. حدّث المفضلة للمحاولة مجددًا.") }
            }
        }
    }

    fun toggle(channelId: String) {
        if (!state.value.canToggle || channelId.isBlank()) return
        state.update { it.copy(isSaving = true, errorMessage = null) }
        scope.launch {
            try {
                repository.toggleFavorite(channelId)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                state.update { it.copy(errorMessage = "تعذر حفظ تغيير المفضلة. حاول مجددًا.") }
            } finally {
                state.update { it.copy(isSaving = false) }
            }
        }
    }
}