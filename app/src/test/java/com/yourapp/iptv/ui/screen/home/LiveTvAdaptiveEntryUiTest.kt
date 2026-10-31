package com.yourapp.iptv.ui.screen.home

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.ui.presentation.LiveChannelRows
import com.yourapp.iptv.ui.presentation.LiveChannelSort
import com.yourapp.iptv.ui.state.*
import com.yourapp.iptv.ui.theme.IPTVAppTheme
import kotlinx.coroutines.Dispatchers
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w960dp-h540dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
class LiveTvAdaptiveEntryUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val channels = (0 until 40).map { Channel("$it", "Synthetic $it HD", streamUrl = "https://synthetic.invalid/$it") }
    private val rows = LiveChannelRows.project(channels, LiveChannelSort.PROVIDER, Locale.ENGLISH)
    private val context = AdaptiveLiveNavigation.context(null, false, "")!!
    private fun learned(id: String) = rows.first { row -> row.variants.any { it.id == id } }.preferenceKey.let { family ->
        AdaptiveLiveNavigation().record(context, family).record(context, family)
    }
    private var state by mutableStateOf(HomeUiState(channels = channels, visibleChannels = channels, isLoading = false, browseScope = "scope"))
    private var preferences by mutableStateOf(LiveTvPreferencesState(ready = true))
    private val choices = mutableListOf<Pair<String, String>>()
    private var played: String? = null
    private fun board(ready: Boolean = true) {
        compose.setContent { IPTVAppTheme {
            val input = LocalInputModeManager.current
            SideEffect { input.requestInputMode(InputMode.Keyboard) }
            Surface(Modifier.fillMaxSize()) {
                LiveTvBrowser(state, HomeAppearance(language = HomeLanguage.ENGLISH), HomeLabels(HomeLanguage.ENGLISH),
                    HomeGuideState(), 1, CaptionUiState(), {}, {}, { played = it.id }, {}, {}, true, { _, _ -> }, {}, {},
                    projectionDispatcher = Dispatchers.Main.immediate, preferences = preferences,
                    onWatchChoice = { context, family -> choices += context to family })
            }
        } }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag(if (ready) "live-channel-list" else "live-channel-list-loading")
            .fetchSemanticsNodes().isNotEmpty() }
    }
    private fun focus(tag: String) {
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.RequestFocus) { it() }
    }
    private fun waitFocus(id: String) {
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("live-channel-$id").fetchSemanticsNodes()
            .any { it.config.getOrElse(SemanticsProperties.Focused) { false } } }
        compose.onNodeWithTag("live-channel-$id").assertIsFocused()
    }
    @Test fun aFreshEntryReachesTheLearnedRowOutsideTheViewportAndOnlyOkTrains() {
        preferences = preferences.copy(value = LiveTvPreferences(adaptive = learned("20")))
        board(); focus("live-list-entry"); waitFocus("20")
        compose.runOnIdle { assertTrue(choices.isEmpty()); assertNull(played) }
        compose.onNodeWithTag("live-channel-20").performKeyInput { pressKey(Key.DirectionDown) }; waitFocus("21")
        compose.runOnIdle { assertTrue(choices.isEmpty()) }
        compose.onNodeWithTag("live-channel-21").performKeyInput { pressKey(Key.Enter) }
        compose.runOnIdle { assertEquals("21", played); assertEquals(listOf(context to rows.first { it.key == "21" }.preferenceKey), choices) }
    }
    @Test fun updatedEvidenceDoesNotStealFocusAndReturningFromDetailsKeepsTheBrowsedRow() {
        board(); focus("live-list-entry"); waitFocus("0")
        compose.runOnIdle { preferences = preferences.copy(value = LiveTvPreferences(adaptive = learned("20"))) }
        waitFocus("0")
        compose.onNodeWithTag("live-channel-0").performKeyInput { pressKey(Key.DirectionRight) }
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        waitFocus("0"); compose.runOnIdle { assertTrue(choices.isEmpty()); assertNull(played) }
    }
    @Test fun anEntryQueuedBeforeProjectionLoadsResolvesTheLearnedVisibleRow() {
        preferences = preferences.copy(ready = false, value = LiveTvPreferences(adaptive = learned("20")))
        board(ready = false); focus("live-list-entry")
        compose.runOnIdle { preferences = preferences.copy(ready = true) }
        waitFocus("20"); compose.runOnIdle { assertTrue(choices.isEmpty()) }
    }
    @Test fun anExplicitSessionRestoreTakesPriorityOverLearnedEvidence() {
        state = state.copy(selectedChannelId = "3", gridRestore = HomeGridRestore(1, HomeGridPosition("0", 0, 1)))
        preferences = preferences.copy(value = LiveTvPreferences(adaptive = learned("20")))
        board(); focus("live-list-entry"); waitFocus("3")
        compose.runOnIdle { assertTrue(choices.isEmpty()); assertNull(played) }
    }
}