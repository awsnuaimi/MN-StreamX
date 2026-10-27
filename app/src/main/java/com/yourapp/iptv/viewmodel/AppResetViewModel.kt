package com.yourapp.iptv.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourapp.iptv.domain.recovery.*
import com.yourapp.iptv.domain.security.SecureStorageFailure
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job

internal data class ResetConfirmation(val finalStep: Boolean = false, val acknowledged: Boolean = false)

@HiltViewModel
class AppResetViewModel @Inject constructor(private val reset: AppDataReset) : ViewModel() {
    val state = reset.state
    private val question = MutableStateFlow<ResetConfirmation?>(null)
    private var checkJob: Job? = null
    internal val confirmation = question.asStateFlow()
    init { retryCheck() }
    fun retryCheck() {
        if (checkJob?.isActive == true) return
        checkJob = viewModelScope.launch { reset.initialize() }
    }
    fun offer(problem: SecureStorageFailure?) {
        if (state.value.phase == AppResetPhase.READY && AppResetPolicy.mayOffer(problem))
            question.value = ResetConfirmation()
    }
    fun resume() { if (state.value.phase == AppResetPhase.RECOVERY_REQUIRED && state.value.mayRequest)
        question.value = ResetConfirmation() }
    fun cancel() { question.value = null }
    fun acknowledge(value: Boolean) {
        val current = question.value ?: return
        if (!current.finalStep) question.value = current.copy(acknowledged = value)
    }
    fun review() {
        val current = question.value ?: return
        if (current.acknowledged && !current.finalStep && state.value.mayRequest)
            question.value = current.copy(finalStep = true)
    }
    fun confirm() {
        val current = question.value ?: return
        if (!current.finalStep || !current.acknowledged || !state.value.mayRequest) return
        question.value = null
        viewModelScope.launch { reset.request() }
    }
}