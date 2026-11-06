package mnstudio.apps.mnstreamx.domain.repository

import mnstudio.apps.mnstreamx.domain.model.VodProgressEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import mnstudio.apps.mnstreamx.domain.model.VodCompletedEpisode

interface VodProgressRepository {
    fun observe(sourceKey: String): Flow<List<VodProgressEntry>>
    fun observeCompleted(sourceKey: String): Flow<List<VodCompletedEpisode>> = flowOf(emptyList())
    fun record(sourceKey: String, entry: VodProgressEntry, completed: Boolean = false)
}