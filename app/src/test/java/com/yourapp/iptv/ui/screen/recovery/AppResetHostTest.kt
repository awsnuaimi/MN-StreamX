package com.yourapp.iptv.ui.screen.recovery

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelStore
import com.yourapp.iptv.domain.model.HomeLanguage
import com.yourapp.iptv.domain.recovery.*
import com.yourapp.iptv.domain.security.SecureStorageFailure
import com.yourapp.iptv.ui.screen.parental.ParentalNavigationStores
import com.yourapp.iptv.ui.screen.parental.ParentalSessionTest
import com.yourapp.iptv.ui.theme.IPTVAppTheme
import com.yourapp.iptv.viewmodel.AppResetViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34],application = Application::class,qualifiers = "w960dp-h540dp-mdpi")
class AppResetHostTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val owners = ViewModelStore()
    private val labels = AppResetLabels(HomeLanguage.GERMAN)
    private class Reset(initial: AppResetState = AppResetState(AppResetPhase.READY)) : AppDataReset {
        override val state = MutableStateFlow(initial)
        var requests = 0;var reads = 0
        override suspend fun initialize() { reads++ }
        override suspend fun request() { requests++;state.value = AppResetState(AppResetPhase.WAITING_FOR_ANDROID) }
    }
    @After fun cleanup() { owners.clear() }
    private fun model(reset: Reset): AppResetViewModel {
        lateinit var model: AppResetViewModel
        compose.runOnIdle { model = AppResetViewModel(reset);owners.put("reset",model) }
        return model
    }
    @Test fun startupCheckAndInterruptedResetNeverCreateNormalAppContent() {
        val reset = Reset(AppResetState());val model = model(reset);var created = 0
        compose.setContent { IPTVAppTheme { AppResetHost(model,HomeLanguage.GERMAN) { created++;Text("Private app") } } }
        compose.onNodeWithText("Private app").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0,created);reset.state.value = AppResetState(AppResetPhase.RECOVERY_REQUIRED,AppResetProblem.INTERRUPTED) }
        compose.onNodeWithTag("reset-startup-gate").assertIsDisplayed()
        compose.onNodeWithText("Private app").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0,created);assertEquals(0,reset.requests) }
    }
    @Test fun beginningResetDisposesContentAndClearsItsNavigationModels() {
        val reset = Reset();val model = model(reset)
        var quarantines = 0
        val stores = ParentalNavigationStores();lateinit var probe: ParentalSessionTest.Probe
        compose.setContent { IPTVAppTheme { AppResetHost(model,HomeLanguage.GERMAN,{ quarantines++;stores.clear() }) {
            val local = remember { ParentalSessionTest.Probe().also { stores.owner("session").viewModelStore.put("private",it) } }
            SideEffect { probe = local };Text("Private playback")
        } } }
        compose.onNodeWithText("Private playback").assertIsDisplayed()
        compose.runOnIdle { reset.state.value = AppResetState(AppResetPhase.COMMITTING) }
        compose.onNodeWithText("Private playback").assertDoesNotExist()
        compose.runOnIdle { assertTrue("Quarantine callback did not run",quarantines > 0);assertTrue("ViewModel was not cleared",probe.cleared);assertTrue("ViewModel coroutine was not cancelled",probe.job.isCancelled) }
    }
    @Test fun twoSeparateConfirmationsAndAcknowledgementAreRequiredAndCancelIsFocused() {
        val reset = Reset();val model = model(reset)
        compose.setContent { IPTVAppTheme { AppResetHost(model,HomeLanguage.GERMAN) {
            AppResetAction(SecureStorageFailure.KEY_MISSING,HomeLanguage.GERMAN)
        } } }
        compose.onNodeWithTag("reset-open").performClick()
        // Real Dialog windows receive this native event; Robolectric requires it explicitly.
        compose.runOnIdle { ShadowDialog.getLatestDialog().window!!.decorView.dispatchWindowFocusChanged(true) }
        compose.waitForIdle()
        compose.onNodeWithTag("reset-review").assertIsNotEnabled()
        compose.onNodeWithTag("reset-cancel").assertIsFocused()
        compose.runOnIdle { assertEquals(0,reset.requests) }
        compose.onNodeWithTag("reset-acknowledge").performScrollTo().performClick()
        compose.onNodeWithTag("reset-review").performClick()
        compose.onNodeWithText(labels.finalTitle()).assertIsDisplayed()
        compose.runOnIdle { ShadowDialog.getLatestDialog().window!!.decorView.dispatchWindowFocusChanged(true) }
        compose.waitForIdle()
        compose.onNodeWithTag("reset-cancel").assertIsFocused()
        compose.runOnIdle { assertEquals(0,reset.requests) }
        compose.onNodeWithTag("reset-erase").performClick()
        compose.onNodeWithTag("reset-startup-gate").assertIsDisplayed()
        compose.onNodeWithTag("reset-open").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1,reset.requests) }
    }
    @Test fun cancellingAnInterruptedResetCannotReturnToPrivateContent() {
        val reset = Reset(AppResetState(AppResetPhase.RECOVERY_REQUIRED,AppResetProblem.INTERRUPTED));val model = model(reset)
        compose.setContent { IPTVAppTheme { AppResetHost(model,HomeLanguage.GERMAN) { Text("Private app") } } }
        compose.onNodeWithTag("reset-resume").performClick()
        compose.onNodeWithTag("reset-cancel").performClick()
        compose.onNodeWithTag("reset-startup-gate").assertIsDisplayed()
        compose.onNodeWithText("Private app").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0,reset.requests) }
    }
    @Test fun aTemporaryStorageFailureDoesNotDisplayAnEraseAction() {
        val reset = Reset();val model = model(reset)
        compose.setContent { IPTVAppTheme { AppResetHost(model,HomeLanguage.GERMAN) {
            AppResetAction(SecureStorageFailure.TEMPORARILY_UNAVAILABLE,HomeLanguage.GERMAN)
        } } }
        compose.onNodeWithTag("reset-open").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0,reset.requests) }
    }
    @Test fun unreadableResetStatusOffersRecheckingButNeverErasure() {
        val reset = Reset(AppResetState(AppResetPhase.RECOVERY_REQUIRED,AppResetProblem.JOURNAL_READ));val model = model(reset)
        compose.setContent { IPTVAppTheme { AppResetHost(model,HomeLanguage.GERMAN) { Text("Private app") } } }
        compose.onNodeWithTag("reset-resume").assertDoesNotExist()
        compose.onNodeWithText(labels.retry()).performClick()
        compose.runOnIdle { assertEquals(2,reset.reads);assertEquals(0,reset.requests) }
    }
    @Test fun allLanguagesWarnAboutTheDefaultProfileAndDoNotPromiseKeyRecovery() {
        for (language in HomeLanguage.entries) {
            val text = AppResetLabels(language)
            assertTrue(text.scope().isNotBlank());assertTrue(text.noRecovery().isNotBlank())
            assertNotEquals(text.review(),text.erase());assertNotEquals(text.cancel(),text.erase())
        }
        assertTrue(labels.scope().contains("Standardprofils"))
        assertTrue(labels.outside().contains("exportierte Sicherungen"))
    }
}