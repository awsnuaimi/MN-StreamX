package com.yourapp.iptv.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourapp.iptv.data.diagnostics.DiagnosticFailures
import com.yourapp.iptv.domain.diagnostics.*
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.domain.repository.*
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class DiagnosticsState(val snapshot: DiagnosticSnapshot? = null, val source: DiagnosticSource = DiagnosticSource.NONE,
    val sourceKey: String? = null, val sourceReady: Boolean = false, val checks: List<DiagnosticCheck> = emptyList(), val busy: Boolean = false,
    val exporting: Boolean = false, val failure: Boolean = false, val shareFailure: Boolean = false,
    val shareFile: File? = null, val shareRequest: Long = 0)

@HiltViewModel
class DiagnosticsViewModel @Inject constructor(private val settings: SettingsRepository,
    private val diagnostics: DiagnosticsRepository) : ViewModel() {
    private val mutable = MutableStateFlow(DiagnosticsState())
    val state = mutable.asStateFlow()
    val events = DiagnosticJournal.events
    private var account: AppSettings? = null
    @Volatile private var generation = 0L
    @Volatile private var visible = true
    private var operation: Job? = null
    private var snapshotJob: Job? = null
    private var snapshotGeneration = 0L
    init {
        val initialSnapshot = ++snapshotGeneration
        snapshotJob = viewModelScope.launch {
            try { val value = diagnostics.snapshot();if (snapshotGeneration == initialSnapshot) mutable.update { it.copy(snapshot = value) } }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { if (snapshotGeneration == initialSnapshot) mutable.update { it.copy(failure = true) } }
        }
        viewModelScope.launch {
            try {
                settings.observeSettings().distinctUntilChangedBy(PlaylistCacheKey::of).collect { source ->
                    generation++;operation?.cancel();account = source
                    mutable.update { it.copy(source = diagnostics.sourceKind(source),sourceKey = if (source.hasSelectedSource()) PlaylistCacheKey.of(source) else null,
                        sourceReady = true,checks = emptyList(),busy = false,exporting = false,failure = it.failure && it.snapshot == null,shareFile = null) }
                }
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { mutable.update { it.copy(failure = true) } }
        }
    }
    fun runChecks() {
        if (!visible || mutable.value.busy || mutable.value.exporting) return
        val source = account ?: return
        val ticket = ++generation
        snapshotGeneration++;snapshotJob?.cancel()
        mutable.update { it.copy(busy = true,checks = emptyList(),failure = false,shareFailure = false,shareFile = null) }
        operation = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                withTimeout(30_000L) {
                    val snapshot = diagnostics.snapshot()
                    mutable.update { if (generation == ticket && visible) it.copy(snapshot = snapshot) else it }
                    diagnostics.probe(source) { check ->
                        mutable.update { if (generation == ticket && visible) it.copy(checks = (it.checks + check).take(12)) else it }
                    }
                }
            } catch (_: TimeoutCancellationException) {
                mutable.update { if (generation == ticket && visible) it.copy(checks = it.checks +
                    DiagnosticCheck(DiagnosticArea.CONNECTION,DiagnosticStatus.FAIL,DiagnosticIssue.TIMEOUT)) else it }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                if (generation == ticket && visible) {
                    DiagnosticFailures.record(DiagnosticArea.SYSTEM,error)
                    mutable.update { it.copy(failure = true) }
                }
            } finally { mutable.update { if (generation == ticket) it.copy(busy = false) else it } }
        }.also { it.start() }
    }
    fun export(language: HomeLanguage) {
        val value = mutable.value
        val snapshot = value.snapshot ?: return
        if (!visible || value.busy || value.exporting) return
        val ticket = ++generation
        mutable.update { it.copy(exporting = true,shareFile = null,shareFailure = false,failure = false) }
        operation = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                val file = diagnostics.export(snapshot,value.source,value.sourceKey,value.checks,events.value,language)
                if (generation == ticket && visible) mutable.update { it.copy(shareFile = file,shareRequest = ticket) }
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { if (generation == ticket && visible) mutable.update { it.copy(failure = true) } }
            finally { mutable.update { if (generation == ticket) it.copy(exporting = false) else it } }
        }.also { it.start() }
    }
    fun cancel() {
        val running = mutable.value.busy
        generation++;operation?.cancel()
        mutable.update { it.copy(busy = false,exporting = false,shareFile = null,
            checks = if (running) (it.checks + DiagnosticCheck(DiagnosticArea.CONNECTION,DiagnosticStatus.NOTICE,DiagnosticIssue.CANCELLED)).take(12) else it.checks) }
    }
    fun setVisible(value: Boolean) { visible = value;if (!value) cancel() }
    fun consumeShare() { mutable.update { it.copy(shareFile = null) } }
    fun shareFailed() { mutable.update { it.copy(shareFailure = true,shareFile = null) } }
    fun clearEvents() { DiagnosticJournal.clear() }
}