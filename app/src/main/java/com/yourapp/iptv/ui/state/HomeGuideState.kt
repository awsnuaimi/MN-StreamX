package com.yourapp.iptv.ui.state

import com.yourapp.iptv.domain.model.Channel

/** Only a small visible window; the caller must supply its current owner and access epoch. */
data class HomeGuideState(val sourceKey: String? = null, val accessEpoch: Long = -1,
    val entries: List<HomeProgramUiState> = emptyList()) {
    fun forChannel(channel: Channel, owner: String, epoch: Long): HomeProgramUiState =
        if (sourceKey != owner || accessEpoch != epoch) HomeProgramUiState()
        else entries.firstOrNull { it.matches(channel) } ?: HomeProgramUiState()
    override fun toString() = "HomeGuideState(entries=${entries.size})"
}