package mnstudio.apps.mnstreamx.domain.repository

import mnstudio.apps.mnstreamx.domain.model.CaptionSettings
import kotlinx.coroutines.flow.Flow

interface CaptionRepository {
    fun observe(): Flow<CaptionSettings>
    suspend fun save(value: CaptionSettings)
}