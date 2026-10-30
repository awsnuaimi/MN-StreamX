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
class LiveTvFocusMapUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val channels = (0 until 100).map { Channel("$it", "Synthetic $it HD",
        streamUrl = "https://synthetic.invalid/$it", group = "Group $it") }
    private var state by mutableStateOf(HomeUiState(channels = channels, visibleChannels = channels,
        groups = channels.mapNotNull { it.group }, isLoading = false, browseScope = "scope"))
    private var preferences by mutableStateOf(LiveTvPreferencesState(ready = true))
    private var scale by mutableFloatStateOf(1f)
    private var played: String? = null
    private var query = ""
    private fun board(language: HomeLanguage = HomeLanguage.GERMAN, side: SidebarSide = SidebarSide.AUTO) {
        compose.setContent { IPTVAppTheme {
            val input = LocalInputModeManager.current
            SideEffect { input.requestInputMode(InputMode.Keyboard) }
            val density = LocalDensity.current
            val appearance = HomeAppearance(language = language, sidebarSide = side)
            val labels = HomeLabels(language)
            val entry = remember { FocusRequester() }
            val rail = remember { HomeSection.entries.associateWith { FocusRequester() } }
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale),
                LocalLayoutDirection provides if (appearance.sidebarOnRight) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                Surface { Row(Modifier.fillMaxSize()) {
                    CompositionLocalProvider(LocalLayoutDirection provides if (appearance.isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                        HomeSidebar(appearance, labels, HomeSection.LIVE, false, rail, {}, {}, liveBrowser = true, browserFocus = entry)
                        LiveTvBrowser(state, appearance, labels, HomeGuideState(), 1, CaptionUiState(),
                            { query = it; state = state.copy(query = it) }, {}, { played = it.id }, {}, {}, true, { _, _ -> }, {}, {},
                            Modifier.weight(1f), projectionDispatcher = Dispatchers.Main.immediate,
                            sidebarFocus = rail.getValue(HomeSection.LIVE), entryFocus = entry, preferences = preferences,
                            onPreference = {})
                    }
                } }
            }
        } }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("live-channel-list").fetchSemanticsNodes().isNotEmpty() }
    }
    private fun focus(tag: String) {
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        waitFocus(tag)
    }
    private fun waitFocus(tag: String) {
        compose.waitUntil(10_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes()
            .any { it.config.getOrElse(SemanticsProperties.Focused) { false } } }
        compose.onNodeWithTag(tag).assertIsFocused()
    }
    private fun press(tag: String, key: Key) { compose.onNodeWithTag(tag).performKeyInput { pressKey(key) } }

    @Test fun sidebarKeepsVerticalOrderAndStopsAtBothEnds() {
        board(); focus("rail-DASHBOARD")
        press("rail-DASHBOARD", Key.DirectionUp); waitFocus("rail-DASHBOARD")
        val sections = listOf("DASHBOARD", "LIVE", "MOVIES", "SERIES", "FAVORITES", "MORE", "SETTINGS")
        sections.zipWithNext().forEach { (a, b) -> press("rail-$a", Key.DirectionDown); waitFocus("rail-$b") }
        press("rail-SETTINGS", Key.DirectionDown); waitFocus("rail-SETTINGS")
        sections.asReversed().zipWithNext().forEach { (a, b) -> press("rail-$a", Key.DirectionUp); waitFocus("rail-$b") }
        compose.runOnIdle { assertNull(played) }
    }
    @Test fun sidebarOutwardArrowDoesNotEscapeIntoTheChannelList() {
        board(); focus("rail-LIVE")
        press("rail-LIVE", Key.DirectionLeft); waitFocus("rail-LIVE")
    }
    @Test fun headerButtonsHaveExplicitNeighborsAndAnOuterBoundary() {
        board(); focus("live-search")
        press("live-search", Key.DirectionRight); waitFocus("live-hidden")
        press("live-hidden", Key.DirectionRight); waitFocus("live-sort")
        press("live-sort", Key.DirectionRight); waitFocus("live-sort")
        press("live-sort", Key.DirectionLeft); waitFocus("live-hidden")
        press("live-hidden", Key.DirectionLeft); waitFocus("live-search")
        press("live-search", Key.DirectionLeft); waitFocus("rail-LIVE")
    }
    @Test fun searchFieldKeepsCursorArrowsAndHasVerticalExits() {
        board(); focus("live-search")
        press("live-search", Key.Enter); waitFocus("live-search-field")
        compose.onNodeWithTag("live-search-field").performTextInput("Synthetic")
        press("live-search-field", Key.DirectionLeft); waitFocus("live-search-field")
        press("live-search-field", Key.DirectionUp); waitFocus("live-search")
        press("live-search", Key.DirectionDown); waitFocus("live-search-field")
        press("live-search-field", Key.DirectionDown); waitFocus("live-category-all")
        compose.runOnIdle { assertEquals("Synthetic", query); assertNull(played) }
    }
    @Test fun categoriesCanReachAndFocusALazyChipOutsideTheViewport() {
        board(); focus("live-category-all")
        compose.onNodeWithTag("live-category-all").performKeyInput { repeat(35) { pressKey(Key.DirectionRight) } }
        waitFocus("live-category-Group 34")
        compose.onNodeWithTag("live-category-Group 34").assertIsDisplayed()
        press("live-category-Group 34", Key.DirectionUp); waitFocus("live-search")
        compose.runOnIdle { assertNull(played) }
    }
    @Test fun leavingCategoriesCancelsPendingLazyFocusRequests() {
        board(); focus("live-category-all")
        compose.onNodeWithTag("live-category-all").performKeyInput { repeat(35) { pressKey(Key.DirectionRight) }; pressKey(Key.DirectionUp) }
        waitFocus("live-search")
        compose.mainClock.advanceTimeBy(1000)
        waitFocus("live-search")
    }
    @Test fun arabicCanPlaceTheSidebarOnTheLeftWithoutReversingItsRoutes() {
        board(HomeLanguage.ARABIC, SidebarSide.LEFT); focus("live-channel-0")
        press("live-channel-0", Key.DirectionLeft); waitFocus("rail-LIVE")
        press("rail-LIVE", Key.DirectionRight); waitFocus("live-channel-0")
        val list = compose.onNodeWithTag("live-channel-list").fetchSemanticsNode().boundsInRoot
        val detail = compose.onNodeWithTag("live-detail").fetchSemanticsNode().boundsInRoot
        assertTrue(list.right <= detail.left)
    }
    @Test fun germanCanPlaceTheSidebarOnTheRightWithoutReversingItsRoutes() {
        board(HomeLanguage.GERMAN, SidebarSide.RIGHT); focus("live-channel-0")
        press("live-channel-0", Key.DirectionRight); waitFocus("rail-LIVE")
        press("rail-LIVE", Key.DirectionLeft); waitFocus("live-channel-0")
        val list = compose.onNodeWithTag("live-channel-list").fetchSemanticsNode().boundsInRoot
        val detail = compose.onNodeWithTag("live-detail").fetchSemanticsNode().boundsInRoot
        assertTrue(detail.right <= list.left)
    }
    @Test fun detailActionsUseTheDirectionInWhichTheyAreActuallyLaidOut() {
        board(); focus("live-watch")
        val watch = compose.onNodeWithTag("live-watch").fetchSemanticsNode().boundsInRoot
        val translation = compose.onNodeWithTag("live-translation").fetchSemanticsNode().boundsInRoot
        val arrow = if (translation.top > watch.top + 1) Key.DirectionDown else Key.DirectionRight
        press("live-watch", arrow); waitFocus("live-translation")
        press("live-translation", if (arrow == Key.DirectionDown) Key.DirectionUp else Key.DirectionLeft)
        waitFocus("live-watch")
        press("live-watch", Key.DirectionLeft); waitFocus("live-channel-0")
        compose.runOnIdle { assertNull(played) }
    }
    @Test fun anEmptyResultOffersAnActionAndAStableRouteToCategories() {
        state = state.copy(visibleChannels = emptyList())
        board(); focus("live-category-all")
        press("live-category-all", Key.DirectionDown); waitFocus("live-empty-action")
        press("live-empty-action", Key.DirectionDown); waitFocus("live-empty-action")
        press("live-empty-action", Key.DirectionUp); waitFocus("live-category-all")
        press("live-category-all", Key.DirectionDown); waitFocus("live-empty-action")
        press("live-empty-action", Key.DirectionLeft); waitFocus("rail-LIVE")
    }
    @Test fun loadingHasNoDanglingDetailTargetAndStillAllowsLeavingTheList() {
        board(); focus("live-channel-0")
        compose.runOnIdle { preferences = preferences.copy(ready = false) }
        compose.waitForIdle()
        waitFocus("live-list-entry")
        press("live-list-entry", Key.DirectionRight); waitFocus("live-list-entry")
        press("live-list-entry", Key.DirectionLeft); waitFocus("rail-LIVE")
        compose.runOnIdle { assertNull(played) }
    }
    @Test fun hidingTheDetailPanelAtLargeFontsReturnsFocusToTheCurrentChannel() {
        board(); focus("live-watch")
        compose.runOnIdle { scale = 2f }
        compose.waitForIdle()
        waitFocus("live-channel-0")
        compose.onNodeWithTag("live-detail").assertDoesNotExist()
        compose.runOnIdle { assertNull(played) }
    }
    @Test fun reloadingWhileInDetailsReturnsFocusToTheMountedListEntry() {
        board(); focus("live-watch")
        compose.runOnIdle { preferences = preferences.copy(ready = false) }
        compose.waitForIdle()
        waitFocus("live-list-entry")
        press("live-list-entry", Key.DirectionLeft); waitFocus("rail-LIVE")
    }
    @Test fun closingTheHiddenDialogReturnsToItsOwnHeaderButton() {
        board(); focus("live-hidden")
        press("live-hidden", Key.Enter); waitFocus("live-hidden-close")
        press("live-hidden-close", Key.Enter); waitFocus("live-hidden")
    }
}