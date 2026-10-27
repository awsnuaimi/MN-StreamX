package com.yourapp.iptv.viewmodel

import com.yourapp.iptv.domain.recovery.*
import com.yourapp.iptv.domain.security.SecureStorageFailure
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class AppResetViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { Dispatchers.resetMain() }
    private class Reset : AppDataReset {
        override val state = MutableStateFlow(AppResetState(AppResetPhase.READY))
        var requests = 0;var reads = 0
        override suspend fun initialize() { reads++ }
        override suspend fun request() { requests++;state.value = AppResetState(AppResetPhase.WAITING_FOR_ANDROID) }
    }
    @Test fun noResetCanBeRequestedWithoutBothAcknowledgementAndFinalConfirmation() = runTest(dispatcher) {
        val reset = Reset();val vm = AppResetViewModel(reset);advanceUntilIdle()
        vm.confirm();vm.offer(SecureStorageFailure.KEY_MISSING);vm.confirm();vm.review();vm.confirm()
        advanceUntilIdle();assertEquals(0,reset.requests)
        vm.acknowledge(true);vm.confirm();advanceUntilIdle();assertEquals(0,reset.requests)
        vm.review();vm.confirm();vm.confirm();advanceUntilIdle();assertEquals(1,reset.requests)
    }
    @Test fun temporaryUnknownAndHealthyStatesNeverOfferDestructiveRecovery() = runTest(dispatcher) {
        val reset = Reset();val vm = AppResetViewModel(reset);advanceUntilIdle()
        vm.offer(null);assertNull(vm.confirmation.value)
        vm.offer(SecureStorageFailure.TEMPORARILY_UNAVAILABLE);assertNull(vm.confirmation.value)
        assertEquals(0,reset.requests)
    }
    @Test fun eachEligibleFailureStartsWithAnUnacknowledgedReview() = runTest(dispatcher) {
        val vm = AppResetViewModel(Reset());advanceUntilIdle()
        for (problem in SecureStorageFailure.entries.filter(AppResetPolicy::mayOffer)) {
            vm.offer(problem);assertEquals(ResetConfirmation(),vm.confirmation.value);vm.cancel()
        }
    }
    @Test fun cancelAtEitherStepNeverErasesAndRequiresNewAcknowledgement() = runTest(dispatcher) {
        val reset = Reset();val vm = AppResetViewModel(reset);advanceUntilIdle()
        vm.offer(SecureStorageFailure.KEY_MISSING);vm.acknowledge(true);vm.cancel();vm.confirm()
        vm.offer(SecureStorageFailure.KEY_MISSING);assertFalse(vm.confirmation.value!!.acknowledged)
        vm.acknowledge(true);vm.review();vm.cancel();vm.confirm();advanceUntilIdle()
        assertEquals(0,reset.requests)
    }
    @Test fun anInterruptedResetRequiresNewReviewAndCanNeverCancelIntoNormalContent() = runTest(dispatcher) {
        val reset = Reset().apply { state.value = AppResetState(AppResetPhase.RECOVERY_REQUIRED,AppResetProblem.INTERRUPTED) }
        val vm = AppResetViewModel(reset);advanceUntilIdle()
        vm.resume();assertNotNull(vm.confirmation.value);vm.cancel()
        assertTrue(vm.state.value.blocked);assertEquals(0,reset.requests)
        vm.resume();vm.acknowledge(true);vm.review();vm.confirm();advanceUntilIdle();assertEquals(1,reset.requests)
    }
    @Test fun unreadableJournalCanOnlyBeRecheckedAndDoesNotEnableWiping() = runTest(dispatcher) {
        val reset = Reset().apply { state.value = AppResetState(AppResetPhase.RECOVERY_REQUIRED,AppResetProblem.JOURNAL_READ) }
        val vm = AppResetViewModel(reset);advanceUntilIdle();vm.resume();vm.confirm();vm.retryCheck();advanceUntilIdle()
        assertNull(vm.confirmation.value);assertEquals(0,reset.requests);assertEquals(2,reset.reads)
    }
}