package mnstudio.apps.mnstreamx.ui.screen.profile

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelStore
import mnstudio.apps.mnstreamx.domain.model.*
import mnstudio.apps.mnstreamx.domain.repository.HomeAppearanceRepository
import mnstudio.apps.mnstreamx.ui.component.common.AppLanguageHost
import mnstudio.apps.mnstreamx.ui.theme.IPTVAppTheme
import mnstudio.apps.mnstreamx.viewmodel.HomeAppearanceViewModel
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