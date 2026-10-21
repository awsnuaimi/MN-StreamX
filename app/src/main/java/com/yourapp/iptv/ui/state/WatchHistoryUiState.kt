package com.yourapp.iptv.ui.state

import com.yourapp.iptv.domain.model.WatchedChannel

enum class WatchHistoryError { READ, CHANNELS, WRITE }
data class WatchHistoryUiState(val sourceKey: String = "", val rows: List<WatchedChannel> = emptyList(),
    val totalRecords: Int = 0, val isLoading: Boolean = true, val isSaving: Boolean = false,
    val needsSource: Boolean = false, val error: WatchHistoryError? = null)