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
import org.junit.Assert.*
import org.junit.Test

/** Deterministic backpressure test; avoids fragile timing-based performance claims. */
class VodWriteQueueBurstStressTest {
    private fun snapshot(id: Long) = VodCatalogSnapshot.Saved(
        VodCatalog(emptyList(), listOf(VodItem("$id", "Item $id", null))), id)

    @Test fun burstUpdatesAreCoalescedAndBoundedBeforeDiskCanProceed() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val finished = CompletableDeferred<Unit>()
        val written = mutableListOf<String>()
        try {
            val queue = VodSnapshotWriteQueue(persist = { key, saved ->
                if (key == "active") {
                    entered.complete(Unit)
                    release.await()
                }
                synchronized(written) {
                    written += "$key:${saved.writtenAtMs}"
                    if (written.size == 5) finished.complete(Unit)
                }
            }, scope = scope)
            queue.offer("active", snapshot(1))
            withTimeout(5_000) { entered.await() }
            repeat(12) { queue.offer("pending-$it", snapshot(it + 2L)) }
            repeat(2_000) { queue.offer("pending-10", snapshot(100L + it)) }
            release.complete(Unit)
            withTimeout(10_000) { finished.await() }
            assertEquals(listOf("active:1", "pending-8:10", "pending-9:11",
                "pending-10:2099", "pending-11:13"), synchronized(written) { written.toList() })
        } finally {
            release.complete(Unit)
            scope.cancel()
        }
    }
}