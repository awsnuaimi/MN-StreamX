package mnstudio.apps.mnstreamx.viewmodel

import androidx.lifecycle.ViewModelStore
import mnstudio.apps.mnstreamx.domain.diagnostics.*
import mnstudio.apps.mnstreamx.domain.model.*
import mnstudio.apps.mnstreamx.domain.repository.DiagnosticsRepository
import mnstudio.apps.mnstreamx.testing.*
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class DiagnosticsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val owner = ViewModelStore()
    private val settings = SearchAccounts()
    private var probes = 0
    private var exports = 0
    private var snapshots = 0
    private var snapshotsAtExport = -1
    private var snapshotReader: suspend () -> DiagnosticSnapshot = { diagnosticSnapshot() }
    private var exportedSnapshot: DiagnosticSnapshot? = null
    private var exportedSource: String? = null
    private var block: suspend ((DiagnosticCheck) -> Unit) -> Unit = { emit -> emit(check()) }
    private var exported: suspend () -> File = { File("mnstreamx-diagnostics-synthetic.txt") }
    private val repository = object : DiagnosticsRepository {
        override suspend fun snapshot(): DiagnosticSnapshot { snapshots++;return snapshotReader() }
        override fun sourceKind(source: AppSettings) = if (source.hasSelectedSource()) DiagnosticSource.XTREAM else DiagnosticSource.NONE
        override suspend fun probe(source: AppSettings,onCheck: (DiagnosticCheck) -> Unit) { probes++;block(onCheck) }
        override suspend fun export(snapshot: DiagnosticSnapshot, source: DiagnosticSource, sourceKey: String?,
            checks: List<DiagnosticCheck>, events: List<DiagnosticEvent>, language: HomeLanguage): File {
            exports++;snapshotsAtExport = snapshots;exportedSnapshot = snapshot;exportedSource = sourceKey;return exported()
        }
    }
    @Before fun setup() { Dispatchers.setMain(dispatcher);DiagnosticJournal.clear() }
    @After fun cleanup() { owner.clear();dispatcher.scheduler.runCurrent();Dispatchers.resetMain();DiagnosticJournal.clear() }
    private fun model() = DiagnosticsViewModel(settings,repository).also { owner.put("diagnostics",it) }
    private fun check() = DiagnosticCheck(DiagnosticArea.ACCOUNT,DiagnosticStatus.PASS,DiagnosticIssue.OK,200)
    @Test fun enteringThePageReadsLocalEnvironmentButDoesNotContactTheProvider() = runTest(dispatcher) {
        val model = model();runCurrent()
        assertNotNull(model.state.value.snapshot);assertTrue(model.state.value.sourceReady)
        assertEquals(0,probes);assertEquals(0,exports)
        model.runChecks();model.runChecks();runCurrent()
        assertEquals(1,probes);assertEquals(listOf(check()),model.state.value.checks)
    }
    @Test fun refreshingMeasurementsDoesNotProbeProviderOrStartPlayback() = runTest(dispatcher) {
        val model = model();runCurrent()
        model.refreshMeasurements();model.refreshMeasurements();runCurrent()
        assertNotNull(model.state.value.snapshot)
        assertFalse(model.state.value.refreshing)
        assertEquals(0,probes)
        assertEquals(0,exports)
    }
    @Test fun aSourceSwitchCancelsTheOldChecksAndRejectsANonCooperativeLateCallback() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        block = { emit -> withContext(NonCancellable) { gate.await() };emit(check()) }
        val model = model();runCurrent();model.runChecks();runCurrent()
        settings.values.value = searchAccount("B");runCurrent();gate.complete(Unit);runCurrent()
        assertEquals(settings.key,model.state.value.sourceKey);assertTrue(model.state.value.checks.isEmpty());assertFalse(model.state.value.busy)
    }
    @Test fun cancellationAndLifecycleLossCannotFinishOrStartChecksInTheBackground() = runTest(dispatcher) {
        var cancelled = false
        block = { try { awaitCancellation() } finally { cancelled = true } }
        val model = model();runCurrent();model.runChecks();runCurrent();model.setVisible(false);runCurrent()
        assertTrue(cancelled);assertFalse(model.state.value.busy)
        assertEquals(DiagnosticIssue.CANCELLED,model.state.value.checks.last().issue)
        model.runChecks();runCurrent();assertEquals(1,probes)
    }
    @Test fun aStalledProbeHasAnOverallDeadlineAndLeavesAClearTimeoutResult() = runTest(dispatcher) {
        block = { awaitCancellation() }
        val model = model();runCurrent();model.runChecks();runCurrent();advanceTimeBy(30_000);runCurrent()
        assertFalse(model.state.value.busy);assertEquals(DiagnosticIssue.TIMEOUT,model.state.value.checks.last().issue)
    }
    @Test fun sharingIsExplicitUsesTheBoundSourceAndConsumesEachRequestOnce() = runTest(dispatcher) {
        val model = model();runCurrent();model.export(HomeLanguage.GERMAN);model.export(HomeLanguage.GERMAN);runCurrent()
        assertEquals(1,exports);assertEquals(settings.key,exportedSource);assertNotNull(model.state.value.shareFile)
        model.consumeShare();assertNull(model.state.value.shareFile)
        model.shareFailed();assertTrue(model.state.value.shareFailure)
    }
    @Test fun exportCapturesFreshLocalMeasurementsWithoutProbingProvider() = runTest(dispatcher) {
        val model = model();runCurrent()
        val initial = snapshots
        val expected = diagnosticSnapshot().copy(heapUsed = 123456,availableRam = 789012)
        snapshotReader = { expected }
        model.export(HomeLanguage.GERMAN);runCurrent()
        assertEquals(initial + 1,snapshots)
        assertEquals(snapshots,snapshotsAtExport)
        assertEquals(expected,exportedSnapshot)
        assertEquals(expected,model.state.value.snapshot)
        assertEquals(1,exports)
        assertEquals(0,probes)
    }
    @Test fun exportDoesNotProceedWhenLocalSnapshotFails() = runTest(dispatcher) {
        val model = model();runCurrent()
        val previous = model.state.value.snapshot
        snapshotReader = { error("Synthetic local snapshot failure") }
        model.export(HomeLanguage.GERMAN);runCurrent()
        assertEquals(0,exports);assertEquals(0,probes)
        assertTrue(model.state.value.failure);assertFalse(model.state.value.exporting)
        assertEquals(previous,model.state.value.snapshot);assertNull(model.state.value.shareFile)
    }
    @Test fun aHiddenPageCannotStartAnExport() = runTest(dispatcher) {
        val model = model();runCurrent()
        model.setVisible(false)
        model.export(HomeLanguage.GERMAN);runCurrent()
        assertEquals(0,exports)
    }
    @Test fun aSourceSwitchDuringSnapshotReadPreventsEvenStartingTheOldExport() = runTest(dispatcher) {
        val model = model();runCurrent()
        val previous = model.state.value.snapshot
        val gate = CompletableDeferred<Unit>()
        snapshotReader = {
            withContext(NonCancellable) { gate.await() }
            diagnosticSnapshot().copy(heapUsed = 999)
        }
        model.export(HomeLanguage.GERMAN);runCurrent()
        settings.values.value = searchAccount("B");runCurrent()
        gate.complete(Unit);runCurrent()
        assertEquals(0,exports);assertEquals(settings.key,model.state.value.sourceKey)
        assertEquals(previous,model.state.value.snapshot)
        assertFalse(model.state.value.exporting);assertNull(model.state.value.shareFile)
    }
    @Test fun leavingDuringAnExportSnapshotCancelsTheReadWithoutSharing() = runTest(dispatcher) {
        val model = model();runCurrent()
        var cancelled = false
        snapshotReader = { try { awaitCancellation() } finally { cancelled = true } }
        model.export(HomeLanguage.GERMAN);runCurrent()
        model.setVisible(false);runCurrent()
        assertTrue(cancelled);assertEquals(0,exports);assertEquals(0,probes)
        assertFalse(model.state.value.exporting);assertNull(model.state.value.shareFile)
    }
    @Test fun aStalledExportSnapshotTimesOutAndAFreshRetryCanSucceed() = runTest(dispatcher) {
        val model = model();runCurrent()
        val previous = model.state.value.snapshot
        snapshotReader = { awaitCancellation() }
        model.export(HomeLanguage.GERMAN);runCurrent();advanceTimeBy(10_000);runCurrent()
        assertTrue(model.state.value.failure);assertFalse(model.state.value.exporting)
        assertEquals(0,exports);assertEquals(previous,model.state.value.snapshot)
        val expected = diagnosticSnapshot().copy(heapUsed = 345)
        snapshotReader = { expected }
        model.export(HomeLanguage.GERMAN);runCurrent()
        assertFalse(model.state.value.failure);assertEquals(1,exports);assertEquals(0,probes)
        assertEquals(expected,exportedSnapshot);assertNotNull(model.state.value.shareFile)
    }
    @Test fun aLateExportFromThePreviousSourceDoesNotOpenTheShareChooser() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        exported = { withContext(NonCancellable) { gate.await() };File("synthetic.txt") }
        val model = model();runCurrent();model.export(HomeLanguage.GERMAN);runCurrent()
        settings.values.value = searchAccount("B");runCurrent();gate.complete(Unit);runCurrent()
        assertNull(model.state.value.shareFile);assertFalse(model.state.value.exporting)
    }
    @Test fun clearingSessionEventsDoesNotChangeAccountOrProbeResults() = runTest(dispatcher) {
        val model = model();runCurrent();model.runChecks();runCurrent()
        DiagnosticJournal.record(DiagnosticArea.PLAYBACK,DiagnosticIssue.DECODER,settings.key,4005)
        val before = model.state.value;model.clearEvents()
        assertTrue(model.events.value.isEmpty());assertEquals(before,model.state.value);assertEquals("A",settings.values.value.xtreamUsername)
    }
    @Test fun probeExceptionsAreRenderedAsSafeFailureStateWithoutExceptionMessages() = runTest(dispatcher) {
        block = { error("https://secret.invalid/private-user/private-password") }
        val model = model();runCurrent();model.runChecks();runCurrent()
        assertTrue(model.state.value.failure);assertFalse(model.state.value.busy)
        assertFalse(model.state.value.toString().contains("private-password"))
    }
}