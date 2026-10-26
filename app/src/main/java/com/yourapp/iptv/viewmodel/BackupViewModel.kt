package com.yourapp.iptv.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.domain.repository.*
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class BackupUiState(
    val working: Boolean = false, val awaitingPicker: Boolean = false,
    val exportRequest: String? = null, val exportName: String = "MN-StreamX.mnxb",
    val fileName: String? = null, val preview: BackupPreview? = null,
    val problem: BackupProblem? = null, val exported: Boolean = false, val restored: Boolean = false
) { val busy: Boolean get() = working || awaitingPicker }

@HiltViewModel
class BackupViewModel @Inject constructor(private val repository: BackupRepository,
    private val documents: BackupDocuments) : ViewModel() {
    private val mutable = MutableStateFlow(BackupUiState())
    val state = mutable.asStateFlow()
    private var exportBytes: ByteArray? = null
    private var importBytes: ByteArray? = null

    fun create(password: CharArray, confirmation: CharArray) {
        if (state.value.busy) { password.fill('\u0000'); confirmation.fill('\u0000'); return }
        if (!password.contentEquals(confirmation)) {
            password.fill('\u0000'); confirmation.fill('\u0000')
            mutable.update { it.copy(problem = BackupProblem.PASSWORD_MISMATCH) }; return
        }
        confirmation.fill('\u0000')
        run {
            try {
                exportBytes?.fill(0)
                exportBytes = repository.export(password)
                val date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
                mutable.update { it.copy(exportRequest = UUID.randomUUID().toString(), awaitingPicker = true,
                    exportName = "MN-StreamX-$date.mnxb") }
            } finally { password.fill('\u0000') }
        }
    }
    fun consumeExportRequest() { mutable.update { it.copy(exportRequest = null) } }
    fun write(uri: String?) {
        if (!state.value.awaitingPicker) return
        mutable.update { it.copy(awaitingPicker = false, exportRequest = null) }
        val bytes = exportBytes
        exportBytes = null
        if (uri == null) { bytes?.fill(0); return }
        if (bytes == null) { mutable.update { it.copy(problem = BackupProblem.STORAGE) }; return }
        run { try { documents.writeNew(uri, bytes); mutable.update { it.copy(exported = true) } } finally { bytes.fill(0) } }
    }
    fun read(uri: String?) {
        if (uri == null || state.value.busy) return
        clearPreview()
        importBytes?.fill(0); importBytes = null
        mutable.update { it.copy(fileName = null) }
        run {
            val document = documents.read(uri)
            importBytes = document.bytes
            mutable.update { it.copy(fileName = document.name) }
        }
    }
    fun inspect(password: CharArray) {
        val bytes = importBytes
        if (state.value.busy || bytes == null) { password.fill('\u0000'); return }
        run {
            try {
                val preview = repository.inspect(bytes, password)
                if (!currentCoroutineContext().isActive) { repository.discard(preview.token); return@run }
                mutable.update { it.copy(preview = preview) }
            } finally { password.fill('\u0000') }
        }
    }
    fun restore(options: BackupRestoreOptions) {
        val preview = state.value.preview ?: return
        if (state.value.busy) return
        run {
            repository.restore(preview.token, options)
            importBytes?.fill(0); importBytes = null
            mutable.update { it.copy(preview = null, fileName = null, restored = true) }
        }
    }
    fun clearPreview() {
        if (state.value.busy) return
        state.value.preview?.let { repository.discard(it.token) }
        mutable.update { it.copy(preview = null, problem = null) }
    }
    fun pickerUnavailable() {
        exportBytes?.fill(0); exportBytes = null
        mutable.update { it.copy(awaitingPicker = false, exportRequest = null, problem = BackupProblem.PICKER) }
    }
    private fun run(block: suspend () -> Unit) {
        mutable.update { it.copy(working = true, problem = null, exported = false, restored = false) }
        viewModelScope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: BackupException) { mutable.update { it.copy(problem = e.problem) } }
            catch (_: Exception) { mutable.update { it.copy(problem = BackupProblem.STORAGE) } }
            finally { mutable.update { it.copy(working = false) } }
        }
    }
    override fun onCleared() {
        state.value.preview?.let { repository.discard(it.token) }
        exportBytes?.fill(0); importBytes?.fill(0)
        super.onCleared()
    }
}