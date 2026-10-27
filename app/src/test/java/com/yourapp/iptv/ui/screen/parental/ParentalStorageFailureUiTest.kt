package com.yourapp.iptv.ui.screen.parental

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.*
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.domain.parental.*
import com.yourapp.iptv.domain.security.SecureStorageFailure
import com.yourapp.iptv.testing.SearchAccounts
import com.yourapp.iptv.ui.component.common.secureStorageMessage
import com.yourapp.iptv.ui.theme.IPTVAppTheme
import com.yourapp.iptv.viewmodel.ParentalViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34],application = Application::class,qualifiers = "w960dp-h540dp-mdpi")
class ParentalStorageFailureUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val owners = ViewModelStore()
    @After fun cleanup() { owners.clear() }
    @Test fun storageFailureDisposesPrivateContentAndRetryNeverRestoresAnOldPinGrant() {
        var retry = false
        val access = object : ParentalControl by ParentalControl.Disabled {
            override val state = MutableStateFlow(ParentalState(ready = true,configured = true,unlocked = true))
            override suspend fun initialize() {
                if (retry) state.value = state.value.copy(storageError = false,storageFailure = null,
                    unlocked = false,epoch = state.value.epoch + 1)
            }
        }
        lateinit var model: ParentalViewModel
        val probes = mutableListOf<ParentalSessionTest.Probe>()
        compose.runOnIdle {
            model = ParentalViewModel(access,SearchAccounts(),object : ParentalCatalog {
                override suspend fun groups(source: AppSettings) = emptyList<String>()
            });owners.put("root",model)
        }
        compose.setContent { IPTVAppTheme { ParentalSessionHost(model,HomeLanguage.GERMAN) { _, _ ->
            NavHost(rememberNavController(),startDestination = "backup") { composable("backup") {
                ParentalRouteGate(null,"backup",{}) {
                    val probe: ParentalSessionTest.Probe = viewModel()
                    DisposableEffect(probe) { probes.add(probe);onDispose {} }
                    Text("Private storage content")
                }
            } }
        } } }
        compose.onNodeWithText("Private storage content").assertIsDisplayed()
        compose.runOnIdle {
            access.state.value = access.state.value.copy(storageError = true,unlocked = false,
                storageFailure = SecureStorageFailure.KEY_MISSING,epoch = 1)
        }
        compose.onNodeWithText("Private storage content").assertDoesNotExist()
        compose.onNodeWithText(secureStorageMessage(SecureStorageFailure.KEY_MISSING,HomeLanguage.GERMAN)).assertIsDisplayed()
        compose.runOnIdle { assertTrue(probes.single().cleared);retry = true }
        compose.onNodeWithText("Erneut versuchen").performClick()
        compose.onNodeWithText("Private storage content").assertDoesNotExist()
        compose.onNodeWithText("Geschützte Inhalte entsperren").assertIsDisplayed()
        compose.runOnIdle { assertFalse(access.state.value.unlocked);assertEquals(1,probes.size) }
    }
    @Test fun everyDiagnosisHasDistinctTextInEverySupportedLanguage() {
        for (language in HomeLanguage.entries) {
            val messages = SecureStorageFailure.entries.map { secureStorageMessage(it,language) }
            assertEquals(SecureStorageFailure.entries.size,messages.distinct().size)
            assertTrue(messages.all { it.isNotBlank() })
        }
    }
}