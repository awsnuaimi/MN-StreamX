package com.yourapp.iptv.ui.screen.profile

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.*
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.domain.parental.*
import com.yourapp.iptv.testing.SearchAccounts
import com.yourapp.iptv.ui.screen.parental.*
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
class ProfileSessionHostTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val owners = ViewModelStore()
    @After fun cleanup() { owners.clear() }
    @Test fun anOldExternalLeaseCannotComposePrivateContentUnderANewProfileOwner() {
        val access = object : ParentalControl by ParentalControl.Disabled {
            override val state = MutableStateFlow(ParentalState(ready = true,configured = true,unlocked = true))
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
                    DisposableEffect(probe) { probes.add(probe);onDispose { } }
                    Text("Private data")
                }
            } }
        } } }
        compose.runOnIdle {
            assertEquals(1,probes.size);model.beginExternal()
            // The namespace boundary must work even before the source observer cancels the old lease.
            access.state.value = access.state.value.copy(unlocked = false,epoch = 1,profileId = "11111111-1111-4111-8111-111111111111")
        }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(1,probes.size);assertTrue(probes.single().cleared) }
    }
}