package mnstudio.apps.mnstreamx.ui.screen.player

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import mnstudio.apps.mnstreamx.domain.model.*
import mnstudio.apps.mnstreamx.testing.waitForFocus
import mnstudio.apps.mnstreamx.ui.component.common.rememberContextFocus
import mnstudio.apps.mnstreamx.ui.state.*
import mnstudio.apps.mnstreamx.ui.theme.IPTVAppTheme
import org.junit.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w960dp-h720dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalComposeUiApi::class, ExperimentalTestApi::class)
class PlayerPanelFocusUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val channels = (0 until 120).map { Channel("$it", "Station $it", streamUrl = "synthetic:$it") }
    private var panel by mutableStateOf(PlayerPanel.MORE)
    private var initial = PlayerMoreAction.FAVORITE
    private var last by mutableStateOf(LastChannelUiState())
    private var loading by mutableStateOf(false)

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
                    if (panel == PlayerPanel.CHANNELS) PlayerChannelPanel(if (loading) emptyList() else channels,
                        if (loading) -1 else 75, false,
                        {}, { panel = PlayerPanel.MORE }, { _, _ -> false }, loading)
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

    private fun waitForChannelProjection() {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("player-channel-loading").fetchSemanticsNodes().isEmpty()
        }
    }

    @Test fun changingTheFocusedProviderGroupShowsItsChannelsBeforeAnySelection() {
        val groups = listOf("PL| Sport", "PL| News")
        val catalog = channels.take(6).mapIndexed { index, item -> item.copy(group = groups[index / 3]) }
        var played = ""
        compose.setContent {
            val input = LocalInputModeManager.current
            SideEffect { input.requestInputMode(InputMode.Keyboard) }
            IPTVAppTheme {
                PlayerChannelPanel(catalog, -1, false, { played = it }, {}, { _, _ -> false },
                    startWithCountries = true)
            }
        }
        focusLatestDialog()
        compose.waitForFocus("live-country-PL")
        compose.onNodeWithTag("live-country-PL").performClick()
        compose.waitForFocus("live-group-group:PL| Sport")
        compose.onNodeWithTag("live-group-group:PL| Sport").performKeyInput { pressKey(Key.DirectionDown) }
        compose.waitForFocus("live-group-group:PL| News")
        compose.onNodeWithTag("player-channel-3").assertIsDisplayed()
        compose.onNodeWithTag("live-group-group:PL| News").performKeyInput { pressKey(Key.DirectionRight) }
        compose.waitForFocus("player-channel-3")
        compose.onNodeWithTag("player-channel-3").performKeyInput { pressKey(Key.DirectionDown) }
        compose.waitForFocus("player-channel-4")
        compose.onNodeWithTag("player-channel-4").performKeyInput { pressKey(Key.DirectionUp) }
        compose.waitForFocus("player-channel-3")
        compose.onNodeWithTag("player-channel-3").performKeyInput { pressKey(Key.DirectionDown) }
        compose.waitForFocus("player-channel-4")
        compose.onNodeWithTag("player-channel-4").performKeyInput { pressKey(Key.DirectionLeft) }
        compose.waitForFocus("live-group-group:PL| News")
        compose.onNodeWithTag("live-group-group:PL| News").performKeyInput { pressKey(Key.DirectionRight) }
        compose.waitForFocus("player-channel-4")
        compose.runOnIdle { assertTrue(played.isEmpty()) }
        compose.onNodeWithTag("player-channel-4").performKeyInput { pressKey(Key.Enter) }
        compose.runOnIdle { assertEquals("4", played) }
    }

    @Test fun rapidArrowsScrollChannelsWithoutStartingPlayback() {
        var played: String? = null
        compose.setContent {
            val input = LocalInputModeManager.current
            SideEffect { input.requestInputMode(InputMode.Keyboard) }
            IPTVAppTheme {
                PlayerChannelPanel(channels, 75, false, { played = it }, {}, { _, _ -> false })
            }
        }
        focusLatestDialog()
        compose.waitForFocus("player-channel-75")
        compose.onNodeWithTag("player-channel-75").performKeyInput {
            repeat(20) { pressKey(Key.DirectionDown) }
        }
        compose.waitForFocus("player-channel-95")
        compose.onNodeWithTag("player-channel-95").performKeyInput {
            repeat(10) { pressKey(Key.DirectionUp) }
        }
        compose.waitForFocus("player-channel-85")
        compose.runOnIdle { assertNull(played) }
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

    @Test fun aLoadingPanelKeepsCloseReachableAndRestoresThePlayingRowWhenTheQueueArrives() {
        loading = true
        board()
        compose.onNodeWithTag("player-more-list").performScrollToIndex(PlayerMoreAction.CHANNELS.ordinal)
        compose.onNodeWithTag("player-more-CHANNELS").performClick()
        focusLatestDialog()
        compose.onNodeWithTag("player-channel-loading").assertIsDisplayed()
        compose.waitForFocus("player-channels-close")
        compose.runOnIdle { loading = false }
        waitForChannelProjection()
        compose.waitForFocus("player-channel-75")
        compose.onNodeWithTag("player-channels-close").performClick()
        focusLatestDialog()
        compose.waitForFocus("player-more-CHANNELS")
    }

    @Test fun queueArrivalCannotStealFocusAfterTheUserMovesWhileLoading() {
        loading = true
        board()
        compose.onNodeWithTag("player-more-list").performScrollToIndex(PlayerMoreAction.CHANNELS.ordinal)
        compose.onNodeWithTag("player-more-CHANNELS").performClick()
        focusLatestDialog()
        compose.waitForFocus("player-channels-close")
        compose.onNodeWithTag("player-channels-close").performKeyInput { pressKey(Key.DirectionDown) }
        compose.runOnIdle { loading = false }
        waitForChannelProjection()
        compose.onNodeWithTag("player-channels-close").assertIsFocused()
    }
}