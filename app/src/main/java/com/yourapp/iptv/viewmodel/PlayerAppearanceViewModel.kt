package com.yourapp.iptv.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourapp.iptv.domain.model.PlayerAppearance
import com.yourapp.iptv.domain.repository.PlayerAppearanceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerAppearanceViewModel @Inject constructor(repository: PlayerAppearanceRepository) : ViewModel() {
    private val state = MutableStateFlow(PlayerAppearance())
    val appearance = state.asStateFlow()
    init {
        viewModelScope.launch {
            while (isActive) {
                try {
                    repository.observe().collect { state.value = it.normalized() }
                    break
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    // Keep the last usable appearance while storage becomes available again.
                    delay(5000)
                }
            }
        }
    }
}