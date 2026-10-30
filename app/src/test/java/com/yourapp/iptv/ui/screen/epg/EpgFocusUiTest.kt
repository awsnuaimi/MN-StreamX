package com.yourapp.iptv.ui.screen.epg

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
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.testing.waitForFocus
import com.yourapp.iptv.ui.state.*
import com.yourapp.iptv.ui.theme.IPTVAppTheme
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w960dp-h720dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalComposeUiApi::class, ExperimentalTestApi::class)
class EpgFocusUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val channels = (0 until 180).map { Channel("$it", "Station $it", streamUrl = "synthetic:$it") }
    private val first = Program("Current", 1_000L, 2_000L)
    private val next = Program("Next", 2_000L, 3_000L)
    private var state by mutableStateOf(EpgUiState(queue = PlaybackQueue.create(channels),
        selectedChannelId = "150", isLoading = false))
    private var schedule by mutableStateOf(PlayerEpgUiState(channelId = "150", isLoading = true))
    private var returned by mutableStateOf<String?>(null)

    private fun board() {
        compose.setContent {
            val input = LocalInputModeManager.current
            SideEffect { input.requestInputMode(InputMode.Keyboard) }
            IPTVAppTheme {
                EpgGuideContent(state, FavoriteUiState(), schedule, returned, EpgGuideActions(
                    back = {}, query = { state = state.copy(query = it) }, group = {}, favorites = {},
                    selectChannel = { state = state.copy(selectedChannelId = it) },
                    selectProgram = { state = state.copy(selectedProgramKey = it) },
                    toggleFavorite = {}, retryFavorites = {}, reload = {}, clearFilters = {},
                    restoreChannel = { state = state.copy(selectedChannelId = it) },
                    returnHandled = { returned = null }, play = {}
                ))
            }
        }
        compose.waitForFocus("epg-channel-150")
    }

    @Test fun initialSelectionOutsideTheViewportIsMountedAndFocused() {
        board()
        compose.onNodeWithTag("epg-channel-150").assertIsDisplayed().assertIsFocused()
    }

    @Test fun movingToProgramsWaitsForLoadingAndSelectsTheCurrentProgram() {
        board()
        compose.onNodeWithTag("epg-channel-150").performKeyInput { pressKey(Key.DirectionRight) }
        compose.runOnIdle { schedule = PlayerEpgUiState("150", EpgSchedule(listOf(first, next)), nowMillis = 1_500L) }
        compose.waitForFocus("epg-program-${EpgProgramSelection.key(first)}")
    }

    @Test fun freshInputInSearchCancelsPendingProgramRestoration() {
        board()
        compose.onNodeWithTag("epg-channel-150").performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag("epg-query").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithTag("epg-query").performKeyInput { pressKey(Key.DirectionRight) }
        compose.runOnIdle { schedule = PlayerEpgUiState("150", EpgSchedule(listOf(first, next)), nowMillis = 1_500L) }
        compose.onNodeWithTag("epg-query").assertIsFocused()
        compose.onNodeWithTag("epg-program-${EpgProgramSelection.key(first)}").assertIsNotFocused()
    }

    @Test fun returningFromPlaybackRestoresTheChosenProgramRatherThanTheFirstRow() {
        schedule = PlayerEpgUiState("150", EpgSchedule(listOf(first, next)), nowMillis = 1_500L)
        board()
        compose.onNodeWithTag("epg-channel-150").performKeyInput { pressKey(Key.DirectionRight) }
        compose.waitForFocus("epg-program-${EpgProgramSelection.key(first)}")
        compose.onNodeWithTag("epg-program-${EpgProgramSelection.key(next)}")
            .performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithTag("epg-program-${EpgProgramSelection.key(next)}").performClick()
        compose.onNodeWithTag("epg-query").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.runOnIdle { returned = "150" }
        compose.waitForFocus("epg-program-${EpgProgramSelection.key(next)}")
    }
}