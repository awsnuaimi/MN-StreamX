package com.yourapp.iptv.ui.state

import com.yourapp.iptv.domain.update.AppUpdate
import com.yourapp.iptv.domain.update.UpdateIssue
import java.io.File

enum class AppUpdatePhase { IDLE, CHECKING, DOWNLOADING, VERIFYING, CURRENT, READY }

data class AppUpdateUiState(
    val phase: AppUpdatePhase = AppUpdatePhase.IDLE,
    val update: AppUpdate? = null,
    val file: File? = null,
    val percent: Int = 0,
    val issue: UpdateIssue? = null,
    val requestInstall: Boolean = false,
    val installRequestId: Long = 0
) {
    val busy: Boolean get() = phase in setOf(AppUpdatePhase.CHECKING, AppUpdatePhase.DOWNLOADING, AppUpdatePhase.VERIFYING)
}