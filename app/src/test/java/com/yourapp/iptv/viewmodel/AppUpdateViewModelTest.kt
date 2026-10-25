package com.yourapp.iptv.viewmodel

import com.yourapp.iptv.domain.update.AppUpdate
import com.yourapp.iptv.domain.update.AppUpdateRepository
import com.yourapp.iptv.domain.update.UpdateFailure
import com.yourapp.iptv.domain.update.UpdateIssue
import com.yourapp.iptv.ui.state.AppUpdatePhase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class AppUpdateViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val update = AppUpdate(104201, "1.1.42.1", "arm64-v8a", "https://example.invalid/a.apk", 5, "a".repeat(64))
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { Dispatchers.resetMain() }

    private open inner class Repository : AppUpdateRepository {
        var checks = 0
        var downloads = 0
        var verifications = 0
        override suspend fun check(): AppUpdate? { checks++; return update }
        override suspend fun download(update: AppUpdate, progress: (Int) -> Unit): File {
            downloads++; progress(50); progress(100); return File("synthetic-cache/update.apk")
        }
        override suspend fun verify(update: AppUpdate, file: File) { verifications++ }
    }

    @Test fun oneClickChecksDownloadsAndVerifiesBeforeRequestingInstallation() = runTest(dispatcher) {
        val verified = CompletableDeferred<Unit>()
        val repository = object : Repository() {
            override suspend fun verify(update: AppUpdate, file: File) { super.verify(update, file); verified.await() }
        }
        val model = AppUpdateViewModel(repository)
        model.updateApp(); runCurrent()
        assertEquals(AppUpdatePhase.VERIFYING, model.state.value.phase)
        assertFalse(model.state.value.requestInstall)
        verified.complete(Unit); runCurrent()
        assertEquals(AppUpdatePhase.READY, model.state.value.phase)
        assertTrue(model.state.value.requestInstall)
        assertEquals(1, repository.checks)
        assertEquals(1, repository.downloads)
    }

    @Test fun repeatedClicksDoNotStartDuplicateDownloadsAndCancellationIgnoresLateProgress() = runTest(dispatcher) {
        lateinit var lateProgress: (Int) -> Unit
        val repository = object : Repository() {
            override suspend fun download(update: AppUpdate, progress: (Int) -> Unit): File {
                downloads++; lateProgress = progress; awaitCancellation()
            }
        }
        val model = AppUpdateViewModel(repository)
        model.updateApp(); runCurrent(); model.updateApp(); runCurrent()
        assertEquals(1, repository.downloads)
        model.cancel(); runCurrent(); lateProgress(99)
        assertEquals(AppUpdatePhase.IDLE, model.state.value.phase)
        assertEquals(0, model.state.value.percent)
        assertFalse(model.state.value.requestInstall)
    }

    @Test fun anUpToDateVersionDoesNotDownloadAnything() = runTest(dispatcher) {
        val repository = object : Repository() { override suspend fun check(): AppUpdate? = null }
        val model = AppUpdateViewModel(repository)
        model.updateApp(); runCurrent()
        assertEquals(AppUpdatePhase.CURRENT, model.state.value.phase)
        assertEquals(0, repository.downloads)
    }

    @Test fun rejectedSignaturesCannotReachTheInstaller() = runTest(dispatcher) {
        val repository = object : Repository() {
            override suspend fun verify(update: AppUpdate, file: File) { throw UpdateFailure(UpdateIssue.SIGNATURE_MISMATCH) }
        }
        val model = AppUpdateViewModel(repository)
        model.updateApp(); runCurrent()
        assertEquals(UpdateIssue.SIGNATURE_MISMATCH, model.state.value.issue)
        assertFalse(model.state.value.requestInstall)
        assertNull(model.state.value.file)
    }

    @Test fun returningFromPermissionSettingsReverifiesAndRequestsInstallationOnlyOnce() = runTest(dispatcher) {
        val repository = Repository()
        val model = AppUpdateViewModel(repository)
        model.updateApp(); runCurrent()
        val firstRequest = model.state.value.installRequestId
        model.consumeInstallRequest()
        model.installBlocked(UpdateIssue.INSTALL_PERMISSION)
        model.installAgain(); runCurrent()
        assertEquals(2, repository.verifications)
        assertTrue(model.state.value.installRequestId > firstRequest)
        assertTrue(model.state.value.requestInstall)
        assertNull(model.state.value.issue)
        model.consumeInstallRequest(); model.cancel()
        assertFalse(model.state.value.requestInstall)
        assertEquals(AppUpdatePhase.READY, model.state.value.phase)
    }
}