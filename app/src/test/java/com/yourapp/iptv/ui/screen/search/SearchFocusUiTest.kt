package com.yourapp.iptv.ui.screen.search

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
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.ui.component.common.LocalizedResources
import com.yourapp.iptv.ui.state.*
import com.yourapp.iptv.ui.theme.IPTVAppTheme
import com.yourapp.iptv.testing.waitForFocus
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w960dp-h540dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalComposeUiApi::class, ExperimentalTestApi::class)
class SearchFocusUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val channels = (0 until 15).map { Channel("$it", "Channel $it", streamUrl = "https://synthetic.invalid/$it") }
    private var state by mutableStateOf(SearchUiState(query = "shared", sourceKey = "profile:source", sourceLoading = false,
        channels = channels, movies = SearchVodState(loaded = true), series = SearchVodState(loaded = true)))
    private var restore by mutableIntStateOf(0)
    private val actions = SearchActions({}, {}, {}, { state = state.copy(query = it) }, {}, {}, {}, {}, {}, {}, {}, {}, {}, { _, _ -> })
    @Composable private fun Content() {
        IPTVAppTheme {
            val mode = LocalInputModeManager.current
            SideEffect { mode.requestInputMode(InputMode.Keyboard) }
            LocalizedResources(HomeLanguage.GERMAN) {
                Surface(Modifier.fillMaxSize()) { SearchContent(state, SearchLabels(HomeLanguage.GERMAN), actions, restore) }
            }
        }
    }
    private fun board() { compose.setContent { Content() } }
    private fun focus(tag: String) {
        if (!tag.startsWith("search-")) compose.onNodeWithTag(tag).performScrollTo()
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.RequestFocus)
    }
    @Test fun downRecallsTheLastFocusedResultWithoutRequiringASelection() {
        board(); focus("channel-2"); focus("search-query")
        compose.onNodeWithTag("search-query").performKeyInput { pressKey(Key.DirectionDown) }
        compose.waitForFocus("channel-2")
    }
    @Test fun disappearingResultMovesToTheNextAtTheSameSlot() {
        board(); focus("channel-2")
        compose.runOnIdle { state = state.copy(channels = channels.filter { it.id != "2" }) }
        compose.waitForFocus("channel-3")
    }
    @Test fun lateSearchResultsDoNotStealFocusFromTheQuery() {
        state = state.copy(channels = emptyList(), channelsSearching = true)
        board(); focus("search-query")
        compose.runOnIdle { state = state.copy(channels = channels, channelsSearching = false) }
        compose.waitForFocus("search-query")
    }
    @Test fun laterInputCancelsAnExplicitPendingDownRequest() {
        state = state.copy(channels = emptyList(), channelsSearching = true)
        board(); focus("search-query")
        compose.onNodeWithTag("search-query").performKeyInput { pressKey(Key.DirectionDown) }
        focus("search-back")
        compose.onNodeWithTag("search-back").performKeyInput { pressKey(Key.DirectionLeft) }
        compose.runOnIdle { state = state.copy(channels = channels, channelsSearching = false) }
        compose.waitForFocus("search-back")
    }
    @Test fun explicitDialogReturnRecallsTheCorrectResultAndMediaLane() {
        state = state.copy(movies = SearchVodState(loaded = true, results = listOf(VodItem("2", "Movie", null))))
        board(); focus("movie-2")
        compose.onNodeWithTag("movie-2").performClick()
        compose.runOnIdle { restore++ }
        compose.waitForFocus("movie-2")
    }
    @Test fun savedStateRestoresTheSelectedMediaLaneWithSharedIds() {
        state = state.copy(movies = SearchVodState(loaded = true, results = listOf(VodItem("2", "Movie", null))))
        val restoration = StateRestorationTester(compose)
        restoration.setContent { Content() }
        focus("movie-2"); restoration.emulateSavedInstanceStateRestore()
        compose.waitForFocus("movie-2")
    }
    @Test fun changingSourceDropsTheOtherSourcesResultMemory() {
        board(); focus("channel-2")
        compose.runOnIdle { state = state.copy(sourceKey = "another-profile:source") }
        compose.waitForFocus("search-query")
    }
}