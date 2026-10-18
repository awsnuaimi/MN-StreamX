package com.yourapp.iptv.ui.state

import com.yourapp.iptv.domain.model.Channel

data class PlayerUiState(
    val channel: Channel? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)