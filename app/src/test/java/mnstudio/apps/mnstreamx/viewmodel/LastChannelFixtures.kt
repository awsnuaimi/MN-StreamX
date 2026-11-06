package mnstudio.apps.mnstreamx.viewmodel

import mnstudio.apps.mnstreamx.domain.model.WatchHistoryEntry
import mnstudio.apps.mnstreamx.domain.repository.WatchHistoryRepository
import kotlinx.coroutines.flow.*

internal class RecallHistory(val memory: HistoryMemory = HistoryMemory()) : WatchHistoryRepository by memory {
    var subscriptions = 0
    var active = 0
    override fun observe(sourceKey: String) = flow {
        subscriptions++; active++
        try { emitAll(memory.observe(sourceKey)) }
        finally { active-- }
    }
    fun set(source: String, vararg ids: String) {
        memory.records(source).value = ids.mapIndexed { i, id -> WatchHistoryEntry(id, 100L - i) }
    }
}