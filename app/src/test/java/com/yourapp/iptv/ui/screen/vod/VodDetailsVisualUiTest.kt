package com.yourapp.iptv.ui.screen.vod

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.ui.theme.IPTVAppTheme
import com.yourapp.iptv.viewmodel.VodUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w960dp-h540dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalComposeUiApi::class, ExperimentalTestApi::class)
class VodDetailsVisualUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val chosen = VodItem("800", "Example Series", "series", year = "2024", plot = "Example plot")
    private val seasons = listOf(VodSeason(1, "Season 1", listOf(
        VodEpisode("21", "Pilot", 1, 1, "mp4"), VodEpisode("22", "Second", 1, 2, "mp4"))))
    private var played: String? = null

    @Composable
    private fun Content(kind: VodKind) {
        IPTVAppTheme {
            val mode = LocalInputModeManager.current
            SideEffect { mode.requestInputMode(InputMode.Keyboard) }
            val state = VodUiState(kind = kind, loading = false, sourceKey = "p:source",
                selected = chosen, details = VodDetails(chosen, if (kind == VodKind.SERIES) seasons else emptyList()))
            Surface(Modifier.fillMaxSize()) {
                VodDetailsDialog(state, chosen, VodLabels(HomeLanguage.GERMAN), emptyList(), {}, {},
                    onPlay = { _, id, _, _, _ -> played = id })
            }
        }
    }

    @Test fun seriesDetailsShowPosterSeasonEpisodeAndRecommendationWithoutChangingPlayback() {
        compose.setContent { Content(VodKind.SERIES) }
        compose.onNodeWithTag("vod-details-dialog").assertExists()
        compose.onNodeWithTag("vod-details-artwork").assertExists()
        compose.onNodeWithTag("vod-details-title").assertExists()
        compose.onNodeWithTag("vod-series-recommend").assertExists()
        compose.onNodeWithTag("vod-episode-21").assertExists()
        compose.onNodeWithTag("vod-details-close").assertExists()
        compose.onNodeWithTag("vod-episode-21").performClick()
        compose.runOnIdle { assertEquals("21", played) }
    }

    @Test fun seasonChipsJumpToOtherSeasonsWithoutStartingPlayback() {
        val many = (1..14).map { VodEpisode("one-$it", "Episode $it", 1, it, "mp4") }
        val other = listOf(VodEpisode("two-1", "Next season", 2, 1, "mp4"))
        compose.setContent {
            IPTVAppTheme {
                val mode = LocalInputModeManager.current
                SideEffect { mode.requestInputMode(InputMode.Keyboard) }
                val details = VodDetails(chosen, listOf(
                    VodSeason(1, "Season 1", many), VodSeason(2, "Season 2", other)))
                VodDetailsDialog(VodUiState(kind = VodKind.SERIES, loading = false,
                    sourceKey = "p:source", selected = chosen, details = details),
                    chosen, VodLabels(HomeLanguage.GERMAN), emptyList(), {}, {},
                    onPlay = { _, id, _, _, _ -> played = id })
            }
        }
        compose.onNodeWithTag("vod-details-seasons").assertExists()
        compose.onNodeWithTag("vod-season-jump-2").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("vod-episode-two-1").assertIsDisplayed()
        compose.runOnIdle { assertEquals(null, played) }
        compose.onNodeWithTag("vod-episode-two-1").performClick()
        compose.runOnIdle { assertEquals("two-1", played) }
    }

    @Test fun selectedSeasonRemainsHighlightedAfterClosingAndReopeningDetails() {
        val memory = VodDetailsNavigationMemory()
        var show by mutableStateOf(true)
        val details = VodDetails(chosen, listOf(
            VodSeason(1, "First", listOf(VodEpisode("e1", "First episode", 1, 1, "mp4"))),
            VodSeason(2, "Second", listOf(VodEpisode("e2", "Second episode", 2, 1, "mp4")))))
        compose.setContent {
            IPTVAppTheme {
                if (show) VodDetailsDialog(
                    VodUiState(kind = VodKind.SERIES, selected = chosen, sourceKey = "profile-a:source",
                        details = details), chosen, VodLabels(HomeLanguage.GERMAN), emptyList(), {}, {},
                    navigationMemory = memory, onPlay = { _, _, _, _, _ -> })
            }
        }
        compose.onNodeWithTag("vod-season-jump-2").performClick()
        compose.onNodeWithTag("vod-season-jump-2").assertIsSelected()
        compose.runOnIdle { show = false }
        compose.runOnIdle { show = true }
        compose.onNodeWithTag("vod-season-jump-2").assertIsSelected()
    }

    @Test fun movieDetailsKeepThePlayControlAndCloseControl() {
        compose.setContent { Content(VodKind.MOVIE) }
        compose.onNodeWithTag("vod-details-artwork").assertExists()
        compose.onNodeWithTag("vod-details-play").assertExists()
        compose.onNodeWithTag("vod-details-close").assertExists()
        compose.onNodeWithTag("vod-details-play").performClick()
        compose.runOnIdle { assertEquals("800", played) }
    }

    @Test
    @Config(qualifiers = "w600dp-h540dp-mdpi")
    fun compactLayoutStillExposesEpisodeAndCloseActions() {
        compose.setContent { Content(VodKind.SERIES) }
        compose.onNodeWithTag("vod-details-artwork").assertExists()
        compose.onNodeWithTag("vod-series-recommend").assertExists()
        compose.onNodeWithTag("vod-episode-21").assertExists()
        compose.onNodeWithTag("vod-details-close").assertExists()
    }
}