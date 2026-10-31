package com.yourapp.iptv.viewmodel

import com.yourapp.iptv.domain.model.VodEpisode
import com.yourapp.iptv.domain.model.VodKind

sealed interface VodPlaybackState {
    data object Loading : VodPlaybackState
    data class Ready(
        val url: String, val title: String, val sourceKey: String,
        val kind: VodKind, val id: String, val extension: String, val resumeMs: Long,
        val seriesId: String? = null, val next: VodEpisode? = null,
        val episodesLoading: Boolean = false, val episodesFailed: Boolean = false,
        val episodes: List<VodEpisode> = emptyList()
    ) : VodPlaybackState
    data object Error : VodPlaybackState
}