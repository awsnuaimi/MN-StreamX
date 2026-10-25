package com.yourapp.iptv.ui.screen.settings

import android.app.Application
import android.app.Activity
import android.content.Intent
import android.content.ComponentName
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.yourapp.iptv.domain.update.UpdateIssue
import com.yourapp.iptv.domain.update.AppUpdate
import com.yourapp.iptv.domain.update.AppUpdateRepository
import com.yourapp.iptv.ui.state.AppUpdatePhase
import com.yourapp.iptv.ui.state.AppUpdateUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.android.controller.ContentProviderController
import com.yourapp.iptv.update.UpdateFileProvider
import org.robolectric.annotation.Config
import org.robolectric.Shadows.shadowOf
import com.yourapp.iptv.viewmodel.AppUpdateViewModel
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AppUpdateCardTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var provider: ContentProviderController<UpdateFileProvider>
    @Before fun attachProviderForThisTestContext() {
        val activity = compose.activity
        val info = activity.packageManager.getProviderInfo(
            ComponentName(activity, UpdateFileProvider::class.java), PackageManager.GET_META_DATA)
        provider = Robolectric.buildContentProvider(UpdateFileProvider::class.java).create(info)
    }
    @After fun closeProvider() { provider.shutdown() }

    @Test fun updateProgressCancellationAndPermissionRetryHaveSeparateActions() {
        val state = mutableStateOf(AppUpdateUiState())
        var updates = 0
        var cancellations = 0
        var installs = 0
        compose.setContent { MaterialTheme { AppUpdateCard(state.value, { updates++ }, { cancellations++ }, { installs++ }) } }
        compose.onNodeWithTag("update-action").performClick()
        compose.runOnIdle { state.value = AppUpdateUiState(phase = AppUpdatePhase.DOWNLOADING, percent = 40) }
        compose.onNodeWithTag("update-action").assertDoesNotExist()
        compose.onNodeWithTag("update-install").assertDoesNotExist()
        compose.onNodeWithTag("update-cancel").performClick()
        compose.runOnIdle { state.value = AppUpdateUiState(phase = AppUpdatePhase.READY, issue = UpdateIssue.INSTALL_PERMISSION) }
        compose.onNodeWithTag("update-install").performClick()
        compose.runOnIdle {
            assertEquals(1, updates); assertEquals(1, cancellations); assertEquals(1, installs)
        }
    }

    @Test fun grantingPermissionResumesVerificationAndOpensTheAndroidInstaller() {
        val activity = compose.activity
        val packages = shadowOf(activity.packageManager)
        packages.setCanRequestPackageInstalls(false)
        val directory = File(activity.cacheDir, "app-updates").apply { mkdirs() }
        val file = File(directory, "synthetic-host.apk").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        var verifications = 0
        val update = AppUpdate(104201, "1.1.42.1", "arm64-v8a", "https://example.invalid/a.apk", 3, "a".repeat(64))
        val model = AppUpdateViewModel(object : AppUpdateRepository {
            override suspend fun check() = update
            override suspend fun download(update: AppUpdate, progress: (Int) -> Unit) = file
            override suspend fun verify(update: AppUpdate, file: File) { verifications++ }
        })
        try {
            compose.setContent { MaterialTheme { AppUpdateSection(model) } }
            compose.onNodeWithTag("update-action").performClick()
            compose.waitUntil(5000) { shadowOf(activity).peekNextStartedActivityForResult() != null }
            val permission = shadowOf(activity).nextStartedActivityForResult
            assertEquals(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, permission.intent.action)
            compose.runOnIdle {
                packages.setCanRequestPackageInstalls(true)
                assertTrue(activity.activityResultRegistry.dispatchResult(permission.requestCode, Activity.RESULT_OK, Intent()))
            }
            try { compose.waitUntil(5000) { shadowOf(activity).peekNextStartedActivityForResult() != null } }
            catch (error: ComposeTimeoutException) {
                System.err.println("Update host: phase=${model.state.value.phase}, issue=${model.state.value.issue}, verifies=$verifications, request=${model.state.value.installRequestId}, allowed=${activity.packageManager.canRequestPackageInstalls()}")
                throw error
            }
            val install = shadowOf(activity).nextStartedActivityForResult.intent
            assertEquals(Intent.ACTION_VIEW, install.action)
            assertEquals("content", install.data?.scheme)
            assertTrue(install.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            assertEquals(2, verifications)
        } finally { model.cancel(); file.delete() }
    }
}