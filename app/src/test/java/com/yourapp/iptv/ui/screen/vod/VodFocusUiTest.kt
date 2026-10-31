package com.yourapp.iptv.ui.screen.vod

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.ui.theme.IPTVAppTheme
import com.yourapp.iptv.viewmodel.VodUiState
import com.yourapp.iptv.testing.waitForFocus
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w960dp-h540dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalComposeUiApi::class, ExperimentalTestApi::class)
class VodFocusUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val items = (0 until 60).map { VodItem("$it", "Film $it", if (it < 30) "a" else "b") }
    private var state by mutableStateOf(VodUiState(loading = false, sourceKey = "profile:source",
        catalog = VodCatalog(listOf(VodCategory("a", "A"), VodCategory("b", "B")), items)))
    private var delayCategory = false
    private var plays = 0
    private val actions = VodActions({}, {}, { state = state.copy(query = it) },
        { id -> state = state.copy(categoryId = id, categoryLoading = delayCategory) }, {},
        { item -> state = state.copy(selected = item, details = VodDetails(item)) },
        { state = state.copy(selected = null, details = null) }, {}, { _, _, _, _, _ -> plays++ })
    @Composable private fun Content(language: HomeLanguage) {
        IPTVAppTheme {
            val mode = LocalInputModeManager.current
            SideEffect { mode.requestInputMode(InputMode.Keyboard) }
            Surface(Modifier.fillMaxSize()) { VodContent(state, emptyList(), VodLabels(language), actions) }
        }
    }
    private fun board(language: HomeLanguage = HomeLanguage.GERMAN) { compose.setContent { Content(language) } }
    private fun focusPoster(id: String) {
        compose.onNodeWithTag("vod-grid").performScrollToNode(hasTestTag("vod-poster-$id"))
        compose.onNodeWithTag("vod-poster-$id").performSemanticsAction(SemanticsActions.RequestFocus)
    }
    @Test fun movingDownFromCategoriesMakesMoviePostersVisibleBelowTheScrollingHero() {
        board()
        compose.onNodeWithTag("vod-all").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithTag("vod-all").performKeyInput { pressKey(Key.DirectionDown) }
        compose.waitForFocus("vod-poster-0")
        compose.onNodeWithTag("vod-poster-0").assertIsDisplayed()
        focusPoster("48")
        compose.waitForFocus("vod-poster-48")
        compose.onNodeWithTag("vod-poster-48").assertIsDisplayed()
    }
    @Test fun seriesPreviewDoesNotRestrictPosterVerticalScrolling() {
        state = state.copy(kind = VodKind.SERIES)
        board()
        focusPoster("48")
        compose.waitForFocus("vod-poster-48")
        compose.onNodeWithTag("vod-poster-48").assertIsDisplayed()
        compose.onNodeWithTag("vod-series-preview").assertExists()
    }
    @Test fun closingDetailsReturnsToTheOriginalOffscreenPoster() {
        board(); focusPoster("24")
        compose.onNodeWithTag("vod-poster-24").performKeyInput { pressKey(Key.Enter) }
        compose.waitForFocus("vod-details-play")
        compose.onNodeWithTag("vod-details-close").performClick()
        compose.waitForFocus("vod-poster-24")
        compose.runOnIdle { assertEquals(0, plays) }
    }
    @Test fun arabicDetailsReturnAndNormalArrowsKeepWorking() {
        board(HomeLanguage.ARABIC); focusPoster("1")
        compose.onNodeWithTag("vod-poster-1").performKeyInput { pressKey(Key.Enter) }
        compose.onNodeWithTag("vod-details-close").performClick()
        compose.waitForFocus("vod-poster-1")
        compose.onNodeWithTag("vod-poster-1").performKeyInput { pressKey(Key.DirectionLeft) }
        compose.waitForFocus("vod-poster-2")
    }
    @Test fun categorySelectionRecallsItsOwnLastPoster() {
        state = state.copy(categoryId = "a")
        board(); focusPoster("20")
        compose.onNodeWithTag("vod-category:b").performClick()
        compose.waitForFocus("vod-poster-30")
        focusPoster("50")
        compose.onNodeWithTag("vod-category:a").performClick()
        compose.waitForFocus("vod-poster-20")
    }
    @Test fun aLateCategoryResponseDoesNotStealFocusFromTheQuery() {
        state = state.copy(categoryId = "a"); delayCategory = true
        board(); compose.onNodeWithTag("vod-category:b").performClick()
        compose.onNodeWithTag("vod-query").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithTag("vod-query").performKeyInput { pressKey(Key.DirectionLeft) }
        compose.runOnIdle { state = state.copy(categoryLoading = false) }
        compose.waitForFocus("vod-query")
    }
    @Test fun refreshRestoresTheSamePosterAndRemovalChoosesItsNearestSlot() {
        board(); focusPoster("24")
        compose.runOnIdle { state = state.copy(loading = true, sourceKey = null) }
        compose.runOnIdle { state = state.copy(loading = false, sourceKey = "profile:source",
            catalog = state.catalog.copy(items = items.filter { it.id != "24" })) }
        compose.waitForFocus("vod-poster-25")
    }
    @Test fun savedStateRestoresAnOffscreenPoster() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { Content(HomeLanguage.GERMAN) }
        focusPoster("40"); restoration.emulateSavedInstanceStateRestore()
        compose.waitForFocus("vod-poster-40")
    }
    @Test fun changingSourceDoesNotRestoreTheOtherSourcesPoster() {
        board(); focusPoster("3")
        compose.runOnIdle { state = state.copy(sourceKey = "other-profile:other-source") }
        compose.waitForFocus("vod-back")
    }
    @Test fun emptyCategoryLeavesFocusOnAnActionableControl() {
        state = state.copy(catalog = state.catalog.copy(categories = state.catalog.categories + VodCategory("empty", "Empty")))
        board(); compose.onNodeWithTag("vod-category:empty").performClick()
        compose.waitForFocus("vod-query")
    }
    @Test fun arrivingEpisodesDoNotPullFocusAwayFromTheDialogCloseButton() {
        val chosen = VodItem("series", "Series", "a")
        state = state.copy(kind = VodKind.SERIES, selected = chosen, detailLoading = true)
        board(); compose.waitForFocus("vod-details-close")
        compose.runOnIdle { state = state.copy(detailLoading = false, details = VodDetails(chosen,
            listOf(VodSeason(1, "Season", listOf(VodEpisode("episode", "Episode", 1, 1, "mp4")))))) }
        compose.waitForFocus("vod-details-close")
        compose.onNodeWithTag("vod-episode-episode").assertExists()
    }
}