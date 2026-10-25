package com.yourapp.iptv.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourapp.iptv.domain.update.AppUpdateRepository
import com.yourapp.iptv.domain.update.UpdateFailure
import com.yourapp.iptv.domain.update.UpdateIssue
import com.yourapp.iptv.ui.state.AppUpdatePhase
import com.yourapp.iptv.ui.state.AppUpdateUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppUpdateViewModel @Inject constructor(private val repository: AppUpdateRepository) : ViewModel() {
    private val mutable = MutableStateFlow(AppUpdateUiState())
    val state = mutable.asStateFlow()
    private var operation: Job? = null
    @Volatile private var generation = 0L

    fun updateApp() {
        if (operation?.isActive == true) return
        val ticket = ++generation
        mutable.value = AppUpdateUiState(phase = AppUpdatePhase.CHECKING)
        operation = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                val update = repository.check()
                if (ticket != generation) return@launch
                if (update == null) { mutable.value = AppUpdateUiState(phase = AppUpdatePhase.CURRENT); return@launch }
                mutable.value = AppUpdateUiState(phase = AppUpdatePhase.DOWNLOADING, update = update)
                val file = repository.download(update) { percent ->
                    mutable.update { current ->
                        if (ticket == generation && current.phase == AppUpdatePhase.DOWNLOADING) {
                            current.copy(percent = percent.coerceIn(0, 100))
                        } else current
                    }
                }
                if (ticket != generation) return@launch
                mutable.update { it.copy(phase = AppUpdatePhase.VERIFYING) }
                repository.verify(update, file)
                if (ticket == generation) mutable.value = AppUpdateUiState(
                    phase = AppUpdatePhase.READY, update = update, file = file, percent = 100, requestInstall = true, installRequestId = ticket)
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                if (ticket == generation) mutable.value = AppUpdateUiState(
                    issue = (error as? UpdateFailure)?.issue ?: UpdateIssue.NETWORK)
            }
        }.also { it.start() }
    }

    fun installAgain() {
        if (operation?.isActive == true) return
        val snapshot = mutable.value
        val update = snapshot.update ?: return
        val file = snapshot.file ?: return
        val ticket = ++generation
        mutable.update { it.copy(phase = AppUpdatePhase.VERIFYING, issue = null, requestInstall = false) }
        operation = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                repository.verify(update, file)
                if (ticket == generation) mutable.update { it.copy(phase = AppUpdatePhase.READY, requestInstall = true, installRequestId = ticket) }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                if (ticket == generation) mutable.value = AppUpdateUiState(
                    issue = (error as? UpdateFailure)?.issue ?: UpdateIssue.INTEGRITY)
            }
        }.also { it.start() }
    }

    fun consumeInstallRequest() { mutable.update { it.copy(requestInstall = false) } }
    fun installBlocked(issue: UpdateIssue) { mutable.update { it.copy(issue = issue, requestInstall = false) } }

    fun cancel() {
        generation++
        operation?.cancel()
        operation = null
        mutable.update { if (it.busy) AppUpdateUiState() else it.copy(requestInstall = false) }
    }
}