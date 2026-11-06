package mnstudio.apps.mnstreamx.ui.state

import mnstudio.apps.mnstreamx.domain.model.Channel
import mnstudio.apps.mnstreamx.domain.model.EpgSchedule

/** A schedule belongs to an exact source/channel pair, never just its display name. */
data class HomeProgramUiState(
    val channelId: String? = null,
    val streamUrl: String? = null,
    val schedule: EpgSchedule = EpgSchedule(emptyList()),
    val nowMillis: Long = 0L,
    val sourceKey: String? = null
) {
    fun matches(channel: Channel): Boolean = channel.id == channelId && channel.streamUrl == streamUrl

    override fun toString(): String = "HomeProgramUiState(programs=${schedule.programs.size})"
}