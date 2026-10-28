package com.yourapp.iptv.ui.screen.settings

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.ui.component.common.LocalizedResources
import com.yourapp.iptv.ui.theme.IPTVAppTheme
import com.yourapp.iptv.viewmodel.HomeAppearanceEditorState
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w960dp-h540dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalComposeUiApi::class, ExperimentalTestApi::class)
class SettingsWorkspaceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var language by mutableStateOf(HomeLanguage.ARABIC)
    private var scale by mutableFloatStateOf(1f)
    private var editor by mutableStateOf(HomeAppearanceEditorState(HomeAppearance(), loading = false, committed = HomeAppearance()))
    private var writes = 0
    private val actions = mutableListOf<SettingsAction>()
    private fun board() {
        compose.setContent {
            val density = LocalDensity.current;val input = LocalInputModeManager.current
            SideEffect { input.requestInputMode(InputMode.Keyboard) }
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                LocalizedResources(language) { IPTVAppTheme {
                    Surface(Modifier.fillMaxSize()) {
                        val labels = SettingsLabels(language)
                        SettingsWorkspace(labels, "Synthetic profile", editor.dirty, editor.saving, editor.saved, editor.error != null, true,
                            onSave = { writes++;editor = editor.copy(saving = true) },
                            onReset = { editor = editor.copy(value = HomeAppearance(), saved = false) },
                            onDiscard = { editor = editor.copy(value = editor.committed!!, saved = false, error = null) },
                            onAction = { actions += it }) { category, first, action ->
                            if (category == SettingsCategory.APPEARANCE) SettingsAppearancePanel(editor, labels, first,
                                onChange = { editor = editor.copy(value = it, saved = false, error = null) }, onRetry = {}, onPlayerText = { action(SettingsAction.PLAYER) })
                            else SettingsActionRow(labels.title(category), category = category,
                                modifier = Modifier.focusRequester(first).testTag("content-${category.name}")) { action(when (category) {
                                    SettingsCategory.ACCOUNTS -> SettingsAction.SUBSCRIPTIONS
                                    SettingsCategory.PLAYBACK -> SettingsAction.PLAYER
                                    SettingsCategory.PARENTAL -> SettingsAction.PARENTAL
                                    SettingsCategory.DATA -> SettingsAction.BACKUP
                                    SettingsCategory.HELP -> SettingsAction.DIAGNOSTICS
                                    else -> SettingsAction.BACK
                                }) }
                        }
                    }
                } }
            }
        }
    }
    private fun category(value: SettingsCategory) = compose.onNodeWithTag("settings-category-${value.name}").performScrollTo().performClick()
    private fun red() = compose.onNodeWithTag("settings-theme-RED").performScrollTo().performClick()
    private fun finishSave() { editor = editor.copy(saving = false, saved = true, committed = editor.value) }
    private fun back() = compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }

    @Test fun arabicMatchesTheTwoPaneDesignAndEnglishMirrorsIt() {
        board()
        val rail = compose.onNodeWithTag("settings-rail").fetchSemanticsNode().boundsInRoot
        val detail = compose.onNodeWithTag("settings-detail").fetchSemanticsNode().boundsInRoot
        assertTrue(detail.right < rail.left)
        compose.onNodeWithTag("settings-save").assertIsDisplayed()
        export("settings-ar.png")
        compose.runOnIdle { language = HomeLanguage.ENGLISH }
        val left = compose.onNodeWithTag("settings-rail").fetchSemanticsNode().boundsInRoot
        val right = compose.onNodeWithTag("settings-detail").fetchSemanticsNode().boundsInRoot
        assertTrue(left.right < right.left)
        export("settings-en.png")
    }
    @Test fun focusDoesNotOpenASectionUntilOkAndRemoteMovesIntoItsControls() {
        board()
        compose.onNodeWithTag("settings-category-ACCOUNTS").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithTag("settings-detail-title", true).assertTextEquals("المظهر واللغة")
        compose.onNodeWithTag("settings-category-ACCOUNTS").performKeyInput { pressKey(Key.Enter) }
        compose.onNodeWithTag("content-ACCOUNTS").assertExists()
        compose.onNodeWithTag("settings-category-ACCOUNTS").performKeyInput { pressKey(Key.DirectionLeft) }
        compose.onNodeWithTag("content-ACCOUNTS").assertIsFocused()
        back();compose.onNodeWithTag("settings-category-ACCOUNTS").assertIsFocused()
        back();compose.runOnIdle { assertEquals(listOf(SettingsAction.BACK), actions) }
    }
    @Test fun searchFindsOptionsAndUnknownQueriesCanBeClearedWithBack() {
        board();compose.onNodeWithTag("settings-search-toggle").performClick()
        compose.onNodeWithTag("settings-search").performTextInput("شبكة")
        compose.onNodeWithTag("settings-category-APPEARANCE").assertExists()
        compose.onNodeWithTag("settings-category-ACCOUNTS").assertDoesNotExist()
        compose.onNodeWithTag("settings-category-APPEARANCE").performClick()
        compose.onNodeWithTag("settings-search").assertDoesNotExist()
        compose.onNodeWithTag("settings-search-toggle").performClick()
        compose.onNodeWithTag("settings-search").performTextInput("zzzz-unknown")
        compose.onNodeWithText("لا توجد نتائج").assertExists()
        back();compose.onNodeWithTag("settings-search").assertDoesNotExist()
        compose.onNodeWithTag("settings-search-toggle").assertIsFocused()
        compose.runOnIdle { assertTrue(actions.isEmpty()) }
    }
    @Test fun aThemePreviewDoesNotSaveAndLeavingCanStayOrDiscard() {
        board();red()
        compose.runOnIdle { assertEquals(0, writes);assertEquals(HomeTheme.RED, editor.value.theme);assertTrue(editor.dirty) }
        category(SettingsCategory.HELP);compose.onNodeWithTag("settings-exit-stay").performClick()
        compose.onNodeWithTag("settings-detail-title", true).assertTextEquals("المظهر واللغة")
        category(SettingsCategory.HELP);compose.onNodeWithTag("settings-exit-discard").performClick()
        compose.onNodeWithTag("content-HELP").assertExists()
        compose.runOnIdle { assertEquals(HomeTheme.DARK_BLUE, editor.value.theme);assertEquals(0, writes);assertFalse(editor.dirty) }
    }
    @Test fun saveBeforeLeavingWaitsForCommitAndFailureKeepsTheDraftAndDestination() {
        board();red();category(SettingsCategory.DATA)
        compose.onNodeWithTag("settings-exit-save").performClick()
        compose.runOnIdle { assertEquals(1, writes);assertTrue(editor.saving) }
        compose.onNodeWithTag("content-DATA").assertDoesNotExist()
        compose.runOnIdle { editor = editor.copy(saving = false, error = "synthetic failure") }
        compose.onNodeWithTag("settings-exit-save").assertExists()
        compose.runOnIdle { assertTrue(editor.dirty);assertEquals(HomeTheme.RED, editor.value.theme) }
        compose.onNodeWithTag("settings-exit-save").performClick()
        compose.runOnIdle { editor = editor.copy(error = null);finishSave() }
        compose.onNodeWithTag("content-DATA").assertExists()
        compose.runOnIdle { assertEquals(2, writes);assertFalse(editor.dirty) }
    }
    @Test fun normalSaveRetainsThePageAndRestoresSaveButtonFocus() {
        board();red()
        compose.onNodeWithTag("settings-save").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithTag("settings-save").performClick()
        compose.runOnIdle { finishSave() }
        compose.onNodeWithTag("settings-save").assertIsFocused()
        compose.onNodeWithTag("settings-saved").assertExists()
        export("settings-save-focused.png")
        compose.runOnIdle { assertTrue(actions.isEmpty());assertEquals(1, writes) }
    }
    @Test fun aLargeFontUsesAFullWidthDetailAndBackReturnsToCategories() {
        scale = 2f
        board()
        compose.onNodeWithTag("settings-detail").assertIsDisplayed()
        compose.onNodeWithTag("settings-rail").assertDoesNotExist()
        back();compose.onNodeWithTag("settings-rail").assertIsDisplayed()
        category(SettingsCategory.HELP)
        compose.onNodeWithTag("content-HELP").assertIsDisplayed()
        compose.onNodeWithTag("settings-detail").assertWidthIsAtLeast(800.dp)
        export("settings-ar-large.png")
    }
    @Test fun allSectionsRemainReachableAndTheirActionsUseTheExistingDestinations() {
        board()
        for (section in SettingsCategory.entries.filter { it != SettingsCategory.APPEARANCE }) {
            category(section)
            compose.onNodeWithTag("content-${section.name}").assertIsDisplayed().performClick()
        }
        compose.runOnIdle {
            assertTrue(actions.containsAll(listOf(SettingsAction.SUBSCRIPTIONS, SettingsAction.PLAYER,
                SettingsAction.PARENTAL, SettingsAction.BACKUP, SettingsAction.DIAGNOSTICS)))
        }
    }
    @Test fun displayOptionsEditTheDraftAndRestoreTheirRowFocusWithoutSaving() {
        board();compose.onNodeWithTag("settings-display").performScrollTo().performClick()
        compose.onNodeWithTag("settings-columns").performClick()
        compose.onNodeWithTag("settings-columns-choice-3").performClick()
        compose.onNodeWithTag("settings-channel-layout").performClick()
        compose.onNodeWithTag("settings-channel-layout-choice-1").performClick()
        compose.onNodeWithTag("settings-display-done").performClick()
        compose.onNodeWithTag("settings-display").assertIsFocused()
        compose.runOnIdle { assertEquals(5, editor.value.columns);assertEquals(HomeChannelLayout.LIST, editor.value.channelLayout)
            assertTrue(editor.dirty);assertEquals(0, writes) }
    }
    @Test fun textSizeUsesTheExistingPlayerEditorAndAdvancedOptionsRemainAvailable() {
        board();compose.onNodeWithTag("settings-player-text").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf(SettingsAction.PLAYER), actions);assertEquals(0, writes) }
        compose.onNodeWithTag("settings-advanced").performScrollTo().performClick()
        compose.onNodeWithTag("settings-logos").performScrollTo().performClick()
        compose.runOnIdle { assertFalse(editor.value.showChannelLogos);assertTrue(editor.dirty);assertEquals(0, writes) }
    }
    private fun export(name: String) {
        val dir = System.getProperty("mnstreamx.settingsPreviewDir")?.let(::File) ?: return
        dir.mkdirs()
        fun owner(view: View): View? {
            if (view.javaClass.name == "androidx.compose.ui.platform.AndroidComposeView") return view
            if (view is ViewGroup) for (i in 0 until view.childCount) owner(view.getChildAt(i))?.let { return it }
            return null
        }
        compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(960, 540, Bitmap.Config.ARGB_8888)
            try { checkNotNull(owner(compose.activity.window.decorView)).draw(Canvas(bitmap))
                dir.resolve(name).outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            } finally { bitmap.recycle() }
        }
    }
}