package com.yourapp.iptv.ui.screen.home

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.*
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.ui.presentation.*
import com.yourapp.iptv.ui.state.*
import com.yourapp.iptv.ui.theme.IPTVAppTheme
import kotlinx.coroutines.Dispatchers
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w960dp-h540dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
class LiveTvNumberInputTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val channels = (0 until 500).map { Channel("id:$it", "PL| Station $it FHD", streamUrl = "stream:$it", group = "General") }
    private var catalog by mutableStateOf(HomeUiState(channels = channels, visibleChannels = channels,
        groups = listOf("General"), browseScope = "scope", isLoading = false))
    private var prefs by mutableStateOf(LiveTvPreferencesState(ready = true))
    private var epoch by mutableLongStateOf(1)
    private var played: String? = null
    private fun board(arabic: Boolean = false, scale: Float = 1f) {
        compose.setContent { IPTVAppTheme {
            val input = LocalInputModeManager.current
            SideEffect { input.requestInputMode(InputMode.Keyboard) }
            val density = LocalDensity.current
            val appearance = HomeAppearance(language = if (arabic) HomeLanguage.ARABIC else HomeLanguage.GERMAN)
            val labels = HomeLabels(appearance.language)
            val entry = remember { FocusRequester() }
            val rail = remember { HomeSection.entries.associateWith { FocusRequester() } }
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale),
                LocalLayoutDirection provides if (arabic) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                Surface { Row(Modifier.fillMaxSize()) {
                    HomeSidebar(appearance, labels, HomeSection.LIVE, false, rail, {}, {}, liveBrowser = true, browserFocus = entry)
                    LiveTvBrowser(catalog, appearance, labels, HomeGuideState(), epoch, CaptionUiState(),
                        { catalog = catalog.copy(query = it) }, {}, { played = it.id }, {}, {}, true,
                        { _, _ -> }, {}, {}, Modifier.weight(1f), projectionDispatcher = Dispatchers.Main.immediate,
                        sidebarFocus = rail.getValue(HomeSection.LIVE), entryFocus = entry, preferences = prefs,
                        onPreference = { prefs = prefs.copy(value = it(prefs.value)) })
                } }
            }
        } }
        ready()
    }
    private fun ready() { compose.waitUntil(10_000) { compose.onAllNodesWithTag("live-channel-list").fetchSemanticsNodes().size == 1 }; compose.waitForIdle() }
    private fun focus(tag: String) {
        if (tag.startsWith("live-channel-")) compose.onNodeWithTag("live-channel-list").performScrollToNode(hasTestTag(tag))
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.RequestFocus) { it() }
    }
    private fun focused(id: String) {
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("live-channel-$id").fetchSemanticsNodes()
            .any { it.config.getOrElse(SemanticsProperties.Focused) { false } } }
        compose.onNodeWithTag("live-channel-$id").assertIsDisplayed().assertIsFocused()
    }
    private fun preview(name: String) {
        val directory = System.getProperty("mnx.preview.dir") ?: return
        compose.runOnIdle {
            val view = compose.activity.findViewById<android.view.View>(android.R.id.content)
            val bitmap = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap)); val output = java.io.File(directory, name); output.parentFile?.mkdirs()
            output.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        }
    }
    @Test fun rapidDigitsJumpToTheLatestNumberAndOnlyAShortOkStartsItsChannel() {
        board(); focus("live-channel-id:400")
        compose.onNodeWithTag("live-channel-id:400").performKeyInput { pressKey(Key.One); pressKey(Key.Two); pressKey(Key.Three) }
        focused("id:122"); compose.onNodeWithTag("live-number-digits").assertTextEquals("123")
        compose.onNodeWithTag("live-number-id:122", true).assertTextEquals("123"); assertNull(played)
        preview("numeric-entry-de.png")
        compose.onNodeWithTag("live-channel-id:122").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals("id:122", played) }
        compose.onNodeWithTag("live-number-overlay").assertDoesNotExist()
    }
    @Test fun timeoutHidesTheIndicatorWithoutStartingPlayback() {
        board(); focus("live-channel-id:0")
        compose.onNodeWithTag("live-channel-id:0").performKeyInput { pressKey(Key.Two) }; focused("id:1")
        compose.mainClock.advanceTimeBy(2600)
        compose.onNodeWithTag("live-number-overlay").assertDoesNotExist(); assertNull(played)
    }
    @Test fun backCancelsEntryWithoutLeavingLiveTv() {
        board(); focus("live-channel-id:0")
        compose.onNodeWithTag("live-channel-id:0").performKeyInput { pressKey(Key.Two) }; focused("id:1")
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("live-number-overlay").assertDoesNotExist()
        compose.onNodeWithTag("live-channel-list").assertIsDisplayed(); assertNull(played)
    }
    @Test fun unavailableNumbersCannotPlayThePreviouslyFocusedChannel() {
        board(); focus("live-channel-id:0")
        compose.onNodeWithTag("live-channel-id:0").performKeyInput { repeat(4) { pressKey(Key.Nine) }; pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("live-number-match").assertTextContains("nicht verfügbar", substring = true)
        assertNull(played)
    }
    @Test fun hiddenChannelsNeverRevealTheirTitlesOrBecomeNumericTargets() {
        prefs = prefs.copy(value = LiveTvPreferences(hiddenChannels = setOf("id:1")))
        board(); focus("live-channel-id:0")
        compose.onNodeWithTag("live-channel-id:0").performKeyInput { pressKey(Key.Two); pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("live-number-match").assertTextContains("nicht verfügbar", substring = true)
        compose.onNodeWithTag("live-channel-id:1").assertDoesNotExist(); assertNull(played)
    }
    @Test fun selectedGroupAndFavoritesDoNotGetClearedToFindAnUnavailableNumber() {
        catalog = catalog.copy(visibleChannels = listOf(channels[0]), favoritesOnly = true, selectedGroup = "General")
        board(); focus("live-channel-id:0")
        compose.onNodeWithTag("live-channel-id:0").performKeyInput { pressKey(Key.Two); pressKey(Key.DirectionCenter) }
        assertNull(played); compose.runOnIdle { assertTrue(catalog.favoritesOnly); assertEquals("General", catalog.selectedGroup) }
        compose.onNodeWithTag("live-number-match").assertTextContains("nicht verfügbar", substring = true)
    }
    @Test fun savedQualityIsUsedWhenANumberedFamilyIsConfirmed() {
        val hd = channels[0].copy(id = "hd", name = "PL| Station 0 HD")
        val all = listOf(channels[0], hd) + channels.drop(1)
        val family = LiveChannelRows.project(all.take(2), LiveChannelSort.PROVIDER, java.util.Locale.GERMAN).single()
        catalog = catalog.copy(channels = all, visibleChannels = all)
        prefs = prefs.copy(value = LiveTvPreferences().prefer(family.preferenceKey, "hd"))
        board(); focus("live-channel-id:0")
        compose.onNodeWithTag("live-channel-id:0").performKeyInput { pressKey(Key.One); pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals("hd", played) }
    }
    @Test fun searchFieldRetainsDigitsAsTextRatherThanJumping() {
        board(); compose.onNodeWithTag("live-search").performClick()
        compose.onNodeWithTag("live-search-field").performTextInput("123")
        compose.runOnIdle { assertEquals("123", catalog.query); assertNull(played) }
        compose.onNodeWithTag("live-number-overlay").assertDoesNotExist()
    }
    @Test fun numpadAndArabicLargeTextKeepTheNumberIndicatorVisible() {
        board(arabic = true, scale = 2f); focus("live-channel-id:0")
        compose.onNodeWithTag("live-channel-id:0").performKeyInput { pressKey(Key.NumPad1); pressKey(Key.NumPad2) }
        focused("id:11"); compose.onNodeWithTag("live-number-digits").assertIsDisplayed().assertTextEquals("12")
        compose.onNodeWithTag("live-number-match").assertIsDisplayed(); preview("numeric-entry-ar-large.png"); assertNull(played)
    }
    @Test fun entryFromTheDetailPanelReturnsFocusToTheRequestedRow() {
        board(); focus("live-watch")
        compose.onNodeWithTag("live-watch").performKeyInput { pressKey(Key.Three) }
        focused("id:2"); assertNull(played)
    }
    @Test fun sidebarAndSourceSwitchCancelPendingDigitsWithoutLatePlayback() {
        board(); focus("live-channel-id:0")
        compose.onNodeWithTag("live-channel-id:0").performKeyInput { pressKey(Key.Two) }; focused("id:1")
        focus("rail-LIVE"); compose.onNodeWithTag("live-number-overlay").assertDoesNotExist()
        focus("live-channel-id:1"); compose.onNodeWithTag("live-channel-id:1").performKeyInput { pressKey(Key.One) }
        compose.runOnIdle { catalog = catalog.copy(browseScope = "other") }; ready()
        compose.onNodeWithTag("live-number-overlay").assertDoesNotExist(); assertNull(played)
    }
    @Test fun longOkDoesNotPlayOrOpenTheNormalRowMenuDuringNumericEntry() {
        board(); focus("live-channel-id:0")
        compose.onNodeWithTag("live-channel-id:0").performKeyInput { pressKey(Key.One); keyDown(Key.DirectionCenter); advanceEventTime(800); keyUp(Key.DirectionCenter) }
        compose.onNodeWithTag("live-menu-favorite").assertDoesNotExist(); assertNull(played)
    }
    @Test fun protectionEpochChangesInvalidateAnArmedConfirmation() {
        board(); focus("live-channel-id:0")
        compose.onNodeWithTag("live-channel-id:0").performKeyInput { pressKey(Key.One); keyDown(Key.DirectionCenter) }
        compose.runOnIdle { epoch++ }
        compose.onNodeWithTag("live-channel-id:0").performKeyInput { keyUp(Key.DirectionCenter) }
        compose.onNodeWithTag("live-number-overlay").assertDoesNotExist(); assertNull(played)
    }
    @Test fun movingTheActivityToTheBackgroundCancelsTheEntryBeforeResume() {
        board(); focus("live-channel-id:0")
        compose.onNodeWithTag("live-channel-id:0").performKeyInput { pressKey(Key.One) }
        compose.runOnIdle {
            val lifecycle = compose.activity.lifecycle as androidx.lifecycle.LifecycleRegistry
            lifecycle.handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_PAUSE)
            lifecycle.handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_RESUME)
        }
        compose.onNodeWithTag("live-number-overlay").assertDoesNotExist(); assertNull(played)
    }

}