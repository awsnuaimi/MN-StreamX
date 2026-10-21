package com.yourapp.iptv.ui.state

import com.yourapp.iptv.domain.model.Channel

enum class LastChannelError { HISTORY, LOOKUP, UNAVAILABLE, SOURCE_CHANGED }

data class LastChannelUiState(val targetId: String? = null, val isLoading: Boolean = false,
    val isSwitching: Boolean = false, val error: LastChannelError? = null) {
    val canRecall: Boolean get() = targetId != null && !isLoading
}

data class LastChannelTarget(val channel: Channel, val sourceKey: String)