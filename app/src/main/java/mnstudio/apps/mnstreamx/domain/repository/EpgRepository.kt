package mnstudio.apps.mnstreamx.domain.repository

import mnstudio.apps.mnstreamx.domain.model.Program

interface EpgRepository {
    suspend fun getPrograms(channelId: String): List<Program>
}