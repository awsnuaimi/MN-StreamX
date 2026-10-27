package com.yourapp.iptv.ui.screen.profile

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelStore
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.domain.repository.HomeAppearanceRepository
import com.yourapp.iptv.ui.component.common.AppLanguageHost
import com.yourapp.iptv.ui.theme.IPTVAppTheme
import com.yourapp.iptv.viewmodel.HomeAppearanceViewModel
import kotlinx.coroutines.flow.emptyFlow
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34],application = Application::class)
class ProfileRecoveryHostTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val owners = ViewModelStore()
    @After fun cleanup() { owners.clear() }
    private fun show(fallback: HomeLanguage?) {
        lateinit var appearance: HomeAppearanceViewModel
        compose.runOnIdle {
            appearance = HomeAppearanceViewModel(object : HomeAppearanceRepository {
                override fun observe() = emptyFlow<HomeAppearance>()
                override suspend fun save(value: HomeAppearance) = Unit
            });owners.put("appearance",appearance)
        }
        compose.setContent { IPTVAppTheme { AppLanguageHost(appearance,fallback) { Text("Recovery content") } } }
    }
    @Test fun unreadableProfilesCanShowRecoveryEvenWhenAppearanceHasNotEmitted() {
        show(HomeLanguage.ARABIC);compose.onNodeWithText("Recovery content").assertIsDisplayed()
    }
    @Test fun normalStartupStillWaitsForTheSavedLanguage() {
        show(null);compose.onNodeWithText("Recovery content").assertDoesNotExist()
    }
}