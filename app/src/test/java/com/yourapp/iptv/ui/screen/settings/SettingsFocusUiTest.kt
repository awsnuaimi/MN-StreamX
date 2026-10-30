package com.yourapp.iptv.ui.screen.settings

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.ui.component.common.LocalizedResources
import com.yourapp.iptv.ui.component.common.FocusInputEpoch
import com.yourapp.iptv.ui.theme.IPTVAppTheme
import com.yourapp.iptv.viewmodel.HomeAppearanceEditorState
import com.yourapp.iptv.testing.waitForFocus
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w960dp-h540dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalComposeUiApi::class, ExperimentalTestApi::class)
class SettingsFocusUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var manager: FocusManager
    private var profile by mutableStateOf("profile-a")
    private var state by mutableStateOf(HomeAppearanceEditorState(HomeAppearance(), loading = false, committed = HomeAppearance()))
    @Composable private fun Content() {
        manager = LocalFocusManager.current
        val mode = LocalInputModeManager.current
        SideEffect { mode.requestInputMode(InputMode.Keyboard) }
        IPTVAppTheme { LocalizedResources(HomeLanguage.GERMAN) {
            Surface(Modifier.fillMaxSize()) {
                val labels = SettingsLabels(HomeLanguage.GERMAN)
                SettingsWorkspace(labels, "Profile", state.dirty, state.saving, state.saved, state.error != null,
                    true, { state = state.copy(saving = true) }, {}, {}, {}, focusScope = profile) { category, first, action ->
                    if (category == SettingsCategory.APPEARANCE) SettingsAppearancePanel(state, labels, first,
                        { state = state.copy(value = it) }, {})
                    else SettingsActionRow(labels.title(category), category = category, glyph = "other-${category.name}",
                        modifier = Modifier.focusRequester(first).testTag("other-content")) { action(SettingsAction.DIAGNOSTICS) }
                }
            }
        } }
    }
    private fun focus(tag: String) {
        compose.onNodeWithTag(tag).performScrollTo().performSemanticsAction(SemanticsActions.RequestFocus)
    }
    @Test fun savedStateRestoresTheSpecificAppearanceOption() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { Content() }
        focus("settings-night"); restoration.emulateSavedInstanceStateRestore()
        compose.waitForFocus("settings-night")
    }
    @Test fun returningToACategoryRecallsItsPreviousOption() {
        compose.setContent { Content() }
        focus("settings-night")
        compose.onNodeWithTag("settings-category-HELP").performClick()
        compose.onNodeWithTag("settings-category-APPEARANCE").performClick()
        focus("settings-category-APPEARANCE")
        compose.onNodeWithTag("settings-category-APPEARANCE").performKeyInput { pressKey(Key.DirectionRight) }
        compose.waitForFocus("settings-night")
    }
    @Test fun inputDuringSaveCancelsTheDelayedReturnToSave() {
        compose.setContent { Content() }
        compose.onNodeWithTag("settings-save").performClick()
        compose.onNodeWithTag("settings-workspace").performKeyInput { pressKey(Key.DirectionDown) }
        // Compose sends synthetic keys directly to the view; model the Activity boundary as well.
        compose.runOnIdle { FocusInputEpoch.advance(); manager.clearFocus(force = true) }
        compose.runOnIdle { state = state.copy(saving = false, saved = true, committed = state.value) }
        compose.onNodeWithTag("settings-save").assertIsNotFocused()
    }
    @Test fun profileChangeDoesNotRecallAnotherProfilesOption() {
        compose.setContent { Content() }
        focus("settings-night")
        compose.runOnIdle { profile = "profile-b" }
        compose.onNodeWithTag("settings-night").assertIsNotFocused()
    }
    @Test fun unsavedChangesPromptStartsOnStayAndReturnsToItsInvoker() {
        compose.setContent { Content() }
        compose.onNodeWithTag("settings-theme-RED").performScrollTo().performClick()
        focus("settings-category-HELP")
        compose.onNodeWithTag("settings-category-HELP").performKeyInput { pressKey(Key.Enter) }
        compose.waitForFocus("settings-exit-stay")
        compose.onNodeWithTag("settings-exit-stay").performKeyInput { pressKey(Key.Enter) }
        compose.waitForFocus("settings-category-HELP")
        compose.onNodeWithTag("settings-night").assertExists()
    }

}