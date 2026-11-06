package mnstudio.apps.mnstreamx.domain.repository

import mnstudio.apps.mnstreamx.domain.model.AppSettings
import mnstudio.apps.mnstreamx.domain.model.Channel
import mnstudio.apps.mnstreamx.domain.model.EpgSchedule

interface LiveEpgRepository {
    /** Null means this channel does not belong to the selected Xtream account. */
    suspend fun load(channel: Channel, settings: AppSettings): EpgSchedule?
}