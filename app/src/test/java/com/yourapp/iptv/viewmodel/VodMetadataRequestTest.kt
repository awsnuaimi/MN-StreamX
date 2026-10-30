package com.yourapp.iptv.viewmodel

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VodMetadataRequestTest {
    @Test fun failurePublicationAcceptsAnImmediateRetry() = runTest {
        val request = VodMetadataRequest(backgroundScope)
        val published = mutableListOf<String>()
        var loads = 0
        request.launch {
            loads++
            complete {
                published.add("failed")
                request.launch { loads++; complete { published.add("ready") } }
            }
        }
        runCurrent()
        assertEquals(2, loads)
        assertEquals(listOf("failed", "ready"), published)
    }

    @Test fun stateFlowObserverCanRetryDuringFailurePublication() = runTest {
        val request = VodMetadataRequest(backgroundScope)
        val state = MutableStateFlow("idle")
        var loads = 0
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            state.collect { value ->
                if (value == "failed") request.launch {
                    loads++
                    complete { state.value = "ready" }
                }
            }
        }
        request.launch { loads++; complete { state.value = "failed" } }
        runCurrent()
        assertEquals("ready", state.value)
        assertEquals(2, loads)
    }

    @Test fun pendingAndRunningRequestsRejectDuplicateLoads() = runTest {
        val request = VodMetadataRequest(backgroundScope)
        val gate = CompletableDeferred<Unit>()
        var loads = 0
        request.launch { loads++; gate.await(); complete {} }
        request.launch { loads++ }
        runCurrent()
        request.launch { loads++ }
        runCurrent()
        assertEquals(1, loads)
        gate.complete(Unit)
        runCurrent()
        request.launch { loads++; complete {} }
        runCurrent()
        assertEquals(2, loads)
    }

    @Test fun finishingTheOldCoroutineCannotReleaseTheNewRetry() = runTest {
        val request = VodMetadataRequest(backgroundScope)
        val gate = CompletableDeferred<Unit>()
        var loads = 0
        request.launch {
            loads++
            complete { request.launch { loads++; gate.await(); complete {} } }
        }
        runCurrent()
        request.launch { loads++ }
        runCurrent()
        assertEquals(2, loads)
        gate.complete(Unit)
        runCurrent()
    }

    @Test fun eagerCompletionDoesNotOverwriteTheJobOfANestedRetry() = runTest {
        val owner = SupervisorJob()
        val scope = CoroutineScope(owner + UnconfinedTestDispatcher(testScheduler))
        val request = VodMetadataRequest(scope)
        var cancelled = false
        try {
            request.launch {
                complete {
                    request.launch {
                        try { awaitCancellation() } finally { cancelled = true }
                    }
                }
            }
            request.cancel()
            runCurrent()
            assertTrue(cancelled)
        } finally { owner.cancel() }
    }

    @Test fun cancellingBeforeStartSkipsTheBodyAndAllowsAnotherRequest() = runTest {
        val request = VodMetadataRequest(backgroundScope)
        var loads = 0
        request.launch { loads++ }
        request.cancel()
        request.launch { loads++; complete {} }
        runCurrent()
        assertEquals(1, loads)
    }

    @Test fun cancelledWorkCannotPublishOverOrReleaseItsReplacement() = runTest {
        val request = VodMetadataRequest(backgroundScope)
        val oldGate = CompletableDeferred<Unit>()
        val newGate = CompletableDeferred<Unit>()
        val published = mutableListOf<String>()
        var loads = 0
        request.launch {
            loads++
            withContext(NonCancellable) { oldGate.await() }
            complete { published.add("old") }
        }
        runCurrent()
        request.cancel()
        request.launch { loads++; newGate.await(); complete { published.add("new") } }
        runCurrent()
        oldGate.complete(Unit)
        runCurrent()
        request.launch { loads++ }
        runCurrent()
        assertEquals(2, loads)
        assertTrue(published.isEmpty())
        newGate.complete(Unit)
        runCurrent()
        assertEquals(listOf("new"), published)
    }

    @Test fun clearingTheOwnerCancelsWorkAndPreventsNewRequests() = runTest {
        val owner = SupervisorJob()
        val scope = CoroutineScope(owner + StandardTestDispatcher(testScheduler))
        val request = VodMetadataRequest(scope)
        var cancelled = false
        var unexpected = false
        request.launch {
            try { awaitCancellation() } finally { cancelled = true }
        }
        runCurrent()
        owner.cancel()
        runCurrent()
        request.launch { unexpected = true }
        runCurrent()
        assertTrue(cancelled)
        assertFalse(unexpected)
        assertTrue(owner.children.none())
    }

    @Test fun completionCanOnlyPublishOnce() = runTest {
        val request = VodMetadataRequest(backgroundScope)
        var publications = 0
        request.launch { complete { publications++ }; complete { publications++ } }
        runCurrent()
        assertEquals(1, publications)
    }

    @Test fun timeoutPublicationAlsoAcceptsAnImmediateRetry() = runTest {
        val request = VodMetadataRequest(backgroundScope)
        var retried = false
        request.launch {
            try { withTimeout(30_000) { awaitCancellation() } }
            catch (_: TimeoutCancellationException) {
                complete { request.launch { complete { retried = true } } }
            }
        }
        runCurrent()
        advanceTimeBy(30_000)
        runCurrent()
        assertTrue(retried)
    }

    @Test fun anExceptionReleasesOwnershipForTheNextRequest() = runTest {
        val owner = SupervisorJob()
        var failures = 0
        val scope = CoroutineScope(owner + StandardTestDispatcher(testScheduler) +
            CoroutineExceptionHandler { _, _ -> failures++ })
        val request = VodMetadataRequest(scope)
        var retried = false
        try {
            request.launch { error("Synthetic metadata failure") }
            runCurrent()
            request.launch { complete { retried = true } }
            runCurrent()
            assertEquals(1, failures)
            assertTrue(retried)
        } finally { owner.cancel() }
    }
}