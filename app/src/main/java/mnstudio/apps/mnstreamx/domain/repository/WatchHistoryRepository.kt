package mnstudio.apps.mnstreamx.domain.repository

import mnstudio.apps.mnstreamx.domain.model.WatchHistoryEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

interface WatchHistoryRepository {
    fun observe(sourceKey: String): Flow<List<WatchHistoryEntry>>
    fun recordStarted(sourceKey: String, entry: WatchHistoryEntry)
    suspend fun remove(sourceKey: String, channelId: String)
    suspend fun clear(sourceKey: String)

    companion object {
        val Disabled = object : WatchHistoryRepository {
            override fun observe(sourceKey: String) = flowOf(emptyList<WatchHistoryEntry>())
            override fun recordStarted(sourceKey: String, entry: WatchHistoryEntry) = Unit
            override suspend fun remove(sourceKey: String, channelId: String) = Unit
            override suspend fun clear(sourceKey: String) = Unit
        }
    }
}