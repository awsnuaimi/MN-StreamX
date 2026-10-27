package com.yourapp.iptv.domain.recovery

import com.yourapp.iptv.domain.security.SecureStorageFailure
import kotlinx.coroutines.flow.StateFlow

enum class AppResetPhase { CHECKING, READY, COMMITTING, WAITING_FOR_ANDROID, RECOVERY_REQUIRED }
enum class AppResetProblem { INTERRUPTED, JOURNAL_READ, JOURNAL_WRITE, SYSTEM_REJECTED, SYSTEM_UNAVAILABLE, NOT_FINISHED }
data class AppResetState(val phase: AppResetPhase = AppResetPhase.CHECKING, val problem: AppResetProblem? = null) {
    val blocked get() = phase != AppResetPhase.READY
    val mayRequest get() = phase == AppResetPhase.READY ||
        (phase == AppResetPhase.RECOVERY_REQUIRED && problem != AppResetProblem.JOURNAL_READ)
}

interface AppDataReset {
    val state: StateFlow<AppResetState>
    suspend fun initialize()
    /** Call only after explicit confirmation that ALL local application data will be erased. */
    suspend fun request()
}

object AppResetPolicy {
    fun mayOffer(problem: SecureStorageFailure?) = problem != null && problem != SecureStorageFailure.TEMPORARILY_UNAVAILABLE
}