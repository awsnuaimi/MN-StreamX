package com.yourapp.iptv.viewmodel

import androidx.lifecycle.ViewModelStore
import com.yourapp.iptv.domain.diagnostics.*
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.domain.repository.DiagnosticsRepository
import com.yourapp.iptv.testing.*
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class DiagnosticMeasurementsTest {
    private val dispatcher = StandardTestDispatcher()
    private val owner = ViewModelStore()
    private var snapshots = 0
    private var probes = 0
    private var exports = 0
    private var read: suspend () -> DiagnosticSnapshot = { diagnosticSnapshot() }
    private var probe: suspend () -> Unit = {}
    private var export: suspend () -> Unit = {}
    private val check = DiagnosticCheck(DiagnosticArea.ACCOUNT, DiagnosticStatus.PASS, DiagnosticIssue.OK)
    private val repository = object : DiagnosticsRepository {
        override suspend fun snapshot(): DiagnosticSnapshot { snapshots++; return read() }
        override fun sourceKind(source: AppSettings) = DiagnosticSource.XTREAM
        override suspend fun probe(source: AppSettings, onCheck: (DiagnosticCheck) -> Unit) {
            probes++; probe(); onCheck(check)
        }
        override suspend fun export(snapshot: DiagnosticSnapshot, source: DiagnosticSource, sourceKey: String?,
            checks: List<DiagnosticCheck>, events: List<DiagnosticEvent>, language: HomeLanguage): File {
            exports++; export(); return File("synthetic-report.txt")
        }
    }
    private fun model() = DiagnosticsViewModel(SearchAccounts(), repository).also { owner.put("measurements", it) }
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { owner.clear(); dispatcher.scheduler.runCurrent(); Dispatchers.resetMain() }

    @Test fun refreshIsLocalCoalescesRapidPressesAndPreservesSourceCheckResults() = runTest(dispatcher) {
        val model = model(); runCurrent(); model.runChecks(); runCurrent()
        val gate = CompletableDeferred<Unit>()
        read = { gate.await(); diagnosticSnapshot().copy(heapUsed = 123) }
        model.refreshMeasurements(); model.refreshMeasurements(); runCurrent()
        model.runChecks(); model.export(HomeLanguage.GERMAN); runCurrent()
        assertTrue(model.state.value.refreshing); assertEquals(listOf(check), model.state.value.checks)
        assertEquals(3, snapshots); assertEquals(1, probes); assertEquals(0, exports)
        gate.complete(Unit); runCurrent()
        assertFalse(model.state.value.refreshing); assertEquals(123L, model.state.value.snapshot!!.heapUsed)
    }

    @Test fun leavingCancelsLocalWorkAndRejectsALateOldReadAfterANewerRefresh() = runTest(dispatcher) {
        val model = model(); runCurrent()
        val gate = CompletableDeferred<Unit>()
        var cancelled = false
        read = {
            try { withContext(NonCancellable) { gate.await() }; diagnosticSnapshot().copy(heapUsed = 999) }
            finally { cancelled = true }
        }
        model.refreshMeasurements(); runCurrent(); model.setVisible(false); runCurrent()
        model.refreshMeasurements(); runCurrent(); assertEquals(2, snapshots)
        model.setVisible(true)
        read = { diagnosticSnapshot().copy(heapUsed = 42) }
        model.refreshMeasurements(); runCurrent(); gate.complete(Unit); runCurrent()
        assertTrue(cancelled); assertEquals(42L, model.state.value.snapshot!!.heapUsed)
        assertFalse(model.state.value.refreshing); assertEquals(0, probes)
    }

    @Test fun anInterruptedInitialReadIsRetriedWhenTheScreenResumes() = runTest(dispatcher) {
        var cancelled = false
        read = { try { awaitCancellation() } finally { cancelled = true } }
        val model = model(); runCurrent(); model.setVisible(false); runCurrent()
        assertTrue(cancelled); assertNull(model.state.value.snapshot)
        read = { diagnosticSnapshot() }
        model.setVisible(true); runCurrent()
        assertNotNull(model.state.value.snapshot); assertEquals(2, snapshots)
        assertFalse(model.state.value.refreshing)
    }

    @Test fun aStalledRefreshTimesOutKeepsThePreviousSnapshotAndCanBeRetried() = runTest(dispatcher) {
        val model = model(); runCurrent(); val previous = model.state.value.snapshot
        read = { awaitCancellation() }
        model.refreshMeasurements(); runCurrent(); advanceTimeBy(10_000); runCurrent()
        assertFalse(model.state.value.refreshing); assertTrue(model.state.value.failure)
        assertEquals(previous, model.state.value.snapshot)
        read = { diagnosticSnapshot().copy(heapUsed = 321) }
        model.refreshMeasurements(); runCurrent()
        assertFalse(model.state.value.failure); assertEquals(321L, model.state.value.snapshot!!.heapUsed)
    }

    @Test fun refreshDoesNotInterruptAnExplicitSourceCheckOrAnExport() = runTest(dispatcher) {
        val model = model(); runCurrent()
        probe = { awaitCancellation() }
        model.runChecks(); runCurrent(); model.refreshMeasurements(); runCurrent()
        assertEquals(2, snapshots); assertTrue(model.state.value.busy)
        model.cancel(); runCurrent()
        export = { awaitCancellation() }
        model.export(HomeLanguage.GERMAN); runCurrent(); model.refreshMeasurements(); runCurrent()
        // The export refreshes its local snapshot once before writing the report.
        assertTrue(model.state.value.exporting); assertEquals(3, snapshots)
        assertEquals(1, probes); assertEquals(1, exports)
    }

    @Test fun viewModelDestructionCancelsARefreshWithoutLeakingItsJob() = runTest(dispatcher) {
        val model = model(); runCurrent(); var cancelled = false
        read = { try { awaitCancellation() } finally { cancelled = true } }
        model.refreshMeasurements(); runCurrent(); owner.clear(); runCurrent()
        assertTrue(cancelled); assertFalse(model.state.value.refreshing)
    }
}