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
class LiveTvRemoteNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val channels = (0 until 500).map { Channel("$it", "PL| Synthetic $it HD", streamUrl = "https://synthetic.invalid/$it", group = "PL| General") }
    private val initial = HomeUiState(channels = channels, visibleChannels = channels, isLoading = false,
        groups = listOf("PL| General"), selectedGroup = "PL| General", browseScope = "scope")
    private var played: String? = null
    private var restored: Long? = null
    private var state by mutableStateOf(initial)
    private var scale by mutableFloatStateOf(1f)
    private fun board(language: HomeLanguage = HomeLanguage.GERMAN) {
        compose.setContent { IPTVAppTheme {
            val input = LocalInputModeManager.current
            SideEffect { input.requestInputMode(InputMode.Keyboard) }
            val density = LocalDensity.current
            val entry = remember { FocusRequester() }
            val rail = remember { HomeSection.entries.associateWith { FocusRequester() } }
            val appearance = HomeAppearance(language = language)
            val labels = HomeLabels(language)
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale),
                LocalLayoutDirection provides if (appearance.isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                Surface { Row(Modifier.fillMaxSize()) {
                    HomeSidebar(appearance, labels, HomeSection.LIVE, false, rail, {}, {}, liveBrowser = true, browserFocus = entry)
                    LiveTvBrowser(state, appearance, labels, HomeGuideState(), 1, CaptionUiState(), {}, {},
                        { played = it.id }, {}, {}, true, { _, _ -> }, { restored = it }, {}, Modifier.weight(1f),
                        projectionDispatcher = Dispatchers.Main.immediate, sidebarFocus = rail.getValue(HomeSection.LIVE), entryFocus = entry)
                } }
            }
        } }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("live-channel-list").fetchSemanticsNodes().isNotEmpty() }
    }
    private fun focus(tag: String) {
        if (tag.startsWith("live-channel-")) compose.onNodeWithTag("live-channel-list").performScrollToNode(hasTestTag(tag))
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.RequestFocus) { it() }
    }
    private fun focused(id: String) {
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("live-channel-$id").fetchSemanticsNodes()
            .any { it.config.getOrElse(SemanticsProperties.Focused) { false } } }
        compose.onNodeWithTag("live-channel-$id").assertIsDisplayed().assertIsFocused()
    }
    @Test fun rapidDpadBatchesKeepTheLatestTargetAndNeverStartPlayback() {
        board(); focus("live-channel-0")
        compose.onNodeWithTag("live-channel-0").performKeyInput { repeat(30) { pressKey(Key.DirectionDown) } }
        focused("30")
        compose.onNodeWithTag("live-channel-30").performKeyInput { repeat(10) { pressKey(Key.DirectionUp) } }
        focused("20"); compose.runOnIdle { assertNull(played) }
    }
    @Test fun categoriesAndHeaderReturnToTheListThroughExplicitRoutes() {
        board(); focus("live-channel-0")
        compose.onNodeWithTag("live-channel-0").performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithTag("live-category-PL| General").assertIsFocused()
        compose.onNodeWithTag("live-category-PL| General").performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithTag("live-search").assertIsFocused()
        compose.onNodeWithTag("live-search").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("live-category-PL| General").assertIsFocused()
        compose.onNodeWithTag("live-category-PL| General").performKeyInput { pressKey(Key.DirectionDown) }
        focused("0")
    }
    @Test fun sidebarRoundTripReturnsToTheSameOffscreenCapableEntry() {
        board(); focus("live-channel-40")
        compose.onNodeWithTag("live-channel-40").performKeyInput { pressKey(Key.DirectionLeft) }
        compose.onNodeWithTag("rail-LIVE").assertIsFocused()
        compose.onNodeWithTag("rail-LIVE").performKeyInput { pressKey(Key.DirectionRight) }
        focused("40")
    }
    @Test fun backFromDetailsRestoresTheSameRow() {
        board(); focus("live-channel-40")
        compose.onNodeWithTag("live-channel-40").performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag("live-watch").assertIsFocused()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        focused("40"); compose.runOnIdle { assertNull(played) }
    }
    @Test fun rtlDirectionsMirrorBothSidePanels() {
        board(HomeLanguage.ARABIC); focus("live-channel-40")
        compose.onNodeWithTag("live-channel-40").performKeyInput { pressKey(Key.DirectionLeft) }
        compose.onNodeWithTag("live-watch").assertIsFocused()
        compose.onNodeWithTag("live-watch").performKeyInput { pressKey(Key.DirectionRight) }
        focused("40")
        compose.onNodeWithTag("live-channel-40").performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag("rail-LIVE").assertIsFocused()
        compose.onNodeWithTag("rail-LIVE").performKeyInput { pressKey(Key.DirectionLeft) }
        focused("40")
    }
    @Test fun pageAndChannelKeysUseTheMeasuredVisiblePage() {
        board(); focus("live-channel-0")
        val height = compose.onNodeWithTag("live-channel-list").fetchSemanticsNode().boundsInRoot.height
        val page = LiveTvNavigationPolicy.visibleRows(height.toInt(), 44, 5)
        compose.onNodeWithTag("live-channel-0").performKeyInput { pressKey(Key.PageDown) }; focused("$page")
        compose.onNodeWithTag("live-channel-$page").performKeyInput { pressKey(Key.ChannelDown) }; focused("${page * 2}")
        compose.onNodeWithTag("live-channel-${page * 2}").performKeyInput { pressKey(Key.PageUp) }; focused("$page")
        compose.onNodeWithTag("live-channel-$page").performKeyInput { pressKey(Key.ChannelUp) }; focused("0")
    }
    @Test fun restorationAtLargeFontsNeverWaitsForAnUnreachableEighthRow() {
        scale = 2f
        state = initial.copy(selectedChannelId = "240", gridRestore = HomeGridRestore(101, HomeGridPosition("234", 0, 1)))
        board(HomeLanguage.ARABIC); focused("240")
        compose.runOnIdle { assertEquals(101L, restored); assertNull(played) }
    }
    @Test fun changingFontSizeKeepsTheCurrentRowFullyVisible() {
        board(); focus("live-channel-200")
        compose.runOnIdle { scale = 2f }
        focused("200")
        val item = compose.onNodeWithTag("live-channel-200").fetchSemanticsNode().boundsInRoot
        val viewport = compose.onNodeWithTag("live-channel-list").fetchSemanticsNode().boundsInRoot
        assertTrue(item.top >= viewport.top - 1); assertTrue(item.bottom <= viewport.bottom + 1)
    }
    @Test fun leavingDuringPageAnimationCancelsLateFocusRequests() {
        board(); focus("live-channel-0")
        compose.onNodeWithTag("live-channel-0").performKeyInput { pressKey(Key.PageDown) }
        focus("live-search"); compose.mainClock.advanceTimeBy(2000)
        compose.onNodeWithTag("live-search").assertIsFocused(); compose.runOnIdle { assertNull(played) }
    }
    @Test fun updatingFavoritesDoesNotLoseListFocusDuringProjection() {
        board(); focus("live-channel-40")
        compose.runOnIdle {
            val updated = channels.map { if (it.id == "40") it.copy(isFavorite = true) else it }
            state = initial.copy(channels = updated, visibleChannels = updated)
        }
        focused("40")
    }
}