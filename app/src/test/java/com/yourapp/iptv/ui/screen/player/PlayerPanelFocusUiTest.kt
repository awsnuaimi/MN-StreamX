package com.yourapp.iptv.ui.screen.player

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.testing.waitForFocus
import com.yourapp.iptv.ui.component.common.rememberContextFocus
import com.yourapp.iptv.ui.state.*
import com.yourapp.iptv.ui.theme.IPTVAppTheme
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w960dp-h720dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalComposeUiApi::class)
class PlayerPanelFocusUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val channels = (0 until 120).map { Channel("$it", "Station $it", streamUrl = "synthetic:$it") }
    private var panel by mutableStateOf(PlayerPanel.MORE)
    private var initial = PlayerMoreAction.FAVORITE
    private var last by mutableStateOf(LastChannelUiState())

    private fun board() {
        compose.setContent {
            val input = LocalInputModeManager.current
            SideEffect { input.requestInputMode(InputMode.Keyboard) }
            val memory = rememberContextFocus("panel-test")
            IPTVAppTheme {
                CompositionLocalProvider(LocalPlayerFocus provides memory) {
                    if (panel == PlayerPanel.MORE) PlayerMoreDialog(channels[75], PlayerAppearance(),
                        FavoriteUiState(), true, initial, { _, _ -> false }, { panel = PlayerPanel.WATCHING },
                        onAction = { if (it == PlayerMoreAction.CHANNELS) panel = PlayerPanel.CHANNELS },
                        onRetryFavorites = {}, lastChannel = last)
                    if (panel == PlayerPanel.CHANNELS) PlayerChannelPanel(channels, 75, null, "", false,
                        {}, { panel = PlayerPanel.MORE }, { _, _ -> false })
                }
            }
        }
        focusLatestDialog()
    }

    private fun focusLatestDialog() {
        // Android sends this to every newly opened window; Robolectric needs it explicitly.
        compose.runOnIdle {
            val dialog = checkNotNull(ShadowDialog.getLatestDialog())
            checkNotNull(dialog.window).decorView.dispatchWindowFocusChanged(true)
        }
        compose.waitForIdle()
    }

    private fun openChannels() {
        compose.onNodeWithTag("player-more-list").performScrollToIndex(PlayerMoreAction.CHANNELS.ordinal)
        compose.onNodeWithTag("player-more-CHANNELS").performClick()
        focusLatestDialog()
        compose.waitForFocus("player-channel-75")
    }

    @Test fun closingTheChannelPanelReturnsToTheActionThatOpenedIt() {
        board()
        openChannels()
        compose.onNodeWithTag("player-channels-close").performClick()
        focusLatestDialog()
        compose.waitForFocus("player-more-CHANNELS")
    }

    @Test fun reopeningThePanelRecallsTheBrowsedRowOutsideTheInitialViewport() {
        board()
        openChannels()
        compose.onNodeWithTag("player-channel-list").performScrollToIndex(100)
        compose.onNodeWithTag("player-channel-100").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithTag("player-channels-close").performClick()
        focusLatestDialog()
        compose.waitForFocus("player-more-CHANNELS")
        compose.onNodeWithTag("player-more-CHANNELS").performClick()
        focusLatestDialog()
        compose.waitForFocus("player-channel-100")
        compose.onNodeWithTag("player-channel-100").assertIsDisplayed()
    }

    @Test fun anUnavailableInitialActionFallsBackWithoutJumpingWhenItBecomesAvailable() {
        initial = PlayerMoreAction.LAST_CHANNEL
        board()
        compose.waitForFocus("player-more-FAVORITE")
        compose.runOnIdle { last = LastChannelUiState(targetId = "1") }
        compose.onNodeWithTag("player-more-FAVORITE").assertIsFocused()
    }
}