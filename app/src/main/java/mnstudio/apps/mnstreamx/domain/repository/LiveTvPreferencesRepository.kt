package mnstudio.apps.mnstreamx.domain.repository

import mnstudio.apps.mnstreamx.domain.model.LiveTvPreferences
import kotlinx.coroutines.flow.Flow

interface LiveTvPreferencesRepository {
    fun observe(scope: String): Flow<LiveTvPreferences>
    suspend fun update(scope: String, change: (LiveTvPreferences) -> LiveTvPreferences)
}