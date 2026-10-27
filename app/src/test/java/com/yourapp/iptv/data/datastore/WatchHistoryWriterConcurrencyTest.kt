package com.yourapp.iptv.data.datastore

import com.yourapp.iptv.domain.model.WatchHistoryEntry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class WatchHistoryWriterConcurrencyTest {
    @Test fun producerRemainsNonBlockingWhileTheWriterIsInsideBlockingStorage() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val producer = Executors.newSingleThreadExecutor()
        val entered = CountDownLatch(1);val finish = CountDownLatch(1)
        try {
            val writer = WatchHistoryWriter(scope) { entered.countDown();check(finish.await(10,TimeUnit.SECONDS)) }
            assertTrue(writer.record("source",WatchHistoryEntry("seed",0)))
            assertTrue(entered.await(5,TimeUnit.SECONDS))
            producer.submit {
                repeat(10_000) { check(writer.record("source",WatchHistoryEntry("id-$it",it.toLong()))) }
            }.get(3,TimeUnit.SECONDS)
            assertEquals(1L,finish.count)
        } finally { finish.countDown();producer.shutdownNow();scope.coroutineContext[Job]!!.cancelAndJoin() }
    }
    @Test fun concurrentExplicitEditsAreSerializedAndEveryAcceptedCallerGetsAReply() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val entered = CountDownLatch(1);val finish = CountDownLatch(1)
        val trace = mutableListOf<String?>()
        try {
            val writer = WatchHistoryWriter(scope) { work ->
                if (work is HistoryWrite.Edit) synchronized(trace) { trace.add(work.remove) }
                else { entered.countDown();check(finish.await(10,TimeUnit.SECONDS)) }
            }
            writer.record("s",WatchHistoryEntry("seed",0));assertTrue(entered.await(5,TimeUnit.SECONDS))
            val callers = List(WatchHistoryWriter.MAX_EDITS) { index -> async(Dispatchers.Default,start = CoroutineStart.UNDISPATCHED) {
                writer.edit("s","$index")
            } }
            assertTrue(runCatching { writer.edit("s","overflow") }.isFailure)
            finish.countDown();withTimeout(5000) { callers.awaitAll() }
            assertEquals((0 until WatchHistoryWriter.MAX_EDITS).map(Int::toString),synchronized(trace) { trace.toList() })
        } finally { finish.countDown();scope.coroutineContext[Job]!!.cancelAndJoin() }
    }
}