package mnstudio.apps.mnstreamx.ui.state

import mnstudio.apps.mnstreamx.domain.model.Channel

/** Only a small visible window; the caller must supply its current owner and access epoch. */
data class HomeGuideState(val sourceKey: String? = null, val accessEpoch: Long = -1,
    val entries: List<HomeProgramUiState> = emptyList()) {
    // A small per-snapshot index avoids scanning the entire guide window for
    // every visible row on each Compose recomposition. The stream URL matters:
    // two channel variants can legitimately share their provider channel ID.
    private val indexed by lazy(LazyThreadSafetyMode.NONE) {
        entries.associateBy { it.channelId to it.streamUrl }
    }

    fun forChannel(channel: Channel, owner: String, epoch: Long): HomeProgramUiState =
        if (sourceKey != owner || accessEpoch != epoch) HomeProgramUiState()
        else indexed[channel.id to channel.streamUrl] ?: HomeProgramUiState()
    override fun toString() = "HomeGuideState(entries=${entries.size})"
}