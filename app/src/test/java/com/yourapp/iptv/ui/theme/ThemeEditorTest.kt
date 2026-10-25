package com.yourapp.iptv.ui.theme

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.yourapp.iptv.domain.model.HomeAppearance
import com.yourapp.iptv.domain.model.HomeLanguage
import com.yourapp.iptv.domain.repository.HomeAppearanceRepository
import com.yourapp.iptv.ui.component.common.LocalizedResources
import com.yourapp.iptv.ui.screen.appearance.HomeAppearanceScreen
import com.yourapp.iptv.viewmodel.HomeAppearanceSettingsViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w1280dp-h720dp-mdpi")
class ThemeEditorTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var background = Color.Unspecified
    private var returned = 0

    private class Repository(initial: HomeAppearance) : HomeAppearanceRepository {
        val values = MutableStateFlow(initial)
        var writes = 0
        override fun observe() = values
        override suspend fun save(value: HomeAppearance) { writes++; values.value = value }
    }

    private fun editor(repository: Repository) {
        val model = HomeAppearanceSettingsViewModel(repository)
        compose.setContent {
            val saved by repository.values.collectAsState()
            IPTVAppTheme(saved.isDarkTheme, saved.isNightMode) {
                val active = MaterialTheme.colorScheme.background
                SideEffect { background = active }
                LocalizedResources(HomeLanguage.GERMAN) {
                    Surface(Modifier.fillMaxSize()) { HomeAppearanceScreen({ returned++ }, model) }
                }
            }
        }
    }

    private fun choose(text: String) {
        compose.onNode(hasText(text) and hasClickAction()).performScrollTo().performClick()
    }

    @Test fun aLightPreviewDoesNotChangeTheApplicationOrWritePreferencesUntilSave() {
        val repository = Repository(HomeAppearance(language = HomeLanguage.GERMAN, isDarkTheme = true))
        editor(repository)
        choose("Hell")
        compose.onNodeWithTag("theme-preview", true).performScrollTo().assertExists()
        compose.runOnIdle {
            assertEquals(0, repository.writes)
            assertTrue(repository.values.value.isDarkTheme)
            assertEquals(AppThemeColors.forMode(true).background, background)
        }
        compose.onNodeWithTag("theme-save").performClick()
        compose.runOnIdle {
            assertEquals(1, repository.writes)
            assertFalse(repository.values.value.isDarkTheme)
            assertEquals(AppThemeColors.forMode(false).background, background)
            assertEquals(1, returned)
        }
    }

    @Test fun turningNightOffRestoresTheChosenBaseThemeAndKeepsTheSavedChoice() {
        val repository = Repository(HomeAppearance(language = HomeLanguage.GERMAN,
            isDarkTheme = false, isNightMode = true))
        editor(repository)
        choose("Dunkelblau")
        compose.runOnIdle {
            assertEquals(Color.Black, background)
            assertFalse(repository.values.value.isDarkTheme)
            assertTrue(repository.values.value.isNightMode)
        }
        choose("Schwarzer Nachtmodus")
        compose.onNodeWithTag("theme-preview", true).performScrollTo().assertExists()
        compose.runOnIdle { assertEquals(Color.Black, background); assertEquals(0, repository.writes) }
        compose.onNodeWithTag("theme-save").performClick()
        compose.runOnIdle {
            assertTrue(repository.values.value.isDarkTheme)
            assertFalse(repository.values.value.isNightMode)
            assertEquals(AppThemeColors.forMode(true).background, background)
            assertEquals(1, returned)
        }
    }
}