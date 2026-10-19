package com.yourapp.iptv.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourapp.iptv.domain.model.Channel
import com.yourapp.iptv.domain.repository.LiveEpgRepository
import com.yourapp.iptv.domain.repository.SettingsRepository
import com.yourapp.iptv.ui.state.PlayerEpgUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlayerEpgViewModel @Inject constructor(
    settings: SettingsRepository,
    repository: LiveEpgRepository
) : ViewModel() {
    private val selected = MutableStateFlow<Channel?>(null)
    val uiState = combine(selected, settings.observeSettings()) { channel, account -> channel to account }
        .distinctUntilChanged()
        .flatMapLatest { (channel, account) -> observePlayerEpg(channel, account, repository) }
        .catch { emit(PlayerEpgUiState(channelId = selected.value?.id,
            errorMessage = "تعذر قراءة إعدادات دليل البرامج.")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 0), PlayerEpgUiState())

    fun selectChannel(channel: Channel?) { selected.value = channel?.copy(isFavorite = false) }
}