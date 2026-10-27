package com.yourapp.iptv.data.recovery

import com.yourapp.iptv.domain.recovery.*
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StoredAppDataResetTest {
    @Test fun startupChecksBeforeAllowingNormalContentAndNeverErasesAutomatically() = runTest {
        val journal = ResetMemory();val eraser = ResetEraser()
        val reset = StoredAppDataReset(journal,eraser,backgroundScope,StandardTestDispatcher(testScheduler))
        assertTrue(reset.state.value.blocked);reset.request();assertEquals(0,eraser.calls)
        reset.initialize();assertFalse(reset.state.value.blocked);assertEquals(0,eraser.calls)
    }
    @Test fun interruptedResetStaysBlockedOnEveryNewProcessWithoutErasingOnItsOwn() = runTest {
        val journal = ResetMemory().apply { marked = true };val eraser = ResetEraser()
        repeat(3) {
            val reset = StoredAppDataReset(journal,eraser,backgroundScope,StandardTestDispatcher(testScheduler))
            reset.initialize();assertEquals(AppResetProblem.INTERRUPTED,reset.state.value.problem)
            assertTrue(reset.state.value.blocked)
        }
        assertEquals(0,eraser.calls);assertTrue(journal.marked)
    }
    @Test fun intentIsDurableAndContentBlockedBeforeAndroidReceivesTheRequest() = runTest {
        val journal = ResetMemory();val eraser = ResetEraser()
        val reset = StoredAppDataReset(journal,eraser,backgroundScope,StandardTestDispatcher(testScheduler))
        reset.initialize()
        eraser.before = { assertTrue(journal.marked);assertTrue(reset.state.value.blocked) }
        reset.request();assertEquals(1,eraser.calls);assertEquals(1,journal.writes)
        assertEquals(AppResetPhase.WAITING_FOR_ANDROID,reset.state.value.phase)
        reset.initialize();assertTrue(reset.state.value.blocked)
    }
    @Test fun acceptingARequestDoesNotMeanDeletionCompletedOrOldContentMayBeShown() = runTest {
        val journal = ResetMemory();val eraser = ResetEraser()
        val reset = StoredAppDataReset(journal,eraser,backgroundScope,StandardTestDispatcher(testScheduler))
        reset.initialize();reset.request()
        repeat(5) { reset.request() }
        assertEquals(1,eraser.calls)
        advanceTimeBy(15_000);runCurrent()
        assertEquals(AppResetProblem.NOT_FINISHED,reset.state.value.problem)
        assertTrue(reset.state.value.blocked);assertTrue(journal.marked)
        reset.request();assertEquals(2,eraser.calls)
    }
    @Test fun failureToSaveTheIntentNeverCallsAndroid() = runTest {
        val journal = ResetMemory().apply { failWrite = true };val eraser = ResetEraser()
        val reset = StoredAppDataReset(journal,eraser,backgroundScope,StandardTestDispatcher(testScheduler))
        reset.initialize();reset.request()
        assertEquals(AppResetProblem.JOURNAL_WRITE,reset.state.value.problem)
        assertTrue(reset.state.value.blocked);assertEquals(0,eraser.calls)
        journal.failWrite = false;reset.request();assertEquals(1,eraser.calls);assertTrue(journal.marked)
    }
    @Test fun failureToCheckTheIntentAllowsOnlyRecheckingNotErasure() = runTest {
        val journal = ResetMemory().apply { failRead = true };val eraser = ResetEraser()
        val reset = StoredAppDataReset(journal,eraser,backgroundScope,StandardTestDispatcher(testScheduler))
        reset.initialize();assertEquals(AppResetProblem.JOURNAL_READ,reset.state.value.problem)
        reset.request();assertEquals(0,eraser.calls)
        journal.failRead = false;reset.initialize();assertFalse(reset.state.value.blocked)
    }
    @Test fun androidRejectionAndExceptionsKeepTheMarkerAndNeverReturnToNormalUi() = runTest {
        for (fail in listOf(false,true)) {
            val journal = ResetMemory();val eraser = ResetEraser().apply {
                accepted = false;if (fail) failure = IOException("Synthetic system failure")
            }
            val reset = StoredAppDataReset(journal,eraser,backgroundScope,StandardTestDispatcher(testScheduler))
            reset.initialize();reset.request();reset.initialize()
            assertEquals(if (fail) AppResetProblem.SYSTEM_UNAVAILABLE else AppResetProblem.SYSTEM_REJECTED,reset.state.value.problem)
            assertTrue(reset.state.value.blocked);assertTrue(journal.marked)
        }
    }
    @Test fun sameProcessRemainsBlockedEvenIfTheSystemDoubleHasAlreadyRemovedAllFiles() = runTest {
        val journal = ResetMemory();val eraser = ResetEraser().apply { before = { journal.marked = false } }
        val dispatcher = StandardTestDispatcher(testScheduler)
        val old = StoredAppDataReset(journal,eraser,backgroundScope,dispatcher)
        old.initialize();old.request();old.initialize();assertTrue(old.state.value.blocked)
        val nextProcess = StoredAppDataReset(journal,eraser,backgroundScope,dispatcher)
        nextProcess.initialize();assertFalse(nextProcess.state.value.blocked)
    }
    @Test fun callerCancellationAfterConfirmationDoesNotInterruptTheIntentAndSystemRequest() = runBlocking {
        val journal = ResetMemory();val eraser = ResetEraser()
        val entered = CountDownLatch(1);val finish = CountDownLatch(1)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val reset = StoredAppDataReset(journal,eraser,scope)
        try {
            reset.initialize();journal.beforeWrite = { entered.countDown();check(finish.await(5,TimeUnit.SECONDS)) }
            val request = launch(Dispatchers.Default) { reset.request() }
            assertTrue(entered.await(5,TimeUnit.SECONDS));request.cancel();finish.countDown();request.join()
            assertTrue(journal.marked);assertEquals(1,eraser.calls);assertTrue(reset.state.value.blocked)
        } finally { finish.countDown();scope.coroutineContext[Job]!!.cancelAndJoin() }
    }
    @Test fun simultaneousRequestsCannotCommitTwoIntentsOrCallAndroidTwice() = runBlocking {
        val journal = ResetMemory();val eraser = ResetEraser()
        val entered = CountDownLatch(1);val finish = CountDownLatch(1)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val reset = StoredAppDataReset(journal,eraser,scope)
        try {
            reset.initialize();eraser.before = { entered.countDown();check(finish.await(5,TimeUnit.SECONDS)) }
            val first = launch(Dispatchers.Default) { reset.request() }
            assertTrue(entered.await(5,TimeUnit.SECONDS))
            withTimeout(2000) { reset.request() }
            finish.countDown();first.join()
            assertEquals(1,journal.writes);assertEquals(1,eraser.calls)
        } finally { finish.countDown();scope.coroutineContext[Job]!!.cancelAndJoin() }
    }

}