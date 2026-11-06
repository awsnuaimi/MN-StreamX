package mnstudio.apps.mnstreamx.domain.performance

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PresentationWorkTest {
    @Test fun listComputationWaitsForThePreviousTaskWithoutBlockingTheCaller() = runTest {
        val queue = PresentationWork();val dispatcher = StandardTestDispatcher(testScheduler)
        val release = CompletableDeferred<Unit>();val order = mutableListOf<String>()
        val first = launch { queue.run(dispatcher) { order.add("first");release.await();order.add("done") } }
        runCurrent()
        val second = launch { queue.run(dispatcher) { order.add("second") } };runCurrent()
        assertEquals(listOf("first"),order)
        release.complete(Unit);joinAll(first,second)
        assertEquals(listOf("first","done","second"),order)
    }
    @Test fun obsoleteQueuedTasksAreCancelledBeforeEnteringComputation() = runTest {
        val queue = PresentationWork();val dispatcher = StandardTestDispatcher(testScheduler)
        val release = CompletableDeferred<Unit>()
        val first = launch { queue.run(dispatcher) { release.await() } };runCurrent()
        val old = launch { queue.run(dispatcher) { fail("Obsolete screen ran") } };runCurrent()
        old.cancelAndJoin();release.complete(Unit);first.join()
        assertEquals("fresh",queue.run(dispatcher) { "fresh" })
    }
    @Test fun cancellationAndFailureAlwaysReleaseTheComputationSlot() = runTest {
        val queue = PresentationWork();val dispatcher = StandardTestDispatcher(testScheduler)
        val first = launch { queue.run(dispatcher) { awaitCancellation() } };runCurrent();first.cancelAndJoin()
        assertTrue(runCatching { queue.run(dispatcher) { error("Synthetic failure") } }.isFailure)
        assertEquals(7,queue.run(dispatcher) { 7 })
        assertTrue(PerformanceJournal.snapshot().any { it.stage == PerformanceStage.PRESENTATION_WAIT })
        assertTrue(PerformanceJournal.snapshot().any { it.stage == PerformanceStage.PRESENTATION_WORK })
    }
}