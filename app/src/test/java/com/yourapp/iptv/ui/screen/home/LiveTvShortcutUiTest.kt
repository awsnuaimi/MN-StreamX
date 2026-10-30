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
import androidx.compose.ui.unit.LayoutDirection
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.ui.state.*
import com.yourapp.iptv.ui.theme.IPTVAppTheme
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w960dp-h540dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
class LiveTvShortcutUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val channels = (0 until 300).map { Channel("$it", "DE| Synthetic $it HD",
        streamUrl = "https://synthetic.invalid/$it", group = if (it < 150) "Group A" else "Group B") }
    private var state by mutableStateOf(HomeUiState(channels = channels, visibleChannels = channels.take(150),
        selectedGroup = "Group A", groups = listOf("Group A", "Group B"), isLoading = false, browseScope = "scope"))
    private var played: String? = null
    private var delaySelection = false
    private var position: HomeGridPosition? = null
    private var positionedId: String? = null

    private fun board(language: HomeLanguage = HomeLanguage.GERMAN) {
        compose.setContent { IPTVAppTheme {
            val input = LocalInputModeManager.current
            SideEffect { input.requestInputMode(InputMode.Keyboard) }
            val entry = remember { FocusRequester() }
            val appearance = HomeAppearance(language = language)
            val labels = HomeLabels(language)
            CompositionLocalProvider(LocalLayoutDirection provides if (appearance.isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                Surface { LiveTvBrowser(state, appearance, labels, HomeGuideState(), 1, CaptionUiState(), {},
                    { group ->
                        state = state.copy(selectedGroup = group, isFiltering = delaySelection)
                        if (!delaySelection) finishSelection()
                    }, { played = it.id }, {}, {}, true,
                    { id, value -> positionedId = id; position = value }, {}, {},
                    Modifier.fillMaxSize(), projectionDispatcher = Dispatchers.Main.immediate, entryFocus = entry)
                }
            }
        } }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("live-channel-list").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun finishSelection() {
        val group = state.selectedGroup
        val saved = group == "Group B"
        state = state.copy(visibleChannels = channels.filter { group == null || it.group == group }, isFiltering = false,
            selectedChannelId = "270".takeIf { saved },
            gridRestore = if (saved) HomeGridRestore(1, HomeGridPosition("267", 11, 1), takeFocus = false) else null)
    }

    private fun focus(tag: String) {
        if (tag.startsWith("live-channel-")) compose.onNodeWithTag("live-channel-list").performScrollToNode(hasTestTag(tag))
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        waitFocus(tag)
    }
    private fun waitFocus(tag: String) {
        try {
            compose.waitUntil(10_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes()
                .any { it.config.getOrElse(SemanticsProperties.Focused) { false } } }
        } catch (error: Throwable) {
            println("Expected focus=$tag; actual=" + compose.onAllNodes(isFocused()).fetchSemanticsNodes().map {
                it.config.getOrElse(SemanticsProperties.TestTag) { "no-tag" } })
            throw error
        }
        compose.onNodeWithTag(tag).assertIsFocused().assertIsDisplayed()
    }
    private fun press(key: Key, gap: Long = 0) {
        compose.onRoot().performKeyInput { if (gap > 0) advanceEventTime(gap); pressKey(key) }
    }
    private fun doubleUp() {
        compose.onRoot().performKeyInput { pressKey(Key.DirectionUp); advanceEventTime(70); pressKey(Key.DirectionUp) }
    }

    @Test fun doubleUpSelectsTheActiveCategoryAndDownRestoresTheOriginalChannelAndViewport() {
        board(); focus("live-category-all"); focus("live-channel-40")
        val before = compose.onNodeWithTag("live-channel-40").fetchSemanticsNode().boundsInRoot
        doubleUp(); waitFocus("live-category-Group A")
        compose.runOnIdle { assertEquals("40", positionedId); assertNull(played) }
        press(Key.DirectionDown); waitFocus("live-channel-40")
        val after = compose.onNodeWithTag("live-channel-40").fetchSemanticsNode().boundsInRoot
        assertEquals(before.top, after.top, 1f)
    }

    @Test fun arabicHasTheSameDoublePressAndReturnBehavior() {
        board(HomeLanguage.ARABIC); focus("live-channel-40")
        doubleUp(); waitFocus("live-category-Group A")
        press(Key.DirectionDown); waitFocus("live-channel-40")
        compose.runOnIdle { assertNull(played) }
    }

    @Test fun firstArrowMovesImmediatelyAndSeparatedPressesKeepScrolling() {
        board(); focus("live-channel-40")
        press(Key.DirectionUp); waitFocus("live-channel-39")
        press(Key.DirectionUp, 400); waitFocus("live-channel-38")
        compose.runOnIdle { assertNull(played) }
    }

    @Test fun doubleUpAtTheFirstChannelStopsAtCategoriesThenASlowerUpCanReachTheHeader() {
        board(); focus("live-channel-0")
        doubleUp(); waitFocus("live-category-Group A")
        press(Key.DirectionUp, 400); waitFocus("live-search")
    }

    @Test fun holdingUpKeepsTheChannelListInsteadOfTriggeringTheShortcut() {
        board(); focus("live-channel-40")
        compose.onRoot().performKeyInput { keyDown(Key.DirectionUp); advanceEventTime(800); keyUp(Key.DirectionUp) }
        compose.waitForIdle()
        assertTrue(compose.onAllNodes(isFocused()).fetchSemanticsNodes().any {
            it.config.getOrElse(SemanticsProperties.TestTag) { "" }.startsWith("live-channel-") })
        compose.runOnIdle { assertNull(played) }
    }

    @Test fun selectingAnotherCategoryEntersItsBookmarkInsteadOfThePreviousCategoryReturnPoint() {
        board(); focus("live-channel-40"); doubleUp(); waitFocus("live-category-Group A")
        press(Key.DirectionRight); waitFocus("live-category-Group B")
        press(Key.Enter); waitFocus("live-channel-270")
        compose.runOnIdle { assertEquals("Group B", state.selectedGroup); assertNull(played) }
        assertEquals(11, position?.offsetPx)
        assertEquals("267", position?.anchorId)
    }

    @Test fun movingBeforeASelectionFinishesCancelsItsAutomaticEntry() {
        delaySelection = true
        board(); focus("live-category-Group B")
        press(Key.Enter)
        press(Key.DirectionLeft); waitFocus("live-category-Group A")
        compose.runOnIdle { finishSelection() }
        compose.waitForIdle()
        waitFocus("live-category-Group A")
        compose.runOnIdle { assertNull(played) }
    }
}