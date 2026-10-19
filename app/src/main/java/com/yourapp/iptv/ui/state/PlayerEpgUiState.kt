package com.yourapp.iptv.ui.state

import com.yourapp.iptv.domain.model.EpgSchedule

data class PlayerEpgUiState(
    val channelId: String? = null,
    val schedule: EpgSchedule = EpgSchedule(emptyList()),
    val nowMillis: Long = 0L,
    val isLoading: Boolean = false,
    val isSupported: Boolean = true,
    val errorMessage: String? = null
)