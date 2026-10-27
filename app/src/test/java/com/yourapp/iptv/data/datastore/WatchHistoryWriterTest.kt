package com.yourapp.iptv.data.datastore

import com.yourapp.iptv.domain.model.*
import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WatchHistoryWriterTest {
    @Test fun recordsRemoveAndClearAreOrderedBarriers() = runTest {
        val trace = mutableListOf<String>()
        val writer = WatchHistoryWriter(backgroundScope) { work -> trace += when (work) {
            is HistoryWrite.Records -> work.batch.entries().flatMap { it.second.apply(emptyList()) }.joinToString { it.channelId }
            is HistoryWrite.Edit -> work.remove ?: "clear"
        } }
        writer.record("s",WatchHistoryEntry("a",1));writer.record("s",WatchHistoryEntry("b",2))
        val remove = async(start = CoroutineStart.UNDISPATCHED) { writer.edit("s","remove") }
        writer.record("s",WatchHistoryEntry("c",3))
        val clear = async(start = CoroutineStart.UNDISPATCHED) { writer.edit("s",null) }
        writer.record("s",WatchHistoryEntry("d",4));runCurrent();remove.await();clear.await()
        assertEquals(listOf("b, a","remove","c","clear","d"),trace)
    }
    @Test fun editCapacityIncludesAcceptedCommandsAndOverflowCannotDropThem() = runTest {
        val trace = mutableListOf<HistoryWrite>()
        val writer = WatchHistoryWriter(backgroundScope) { trace.add(it) }
        val accepted = List(WatchHistoryWriter.MAX_EDITS) { async(start = CoroutineStart.UNDISPATCHED) { writer.edit("s","$it") } }
        assertTrue(runCatching { writer.edit("s","overflow") }.exceptionOrNull() is IOException)
        assertTrue(writer.record("s",WatchHistoryEntry("latest",1)))
        runCurrent();accepted.forEach { it.await() }
        assertEquals((0 until WatchHistoryWriter.MAX_EDITS).map(Int::toString),trace.filterIsInstance<HistoryWrite.Edit>().map { it.remove })
        assertEquals("latest",trace.filterIsInstance<HistoryWrite.Records>().single().batch.entries().single().second.apply(emptyList()).single().channelId)
    }
    @Test fun cancellingTheCallingScreenDoesNotRetractAnAcceptedEdit() = runTest {
        val started = CompletableDeferred<Unit>();val finish = CompletableDeferred<Unit>();var applied = false
        val writer = WatchHistoryWriter(backgroundScope) { started.complete(Unit);finish.await();applied = true }
        val caller = launch(start = CoroutineStart.UNDISPATCHED) { writer.edit("s",null) }
        runCurrent();started.await();caller.cancel();finish.complete(Unit);runCurrent()
        assertTrue(applied)
    }
    @Test fun ordinaryFailureCompletesItsReplyAndDoesNotKillFollowingWork() = runTest {
        val writer = WatchHistoryWriter(backgroundScope) { if ((it as HistoryWrite.Edit).remove == "fail") throw IOException("Synthetic write failure") }
        val failed = async(start = CoroutineStart.UNDISPATCHED) { runCatching { writer.edit("s","fail") } }
        val next = async(start = CoroutineStart.UNDISPATCHED) { runCatching { writer.edit("s","next") } }
        runCurrent();assertTrue(failed.await().isFailure);assertTrue(next.await().isSuccess)
    }
    @Test fun stoppingBeforeTheWorkerBodyStartsCompletesAllAcceptedReplies() = runTest {
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val writer = WatchHistoryWriter(scope) { fail("Stopped writer must not run") }
        val callers = List(4) { async(start = CoroutineStart.UNDISPATCHED) { runCatching { writer.edit("s","$it") } } }
        scope.cancel();runCurrent()
        withTimeout(1000) { callers.forEach { assertTrue(it.await().isFailure) } }
        assertFalse(writer.record("s",WatchHistoryEntry("a",1)))
        assertTrue(runCatching { writer.edit("s",null) }.exceptionOrNull() is IOException)
    }
    @Test fun cancellationDuringAnOperationCompletesExecutingAndQueuedReplies() = runTest {
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val entered = CompletableDeferred<Unit>();val never = CompletableDeferred<Unit>()
        val writer = WatchHistoryWriter(scope) { entered.complete(Unit);never.await() }
        val first = async(start = CoroutineStart.UNDISPATCHED) { runCatching { writer.edit("s","first") } }
        val second = async(start = CoroutineStart.UNDISPATCHED) { runCatching { writer.edit("s","second") } }
        runCurrent();entered.await();scope.cancel();runCurrent()
        withTimeout(1000) {
            assertTrue(first.await().exceptionOrNull() is IOException)
            assertTrue(second.await().exceptionOrNull() is IOException)
        }
        assertFalse(writer.record("s",WatchHistoryEntry("a",1)))
    }
    @Test fun aFatalWorkerFailureIsNotSwallowedAndStillReleasesAllReplies() = runTest {
        val failures = mutableListOf<Throwable>()
        val handler = CoroutineExceptionHandler { _,error -> failures += error }
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler) + handler)
        try {
            val writer = WatchHistoryWriter(scope) { throw AssertionError("Synthetic worker failure") }
            val first = async(start = CoroutineStart.UNDISPATCHED) { runCatching { writer.edit("s","first") } }
            val second = async(start = CoroutineStart.UNDISPATCHED) { runCatching { writer.edit("s","second") } }
            runCurrent()
            withTimeout(1000) { assertTrue(first.await().isFailure);assertTrue(second.await().isFailure) }
            assertTrue(failures.single() is AssertionError);assertFalse(writer.record("s",WatchHistoryEntry("a",1)))
        } finally { scope.cancel() }
    }
    @Test fun aScopeAlreadyCancelledRejectsRecordsAndEditsWithoutHanging() = runTest {
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler));scope.cancel()
        val writer = WatchHistoryWriter(scope) { fail("No work after shutdown") };runCurrent()
        assertFalse(writer.record("s",WatchHistoryEntry("a",1)))
        withTimeout(1000) { assertTrue(runCatching { writer.edit("s",null) }.isFailure) }
    }
    @Test fun aHundredThousandQueuedRecordsRequireOneBoundedWriteRun() = runTest {
        var runs = 0;var count = 0
        val writer = WatchHistoryWriter(backgroundScope) { val work = it as HistoryWrite.Records;runs++;count = work.batch.entryCount }
        repeat(100_000) { assertTrue(writer.record("s",WatchHistoryEntry("id-$it",it.toLong()))) }
        runCurrent();assertEquals(1,runs);assertEquals(20,count)
    }
    @Test fun interleavedBarriersBoundEveryPendingRunWithoutDroppingAcceptedEdits() = runTest {
        val started = CompletableDeferred<Unit>();val release = CompletableDeferred<Unit>()
        val runs = mutableListOf<HistoryRecordBatch>();var appliedEdits = 0
        val writer = WatchHistoryWriter(backgroundScope) { work ->
            if (work is HistoryWrite.Records) {
                if (runs.isEmpty()) { started.complete(Unit);release.await() }
                runs.add(work.batch)
            } else appliedEdits++
        }
        writer.record("seed",WatchHistoryEntry("seed",1));runCurrent();started.await()
        val edits = (0 until WatchHistoryWriter.MAX_EDITS).map { index ->
            repeat(HistoryRecordBatch.MAX_SOURCES) { source ->
                repeat(30) { assertTrue(writer.record("source-$source",WatchHistoryEntry("id-$it",it.toLong()))) }
            }
            assertFalse(writer.record("overflow",WatchHistoryEntry("rejected",1)))
            async(start = CoroutineStart.UNDISPATCHED) { writer.edit("source-0","$index") }
        }
        writer.record("tail",WatchHistoryEntry("latest",1));release.complete(Unit);runCurrent();edits.forEach { it.await() }
        assertEquals(WatchHistoryWriter.MAX_EDITS,appliedEdits)
        assertEquals(WatchHistoryWriter.MAX_EDITS + 2,runs.size)
        assertTrue(runs.all { it.sourceCount <= 32 && it.entryCount <= 32 * WatchHistory.LIMIT })
    }

}