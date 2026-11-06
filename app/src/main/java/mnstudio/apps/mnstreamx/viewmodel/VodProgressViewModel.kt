package mnstudio.apps.mnstreamx.viewmodel

import mnstudio.apps.mnstreamx.domain.profile.ProfileScope

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import mnstudio.apps.mnstreamx.domain.model.PlaylistCacheKey
import mnstudio.apps.mnstreamx.domain.model.PlaylistSource
import mnstudio.apps.mnstreamx.domain.model.VodProgressEntry
import mnstudio.apps.mnstreamx.domain.model.VodCompletedEpisode
import mnstudio.apps.mnstreamx.domain.repository.SettingsRepository
import mnstudio.apps.mnstreamx.domain.repository.VodProgressRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject

/** UI data is tagged with its source, so a cached emission cannot appear in another profile. */
data class VodScopedHistory(val sourceKey: String? = null,
    val entries: List<VodProgressEntry> = emptyList(),
    val completed: List<VodCompletedEpisode> = emptyList())

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

    val scopedHistory = settings.observeSettings().map { account ->
        if (account.source == PlaylistSource.XTREAM && account.hasSelectedSource())
            ProfileScope.of(account) else null
    }.distinctUntilChanged().flatMapLatest { key ->
        if (key == null) kotlinx.coroutines.flow.flowOf(VodScopedHistory())
        else combine(progress.observe(key), progress.observeCompleted(key)) { positions, watched ->
            VodScopedHistory(key, positions, watched)
        }.onStart { emit(VodScopedHistory(key)) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), VodScopedHistory())
}