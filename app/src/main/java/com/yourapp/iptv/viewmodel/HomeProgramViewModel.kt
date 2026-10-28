package com.yourapp.iptv.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourapp.iptv.domain.model.Channel
import com.yourapp.iptv.domain.repository.LiveEpgRepository
import com.yourapp.iptv.domain.repository.SettingsRepository
import com.yourapp.iptv.ui.state.HomeProgramUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Shares the player's bounded guide/cache policy, with an independent Home selection. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeProgramViewModel @Inject constructor(
    settings: SettingsRepository,
    repository: LiveEpgRepository
) : ViewModel() {
    private val selected = MutableStateFlow<Channel?>(null)
    val uiState = combine(selected, settings.observeSettings()) { channel, account -> channel to account }
        .distinctUntilChanged()
        .flatMapLatest { (channel, account) ->
            observePlayerEpg(channel, account, repository).map { guide ->
                HomeProgramUiState(channel?.id, channel?.streamUrl, guide.schedule, guide.nowMillis,
                    sourceKey = com.yourapp.iptv.domain.profile.ProfileScope.of(account))
            }
        }
        .catch { emit(HomeProgramUiState()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 0, replayExpirationMillis = 0), HomeProgramUiState())

    fun selectChannel(channel: Channel?) {
        selected.value = channel?.copy(isFavorite = false)
    }
}