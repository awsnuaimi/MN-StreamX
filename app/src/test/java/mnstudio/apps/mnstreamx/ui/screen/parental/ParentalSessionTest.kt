package mnstudio.apps.mnstreamx.ui.screen.parental

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.*
import mnstudio.apps.mnstreamx.data.parental.*
import mnstudio.apps.mnstreamx.domain.model.*
import mnstudio.apps.mnstreamx.domain.parental.*
import mnstudio.apps.mnstreamx.testing.SearchAccounts
import mnstudio.apps.mnstreamx.ui.theme.IPTVAppTheme
import mnstudio.apps.mnstreamx.ui.component.common.AppButton
import mnstudio.apps.mnstreamx.viewmodel.ParentalViewModel
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34],application = Application::class,qualifiers = "w960dp-h540dp-mdpi")
class ParentalSessionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val owner = ViewModelStore()
    @After fun cleanup() { owner.clear() }
    @Test fun aProtectedDestinationDoesNotComposeItsContentBeforeAuthorization() {
        var entered = 0
        compose.setContent { IPTVAppTheme { CompositionLocalProvider(LocalParentalSession provides ParentalUiSession(
            ParentalState(ready = true,configured = true,rules = ParentalRules(setOf(ParentalSection.MOVIES))),HomeLanguage.GERMAN)) {
            ParentalRouteGate(ParentalSection.MOVIES,"vod/movie",{}) { entered++;Text("Protected movie") }
        } } }
        compose.runOnIdle { assertEquals(0,entered) }
        compose.onNodeWithText("Protected movie").assertDoesNotExist()
        compose.onNodeWithText("Geschützte Inhalte entsperren").assertIsDisplayed()
    }
    @Test fun sensitiveManagementStillNeedsPinWhenGeneralSettingsAreNotLocked() {
        compose.setContent { IPTVAppTheme { CompositionLocalProvider(LocalParentalSession provides ParentalUiSession(
            ParentalState(ready = true,configured = true,rules = ParentalRules(emptySet())),HomeLanguage.GERMAN)) {
            ParentalRouteGate(null,"backup",{}) { Text("Sensitive backup") }
        } } }
        compose.onNodeWithText("Sensitive backup").assertDoesNotExist()
    }
    @Test fun changingAccessEpochClearsNavigationViewModelsAndCancelsTheirJobs() {
        val access = Access(ParentalState(ready = true,configured = true,unlocked = true))
        lateinit var model: ParentalViewModel
        val probes = mutableListOf<Probe>()
        compose.runOnIdle {
            model = ParentalViewModel(access,SearchAccounts(),object : ParentalCatalog {
                override suspend fun groups(source: AppSettings) = emptyList<String>()
            });owner.put("root",model)
        }
        compose.setContent { IPTVAppTheme { ParentalSessionHost(model,HomeLanguage.GERMAN) { _, _ ->
            NavHost(rememberNavController(),startDestination = "home") { composable("home") {
                val probe: Probe = viewModel()
                DisposableEffect(probe) { probes.add(probe);onDispose { } }
                Text("Session")
            } }
        } } }
        compose.runOnIdle { assertEquals(1,probes.size);access.lock() }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(2,probes.size);assertTrue(probes.first().cleared);assertTrue(probes.first().job.isCancelled)
            assertFalse(probes.last().cleared)
        }
    }
    @Test fun externalResultKeepsItsOwnerButHidesTheOldUiUntilPinReauthentication() {
        val access = Access(ParentalState(ready = true,configured = true,unlocked = true))
        lateinit var model: ParentalViewModel
        var ticket: Long? = null
        val probes = mutableListOf<Probe>()
        compose.runOnIdle {
            model = ParentalViewModel(access,SearchAccounts(),object : ParentalCatalog {
                override suspend fun groups(source: AppSettings) = emptyList<String>()
            });owner.put("root",model)
        }
        compose.setContent { IPTVAppTheme { ParentalSessionHost(model,HomeLanguage.GERMAN) { _, _ ->
            NavHost(rememberNavController(),startDestination = "backup") { composable("backup") {
                ParentalRouteGate(null,"backup",{}) {
                    val probe: Probe = viewModel()
                    DisposableEffect(probe) { probes.add(probe);onDispose { } }
                    AppButton("Private backup data",{})
                }
            } }
        } } }
        compose.runOnIdle { ticket = model.beginExternal();model.background() }
        compose.waitForIdle();compose.onNodeWithText("Private backup data").assertDoesNotExist()
        compose.runOnIdle { assertFalse(probes.single().cleared);model.finishExternal(ticket) }
        compose.onNodeWithText("Geschützte Inhalte entsperren").assertIsDisplayed()
        compose.runOnIdle { access.state.value = access.state.value.copy(unlocked = true,epoch = access.state.value.epoch + 1) }
        compose.waitForIdle();compose.onNodeWithText("Private backup data").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1,probes.size);assertFalse(probes.single().cleared);assertNull(model.externalFlow.value) }
    }
    class Probe : ViewModel() {
        val job = viewModelScope.launch { awaitCancellation() }
        var cleared = false
        override fun onCleared() { cleared = true }
    }
}