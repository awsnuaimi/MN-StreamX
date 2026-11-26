package mnstudio.apps.mnstreamx.ui.screen.player

import android.app.Application
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import mnstudio.apps.mnstreamx.R
import mnstudio.apps.mnstreamx.domain.model.HomeLanguage
import mnstudio.apps.mnstreamx.domain.model.LiveRecoveryPhase
import mnstudio.apps.mnstreamx.domain.model.LiveRecoveryState
import mnstudio.apps.mnstreamx.ui.component.common.AppButton
import mnstudio.apps.mnstreamx.ui.component.common.FocusInputEpoch
import mnstudio.apps.mnstreamx.ui.component.player.LocalizedPlayer
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
@OptIn(ExperimentalComposeUiApi::class, ExperimentalTestApi::class)
class PlayerRecoveryFocusUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val owner = object : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this)
        override val lifecycle: Lifecycle get() = registry
    }
    private lateinit var manager: FocusManager
    private lateinit var entry: FocusRequester
    private lateinit var context: Context
    private var canRetry by mutableStateOf(true)
    private var hasChannels by mutableStateOf(true)
    private var shown by mutableStateOf(true)
    private var focusActive by mutableStateOf(true)
    private var retries = 0
    private var choices = 0
    private var backs = 0

    private fun board() {
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        compose.setContent {
            val mode = LocalInputModeManager.current
            SideEffect { mode.requestInputMode(InputMode.Keyboard) }
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                LocalizedPlayer(HomeLanguage.GERMAN) {
                    manager = LocalFocusManager.current
                    context = LocalContext.current
                    entry = remember { FocusRequester() }
                    IPTVAppTheme {
                        Box(Modifier.fillMaxSize()) {
                            AppButton("Other window control", {}, Modifier.align(Alignment.BottomEnd)
                                .testTag("other-control"))
                            if (shown) PlayerErrorOverlay(null, canRetry, hasChannels, entry,
                                { retries++ }, { choices++ }, { backs++ },
                                LiveRecoveryState(LiveRecoveryPhase.WAITING_NETWORK), focusActive = focusActive)
                        }
                    }
                    LaunchedEffect(Unit) { withFrameNanos { }; entry.requestFocus() }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun action(id: Int) = compose.onNodeWithText(context.getString(id))
    private fun focus(id: Int) = action(id).performSemanticsAction(SemanticsActions.RequestFocus)
    private fun press(id: Int, key: Key) {
        action(id).performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    @Test fun removingTheFocusedRetryMovesToChannelSelection() {
        board()
        action(R.string.player_retry).assertIsFocused()
        compose.runOnIdle { canRetry = false }
        action(R.string.player_retry).assertDoesNotExist()
        action(R.string.player_pick_channel).assertIsFocused()
        press(R.string.player_pick_channel, Key.Enter)
        compose.runOnIdle { assertEquals(1, choices); assertEquals(0, retries) }
    }

    @Test fun removingFocusedChannelSelectionFallsBackToBack() {
        board()
        focus(R.string.player_pick_channel)
        compose.runOnIdle { hasChannels = false }
        action(R.string.player_back_channels).assertIsFocused()
        press(R.string.player_back_channels, Key.Enter)
        compose.runOnIdle { assertEquals(1, backs); assertEquals(0, retries) }
    }

    @Test fun availabilityChangesDoNotReplaceAStillAvailableChoice() {
        board()
        focus(R.string.player_back_channels)
        compose.runOnIdle { canRetry = false; hasChannels = false }
        action(R.string.player_back_channels).assertIsFocused()
        compose.runOnIdle { canRetry = true; hasChannels = true }
        action(R.string.player_back_channels).assertIsFocused()
    }

    @Test fun stopAndResumeRecallTheChosenActionAfterAndroidClearsFocus() {
        board()
        focus(R.string.player_back_channels)
        compose.runOnIdle {
            owner.registry.currentState = Lifecycle.State.CREATED
            manager.clearFocus(force = true)
        }
        action(R.string.player_back_channels).assertIsNotFocused()
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        action(R.string.player_back_channels).assertIsFocused()
    }

    @Test fun returningFromAnotherPanelRecallsTheLastConfirmedAction() {
        board()
        focus(R.string.player_back_channels)
        press(R.string.player_back_channels, Key.Enter)
        compose.runOnIdle { focusActive = false }
        compose.onNodeWithTag("other-control").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.runOnIdle { focusActive = true }
        action(R.string.player_back_channels).assertIsFocused()
        compose.runOnIdle { assertEquals(1, backs) }
    }

    @Test fun aDisposedOverlayCannotRestoreFocusAfterResume() {
        board()
        focus(R.string.player_back_channels)
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.CREATED; shown = false }
        compose.onNodeWithTag("other-control").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        compose.onNodeWithTag("other-control").assertIsFocused()
        compose.onNodeWithTag("player-recovery-panel").assertDoesNotExist()
    }

    @Test fun availabilityChangesWhileStoppedWaitForResumeAndChooseAnAvailableAction() {
        board()
        compose.runOnIdle {
            owner.registry.currentState = Lifecycle.State.CREATED
            manager.clearFocus(force = true)
        }
        // The stopped player's window no longer owns input; a fake lifecycle alone leaves it active.
        compose.onNodeWithTag("other-control").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.runOnIdle { canRetry = false }
        compose.onNodeWithTag("other-control").assertIsFocused()
        action(R.string.player_pick_channel).assertIsNotFocused()
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        action(R.string.player_pick_channel).assertIsFocused()
    }

    @Test fun anInactiveOverlayCannotTakeFocusFromAnotherPanel() {
        board()
        focus(R.string.player_back_channels)
        compose.runOnIdle { focusActive = false }
        compose.onNodeWithTag("other-control").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.runOnIdle { canRetry = false; hasChannels = false }
        compose.onNodeWithTag("other-control").assertIsFocused()
        action(R.string.player_back_channels).assertIsNotFocused()
        compose.runOnIdle { focusActive = true }
        action(R.string.player_back_channels).assertIsFocused()
    }

    @Test fun newerInputAfterActivationCancelsTheQueuedReturn() {
        board()
        focus(R.string.player_back_channels)
        compose.runOnIdle { focusActive = false }
        compose.onNodeWithTag("other-control").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { focusActive = true }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { FocusInputEpoch.advance() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.onNodeWithTag("other-control").assertIsFocused()
        action(R.string.player_back_channels).assertIsNotFocused()
    }

    @Test fun newerInputAfterResumeWinsOverTheQueuedRestoration() {
        board()
        focus(R.string.player_back_channels)
        compose.runOnIdle {
            owner.registry.currentState = Lifecycle.State.CREATED
            manager.clearFocus(force = true)
        }
        compose.runOnIdle {
            owner.registry.currentState = Lifecycle.State.RESUMED
            FocusInputEpoch.advance()
        }
        action(R.string.player_back_channels).assertIsNotFocused()
        compose.onNodeWithTag("other-control").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithTag("other-control").assertIsFocused()
        action(R.string.player_back_channels).assertIsNotFocused()
    }
}