package mnstudio.apps.mnstreamx.viewmodel

import mnstudio.apps.mnstreamx.domain.model.VodEpisode
import mnstudio.apps.mnstreamx.domain.model.VodKind

sealed interface VodPlaybackState {
    data object Loading : VodPlaybackState
    data class Ready(
        val url: String, val title: String, val sourceKey: String,
        val kind: VodKind, val id: String, val extension: String, val resumeMs: Long,
        val seriesId: String? = null, val next: VodEpisode? = null,
        val episodesLoading: Boolean = false, val episodesFailed: Boolean = false,
        val episodes: List<VodEpisode> = emptyList(),
        val attempt: Long = 0
    ) : VodPlaybackState
    data object Error : VodPlaybackState
}