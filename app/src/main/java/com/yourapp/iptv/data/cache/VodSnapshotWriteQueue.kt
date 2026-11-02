package com.yourapp.iptv.data.cache

import com.yourapp.iptv.domain.performance.PerformanceJournal
import com.yourapp.iptv.domain.performance.PerformanceStage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Best-effort, bounded disk persistence. The provider result is published before
 * expensive compression finishes. Only one writer runs, so an older snapshot
 * cannot overwrite a newer completed refresh of the same key.
 */
internal class VodSnapshotWriteQueue(
    private val persist: suspend (String, VodCatalogSnapshot.Saved) -> Unit,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    private val lock = Any()
    private val pending = LinkedHashMap<String, VodCatalogSnapshot.Saved>()
    private var worker: Job? = null

    fun offer(key: String, value: VodCatalogSnapshot.Saved) {
        synchronized(lock) {
            // Replacing an existing key retains its position in the write order.
            pending[key] = value
            if (pending.size > MAX_PENDING) pending.remove(pending.keys.first())
            if (worker?.isActive != true) worker = scope.launch { drain() }
        }
    }

    private suspend fun drain() {
        while (true) {
            val next = synchronized(lock) {
                val entry = pending.entries.firstOrNull()
                if (entry == null) {
                    worker = null
                    null
                } else {
                    pending.remove(entry.key)
                    entry.key to entry.value
                }
            } ?: return
            try {
                PerformanceJournal.measure(PerformanceStage.VOD_CACHE_WRITE) {
                    persist(next.first, next.second)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Files in the cache directory are disposable; never fail playback.
            }
        }
    }

    private companion object {
        const val MAX_PENDING = 4
    }
}