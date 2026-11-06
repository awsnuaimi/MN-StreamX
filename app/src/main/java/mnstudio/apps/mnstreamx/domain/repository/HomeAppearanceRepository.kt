package mnstudio.apps.mnstreamx.domain.repository

import mnstudio.apps.mnstreamx.domain.model.HomeAppearance
import kotlinx.coroutines.flow.Flow

interface HomeAppearanceRepository {
    fun observe(): Flow<HomeAppearance>
    suspend fun save(value: HomeAppearance)
}