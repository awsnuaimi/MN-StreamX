package mnstudio.apps.mnstreamx.ui.screen.player

import android.app.Application
import android.content.Context
import android.os.SystemClock
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import mnstudio.apps.mnstreamx.R
import mnstudio.apps.mnstreamx.domain.model.HomeLanguage
import mnstudio.apps.mnstreamx.domain.model.LiveRecoveryPhase
import mnstudio.apps.mnstreamx.domain.model.LiveRecoveryState
import mnstudio.apps.mnstreamx.ui.component.player.LocalizedPlayer
import mnstudio.apps.mnstreamx.ui.component.player.PlayerRemoteCommand
import mnstudio.apps.mnstreamx.ui.component.player.PlayerRemoteKeyGate
import mnstudio.apps.mnstreamx.ui.component.common.AppButton
import mnstudio.apps.mnstreamx.ui.theme.IPTVAppTheme
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
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
class PlayerRecoveryActionsUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var context: Context
    private var retries = 0
    private var channelChoices = 0
    private var backs = 0
    private var underlyingClicks = 0
    private val gate = PlayerRemoteKeyGate()
    private val commands = mutableListOf<PlayerRemoteCommand>()
    private var recovery by mutableStateOf(LiveRecoveryState(LiveRecoveryPhase.WAITING_NETWORK))

    private fun board(width: Dp = 960.dp, height: Dp = 720.dp, fontScale: Float = 1f,
        language: HomeLanguage = HomeLanguage.GERMAN, canRetry: Boolean = true, hasChannels: Boolean = true) {
        compose.setContent {
            LocalizedPlayer(language) {
                val density = LocalDensity.current
                val input = LocalInputModeManager.current
                val localized = LocalContext.current
                SideEffect { context = localized; input.requestInputMode(InputMode.Keyboard) }
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                    IPTVAppTheme {
                        val focus = remember { FocusRequester() }
                        Box(Modifier.size(width, height).testTag("recovery-host").onPreviewKeyEvent {
                            val key = it.nativeKeyEvent
                            gate.handle(key.keyCode, key.action, key.repeatCount,
                                false, false, false, true) { command -> commands += command }
                        }) {
                            AppButton("Underlying player control", { underlyingClicks++ },
                                Modifier.align(Alignment.BottomEnd).testTag("underlying-player-control"))
                            PlayerErrorOverlay("ERROR_CODE_IO_NETWORK_CONNECTION_FAILED", canRetry, hasChannels,
                                focus, { retries++ }, { channelChoices++ }, { backs++ }, recovery)
                            LaunchedEffect(Unit) { withFrameNanos { }; focus.requestFocus() }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun action(resource: Int) = compose.onNodeWithText(context.getString(resource))
    private fun press(resource: Int, key: Key) {
        action(resource).performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }
    private fun withinHost(resource: Int) {
        val host = compose.onNodeWithTag("recovery-host").fetchSemanticsNode().boundsInRoot
        val bounds = action(resource).fetchSemanticsNode().boundsInRoot
        assertTrue("Action must fit horizontally: $resource, $bounds in $host",
            bounds.left >= host.left && bounds.right <= host.right)
    }

    @Test fun germanActionsFitAt320DpWithLargeText() {
        board(320.dp, 320.dp, 2f)
        listOf(R.string.player_retry, R.string.player_pick_channel, R.string.player_back_channels).forEach {
            withinHost(it)
            action(it).performScrollTo().assertIsDisplayed()
        }
    }

    @Test fun wideRemoteNavigationReachesEveryActionWithoutOpeningAPlayerPanel() {
        board()
        action(R.string.player_retry).assertIsFocused()
        press(R.string.player_retry, Key.DirectionRight)
        action(R.string.player_pick_channel).assertIsFocused()
        press(R.string.player_pick_channel, Key.Enter)
        press(R.string.player_pick_channel, Key.DirectionRight)
        action(R.string.player_back_channels).assertIsFocused()
        press(R.string.player_back_channels, Key.Enter)
        press(R.string.player_back_channels, Key.DirectionLeft)
        press(R.string.player_pick_channel, Key.DirectionLeft)
        press(R.string.player_retry, Key.Enter)
        compose.runOnIdle {
            assertEquals(1, retries); assertEquals(1, channelChoices); assertEquals(1, backs)
            assertTrue(commands.isEmpty())
        }
    }

    @Test fun countdownChangesDoNotMoveFocusFromTheChosenAction() {
        board()
        press(R.string.player_retry, Key.DirectionRight)
        action(R.string.player_pick_channel).assertIsFocused()
        compose.runOnIdle {
            recovery = LiveRecoveryState(LiveRecoveryPhase.SCHEDULED, 2, SystemClock.elapsedRealtime() + 8_000)
        }
        action(R.string.player_pick_channel).assertIsFocused()
        compose.runOnIdle { recovery = LiveRecoveryState(LiveRecoveryPhase.CONNECTING, 2) }
        action(R.string.player_pick_channel).assertIsFocused()
        press(R.string.player_pick_channel, Key.Enter)
        compose.runOnIdle { assertEquals(1, channelChoices); assertEquals(0, retries) }
    }

    @Test fun compactRemoteNavigationScrollsEachActionIntoView() {
        board(320.dp, 320.dp, 2f)
        action(R.string.player_retry).assertIsFocused().assertIsDisplayed()
        press(R.string.player_retry, Key.DirectionDown)
        action(R.string.player_pick_channel).assertIsFocused().assertIsDisplayed()
        press(R.string.player_pick_channel, Key.DirectionDown)
        action(R.string.player_back_channels).assertIsFocused().assertIsDisplayed()
        press(R.string.player_back_channels, Key.Enter)
        press(R.string.player_back_channels, Key.DirectionUp)
        action(R.string.player_pick_channel).assertIsFocused().assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, backs); assertTrue(commands.isEmpty()) }
    }

    @Test fun arabicActionsFollowPhysicalArrowsInRtl() {
        board(language = HomeLanguage.ARABIC)
        val retryBounds = action(R.string.player_retry).fetchSemanticsNode().boundsInRoot
        val channelBounds = action(R.string.player_pick_channel).fetchSemanticsNode().boundsInRoot
        assertTrue(retryBounds.left > channelBounds.left)
        press(R.string.player_retry, Key.DirectionLeft)
        action(R.string.player_pick_channel).assertIsFocused()
        press(R.string.player_pick_channel, Key.DirectionLeft)
        action(R.string.player_back_channels).assertIsFocused()
        press(R.string.player_back_channels, Key.DirectionRight)
        action(R.string.player_pick_channel).assertIsFocused()
        press(R.string.player_pick_channel, Key.Enter)
        compose.runOnIdle { assertEquals(1, channelChoices); assertTrue(commands.isEmpty()) }
    }

    @Test fun unavailableRetryStartsAtChannelSelection() {
        board(canRetry = false)
        action(R.string.player_retry).assertDoesNotExist()
        action(R.string.player_pick_channel).assertIsFocused()
        press(R.string.player_pick_channel, Key.DirectionRight)
        action(R.string.player_back_channels).assertIsFocused()
        press(R.string.player_back_channels, Key.Enter)
        compose.runOnIdle { assertEquals(1, backs); assertEquals(0, retries) }
    }

    @Test fun onlyBackRemainsReachableAndArrowsCannotEscapeToThePlayer() {
        board(canRetry = false, hasChannels = false)
        action(R.string.player_retry).assertDoesNotExist()
        action(R.string.player_pick_channel).assertDoesNotExist()
        for (key in listOf(Key.DirectionLeft, Key.DirectionRight, Key.DirectionUp, Key.DirectionDown)) {
            press(R.string.player_back_channels, key)
            action(R.string.player_back_channels).assertIsFocused()
        }
        press(R.string.player_back_channels, Key.Enter)
        compose.runOnIdle { assertEquals(1, backs); assertEquals(0, underlyingClicks); assertTrue(commands.isEmpty()) }
    }

    @Test fun aHeldOkClicksOnceOnRelease() {
        board(language = HomeLanguage.ENGLISH)
        compose.runOnIdle {
            fun dispatch(action: Int, repeat: Int = 0) = compose.activity.dispatchKeyEvent(
                KeyEvent(1_000L, 1_000L + repeat, action, KeyEvent.KEYCODE_DPAD_CENTER, repeat))
            dispatch(KeyEvent.ACTION_DOWN)
            repeat(4) { dispatch(KeyEvent.ACTION_DOWN, it + 1) }
            assertEquals(0, retries)
            dispatch(KeyEvent.ACTION_UP)
        }
        compose.runOnIdle { assertEquals(1, retries); assertEquals(0, underlyingClicks); assertTrue(commands.isEmpty()) }
    }
}