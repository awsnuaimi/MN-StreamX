package mnstudio.apps.mnstreamx.ui.screen.player

import android.app.Application
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import mnstudio.apps.mnstreamx.domain.model.Channel
import mnstudio.apps.mnstreamx.domain.model.WatchedChannel
import mnstudio.apps.mnstreamx.ui.state.WatchHistoryError
import mnstudio.apps.mnstreamx.ui.state.WatchHistoryUiState
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w960dp-h720dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalComposeUiApi::class, ExperimentalTestApi::class)
class PlayerRecentChannelsRailUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val scope = "synthetic-profile-a"
    private val rows = (0 until 20).map { index ->
        WatchedChannel(Channel("$index", "Station $index", streamUrl = "synthetic:$index"), 100L - index)
    }
    private var state by mutableStateOf(WatchHistoryUiState(sourceKey = scope, rows = rows, isLoading = false))
    private var expectedSource by mutableStateOf(scope)
    private var playingId by mutableStateOf<String?>("0")
    private var shown by mutableStateOf(true)
    private var closes = 0
    private var retries = 0
    private var escapedEnter = 0
    private val played = mutableListOf<Pair<String, String>>()

    private fun close() { closes++; shown = false }

    private fun board(direction: LayoutDirection = LayoutDirection.Ltr, compact: Boolean = false) {
        compose.setContent {
            val input = LocalInputModeManager.current
            SideEffect { input.requestInputMode(InputMode.Keyboard) }
            val watchingFocus = remember { FocusRequester() }
            LaunchedEffect(shown) {
                if (!shown) { withFrameNanos { }; watchingFocus.requestFocus() }
            }
            MaterialTheme {
                CompositionLocalProvider(LocalLayoutDirection provides direction) {
                    Box(Modifier.fillMaxSize().onKeyEvent {
                        if (it.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER) escapedEnter++
                        false
                    }.focusRequester(watchingFocus).focusable().testTag("watching")) {
                        // Mirrors the player's modal Back route, without creating a media player.
                        if (shown) BackHandler { close() }
                        if (shown) Box(if (compact) Modifier.size(320.dp, 240.dp) else Modifier.fillMaxSize()) {
                            PlayerRecentChannelsRailContent(state, playingId, expectedSource,
                                onDismiss = ::close,
                                onPlay = { channel, source -> played += channel.id to source; shown = false },
                                onRetry = { retries++ })
                        }
                    }
                }
            }
        }
        waitForRailFocus()
    }

    private fun rail() = compose.onNodeWithTag("player-recent-rail")
    private fun press(key: Key) { rail().performKeyInput { pressKey(key) }; compose.waitForIdle() }
    private fun selected(id: String) = compose.onNodeWithTag("player-recent-$id").assertIsSelected()
    private fun waitForRailFocus() {
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasTestTag("player-recent-rail") and isFocused()).fetchSemanticsNodes().size == 1
        }
    }
    private fun native(action: Int, code: Int, repeat: Int = 0, flags: Int = 0) {
        compose.runOnIdle {
            compose.activity.dispatchKeyEvent(KeyEvent(1_000L, 1_000L + repeat, action, code,
                repeat, 0, -1, 0, flags))
        }
        compose.waitForIdle()
    }

    @Test fun openingFocusesTheRailAndRightThenOkRecallsThePreviousChannel() {
        board()
        rail().assertIsFocused()
        selected("1")
        press(Key.DirectionRight)
        selected("1")
        compose.runOnIdle { assertTrue(played.isEmpty()); assertEquals(0, closes) }
        press(Key.Enter)
        compose.runOnIdle { assertEquals(listOf("1" to scope), played); assertEquals(0, escapedEnter) }
        rail().assertDoesNotExist()
    }

    @Test fun rapidArrowsScrollToBothBoundsWithoutPlayingOrLosingFocus() {
        board()
        rail().performKeyInput { repeat(30) { pressKey(Key.DirectionDown) } }
        compose.waitForIdle()
        selected("19").assertIsDisplayed()
        rail().assertIsFocused()
        rail().performKeyInput { repeat(30) { pressKey(Key.DirectionUp) } }
        compose.waitForIdle()
        selected("0").assertIsDisplayed()
        compose.runOnIdle { assertTrue(played.isEmpty()); assertEquals(0, escapedEnter) }
    }

    @Test fun holdingOkActivatesOnlyOnceOnReleaseAndDoesNotLeakToThePlayer() {
        board()
        native(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER)
        repeat(5) { native(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, it + 1) }
        compose.runOnIdle { assertTrue(played.isEmpty()) }
        native(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER)
        compose.runOnIdle { assertEquals(listOf("1" to scope), played); assertEquals(0, escapedEnter) }
        compose.onNodeWithTag("watching").assertIsFocused()
    }

    @Test fun cancelledOkDoesNotStartPlayback() {
        board()
        native(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER)
        native(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER, flags = KeyEvent.FLAG_CANCELED)
        rail().assertIsFocused()
        compose.runOnIdle { assertTrue(played.isEmpty()) }
    }

    @Test fun loadingCannotActivateStaleRowsAndArrivalKeepsTheRailFocused() {
        state = state.copy(isLoading = true)
        board()
        compose.onNodeWithTag("player-recent-loading").assertIsDisplayed()
        press(Key.Enter)
        compose.runOnIdle { assertTrue(played.isEmpty()); assertEquals(0, retries) }
        compose.runOnIdle { state = state.copy(isLoading = false, rows = rows.take(3)) }
        waitForRailFocus()
        selected("1")
        press(Key.Enter)
        compose.runOnIdle { assertEquals(listOf("1" to scope), played) }
    }

    @Test fun emptyHistoryKeepsCloseReachableWithoutStartingPlayback() {
        state = state.copy(rows = emptyList())
        board()
        compose.onNodeWithTag("player-recent-empty").assertIsDisplayed()
        press(Key.DirectionDown)
        press(Key.DirectionUp)
        press(Key.Enter)
        rail().assertIsFocused()
        compose.runOnIdle { assertTrue(played.isEmpty()); assertEquals(0, retries) }
        press(Key.DirectionLeft)
        compose.runOnIdle { assertEquals(1, closes) }
        compose.onNodeWithTag("watching").assertIsFocused()
    }

    @Test fun everyErrorHasAnExplicitRetryAndNeverPlaysRetainedRows() {
        state = state.copy(error = WatchHistoryError.READ)
        board()
        WatchHistoryError.entries.forEachIndexed { index, error ->
            compose.runOnIdle { state = state.copy(error = error) }
            compose.onNodeWithTag("player-recent-error").assertIsDisplayed()
            press(Key.Enter)
            compose.runOnIdle { assertEquals(index + 1, retries); assertTrue(played.isEmpty()) }
            rail().assertIsFocused()
        }
        compose.runOnIdle { state = state.copy(error = null) }
        selected("1")
        press(Key.Enter)
        compose.runOnIdle { assertEquals(listOf("1" to scope), played) }
    }

    @Test fun changingProfilesHidesOldRowsEvenWhenChannelIdsOverlap() {
        board()
        compose.runOnIdle { expectedSource = "synthetic-profile-b" }
        compose.onNodeWithTag("player-recent-empty").assertIsDisplayed()
        compose.onNodeWithTag("player-recent-1").assertDoesNotExist()
        press(Key.Enter)
        compose.runOnIdle { assertTrue(played.isEmpty()) }
        compose.runOnIdle {
            state = state.copy(sourceKey = expectedSource, rows = rows.take(2).map {
                it.copy(channel = it.channel.copy(name = "Other profile ${it.channel.id}"))
            })
        }
        selected("1")
        compose.onNodeWithText("Other profile 1").assertIsDisplayed()
        press(Key.Enter)
        compose.runOnIdle { assertEquals(listOf("1" to "synthetic-profile-b"), played) }
    }

    @Test fun missingSourceCannotPlayRowsRetainedInState() {
        state = state.copy(needsSource = true)
        board()
        compose.onNodeWithTag("player-recent-empty").assertIsDisplayed()
        press(Key.Enter)
        compose.runOnIdle { assertTrue(played.isEmpty()) }
    }

    @Test fun anUpdatePreservesUserSelectionAndRemovedRowsFallBackSafely() {
        board()
        press(Key.DirectionDown)
        selected("2")
        compose.runOnIdle { playingId = "3"; state = state.copy(rows = rows.take(5).reversed()) }
        selected("2")
        compose.runOnIdle { state = state.copy(rows = rows.take(2)) }
        selected("0")
        rail().assertIsFocused()
        press(Key.Enter)
        compose.runOnIdle { assertEquals(listOf("0" to scope), played) }
    }

    @Test fun leftClosesOnReleaseAndReopeningRestoresRemoteInput() {
        board()
        native(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT)
        repeat(3) { native(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, it + 1) }
        compose.runOnIdle { assertEquals(0, closes) }
        native(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_LEFT)
        compose.runOnIdle { assertEquals(1, closes); assertTrue(played.isEmpty()) }
        rail().assertDoesNotExist()
        compose.onNodeWithTag("watching").assertIsFocused()
        compose.runOnIdle { shown = true }
        waitForRailFocus()
        selected("1")
        press(Key.Enter)
        compose.runOnIdle { assertEquals(listOf("1" to scope), played) }
    }

    @Test fun backClosesTheModalWithoutTriggeringPlayback() {
        board()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        rail().assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, closes); assertTrue(played.isEmpty()) }
        compose.onNodeWithTag("watching").assertIsFocused()
    }

    @Test fun theRailStaysOnThePhysicalRightInRtlAndCanScrollInASmallViewport() {
        board(direction = LayoutDirection.Rtl, compact = true)
        val watching = compose.onNodeWithTag("watching").fetchSemanticsNode().boundsInRoot
        val bounds = rail().fetchSemanticsNode().boundsInRoot
        // In RTL the compact host is on the right; its 16dp inset must remain inside it.
        assertTrue(bounds.right <= watching.right)
        assertTrue(bounds.left > watching.width / 2f)
        rail().performKeyInput { repeat(25) { pressKey(Key.DirectionDown) } }
        compose.waitForIdle()
        selected("19").assertIsDisplayed()
        rail().assertIsFocused()
        compose.runOnIdle { assertTrue(played.isEmpty()) }
    }
}