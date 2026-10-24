package com.yourapp.iptv.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourapp.iptv.domain.model.PlaylistCacheKey
import com.yourapp.iptv.domain.model.VodProgress
import com.yourapp.iptv.domain.model.VodProgressEntry
import com.yourapp.iptv.domain.model.PlaylistSource
import com.yourapp.iptv.domain.model.VodKind
import com.yourapp.iptv.domain.repository.SettingsRepository
import com.yourapp.iptv.domain.repository.VodProgressRepository
import com.yourapp.iptv.domain.repository.VodRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface VodPlaybackState {
    data object Loading : VodPlaybackState
    data class Ready(
        val url: String, val title: String, val sourceKey: String,
        val kind: VodKind, val id: String, val extension: String,
        val resumeMs: Long
    ) : VodPlaybackState
    data object Error : VodPlaybackState
}

@HiltViewModel
class VodPlaybackViewModel @Inject constructor(
    saved: SavedStateHandle,
    private val settings: SettingsRepository,
    private val repository: VodRepository,
    private val progress: VodProgressRepository
) : ViewModel() {
    private val mutable = MutableStateFlow<VodPlaybackState>(VodPlaybackState.Loading)
    val state = mutable.asStateFlow()

    init {
        val kind = saved.get<String>("kind")
        val id = saved.get<String>("id")
        val extension = saved.get<String>("ext")
        val title = saved.get<String>("title").orEmpty().take(150)
        viewModelScope.launch {
            try {
                val mediaKind = when (kind) {
                    "movie" -> VodKind.MOVIE
                    "series" -> VodKind.SERIES
                    else -> throw IllegalArgumentException("Invalid media kind")
                }
                require(id != null && extension != null)
                val account = settings.observeSettings().first()
                require(account.source == PlaylistSource.XTREAM && account.hasSelectedSource())
                val url = repository.playbackUrl(account, mediaKind, id, extension)
                val sourceKey = PlaylistCacheKey.of(account)
                val resume = progress.observe(sourceKey).first()
                val position = VodProgress.find(resume, mediaKind, id)?.positionMs ?: 0L
                mutable.value = VodPlaybackState.Ready(url, title, sourceKey,
                    mediaKind, id, extension, position)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                mutable.value = VodPlaybackState.Error
            }
        }
    }

    /** Safe to call from onDispose; repository enqueues on its application-owned scope. */
    fun capture(positionMs: Long, durationMs: Long, completed: Boolean = false) {
        val ready = mutable.value as? VodPlaybackState.Ready ?: return
        if (positionMs < 0 || durationMs <= 0 || (!completed && positionMs < 10_000L)) return
        val boundedPosition = positionMs.coerceAtMost(durationMs)
        progress.record(ready.sourceKey, VodProgressEntry(
            ready.kind, ready.id, ready.title.ifBlank { ready.id }.take(150),
            ready.extension, boundedPosition, durationMs, System.currentTimeMillis()
        ), completed)
    }
}