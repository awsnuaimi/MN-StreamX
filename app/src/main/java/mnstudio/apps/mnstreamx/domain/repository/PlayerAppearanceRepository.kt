package mnstudio.apps.mnstreamx.domain.repository

import mnstudio.apps.mnstreamx.domain.model.PlayerAppearance
import kotlinx.coroutines.flow.Flow

interface PlayerAppearanceRepository {
    fun observe(): Flow<PlayerAppearance>
    suspend fun save(appearance: PlayerAppearance)
}