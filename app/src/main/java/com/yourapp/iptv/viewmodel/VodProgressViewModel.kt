package com.yourapp.iptv.viewmodel

import com.yourapp.iptv.domain.profile.ProfileScope

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourapp.iptv.domain.model.PlaylistCacheKey
import com.yourapp.iptv.domain.model.PlaylistSource
import com.yourapp.iptv.domain.model.VodProgressEntry
import com.yourapp.iptv.domain.repository.SettingsRepository
import com.yourapp.iptv.domain.repository.VodProgressRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class VodProgressViewModel @Inject constructor(
    settings: SettingsRepository, progress: VodProgressRepository
) : ViewModel() {
    val entries = settings.observeSettings().map { account ->
        if (account.source == PlaylistSource.XTREAM && account.hasSelectedSource())
            ProfileScope.of(account) else null
    }.distinctUntilChanged().flatMapLatest { source ->
        if (source == null) flowOf(emptyList()) else progress.observe(source)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList<VodProgressEntry>())
}