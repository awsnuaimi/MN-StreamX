package com.yourapp.iptv.data.cache

import com.yourapp.iptv.domain.model.VodCatalog
import com.yourapp.iptv.domain.model.VodItem
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test

class VodSnapshotWriteQueueTest {
    private fun saved(id: String) = VodCatalogSnapshot.Saved(
        VodCatalog(emptyList(), listOf(VodItem(id, id, null))), id.toLong())

    @Test fun pendingUpdatesForSameKeyAreCollapsedAndPersistedInOrder() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val completed = CompletableDeferred<Unit>()
        val writes = mutableListOf<String>()
        try {
            val queue = VodSnapshotWriteQueue(persist = { key, snapshot ->
                val id = snapshot.catalog.items.single().id
                if (id == "1") {
                    started.complete(Unit)
                    release.await()
                }
                synchronized(writes) {
                    writes += "$key:$id"
                    if (writes.size == 3) completed.complete(Unit)
                }
            }, scope = scope)
            queue.offer("a", saved("1"))
            withTimeout(5_000) { started.await() }
            queue.offer("a", saved("2"))
            queue.offer("a", saved("3"))
            queue.offer("b", saved("4"))
            release.complete(Unit)
            withTimeout(5_000) { completed.await() }
            assertEquals(listOf("a:1", "a:3", "b:4"), synchronized(writes) { writes.toList() })
        } finally {
            release.complete(Unit)
            scope.cancel()
        }
    }

    @Test fun aFailedCacheWriteDoesNotPreventTheNextSnapshot() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val recovered = CompletableDeferred<Unit>()
        try {
            val queue = VodSnapshotWriteQueue(persist = { _, value ->
                if (value.writtenAtMs == 1L) error("synthetic disk failure")
                recovered.complete(Unit)
            }, scope = scope)
            queue.offer("a", saved("1"))
            queue.offer("b", saved("2"))
            withTimeout(5_000) { recovered.await() }
        } finally { scope.cancel() }
    }
}