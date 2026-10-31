package com.yourapp.iptv.ui.screen.vod

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.ui.component.common.LocalizedResources
import com.yourapp.iptv.ui.theme.IPTVAppTheme
import com.yourapp.iptv.viewmodel.VodPlaybackState
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34],application = Application::class,qualifiers = "w960dp-h540dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VodEpisodePanelTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val episodes = listOf(
        VodEpisode("one","First episode",1,1,"mp4"),
        VodEpisode("current","Playing now",2,3,"mp4"),
        VodEpisode("four","Next episode",2,4,"mp4")
    )
    private val item = VodPlaybackState.Ready("https://synthetic.invalid/play.mp4","Series",
        "source-1",VodKind.SERIES,"current","mp4",0,"series-1",episodes = episodes)

    @Test fun currentSeasonOpensFirstAndTheOtherSeasonCanBeSelected() {
        var selected: VodEpisode? = null
        compose.setContent { IPTVAppTheme { LocalizedResources(HomeLanguage.GERMAN) {
            VodEpisodePanel(item,VodPlaybackLabels(HomeLanguage.GERMAN),{ selected = it },{})
        } } }
        compose.onNodeWithTag("vod-episode-panel").assertExists()
        compose.onNodeWithTag("vod-season-1").assertExists()
        compose.onNodeWithTag("vod-season-2").assertExists()
        compose.onNodeWithTag("vod-episode-current").assertExists()
        compose.onNodeWithTag("vod-season-1").performClick()
        compose.onNodeWithTag("vod-episode-one").assertExists().performClick()
        compose.runOnIdle { assertEquals("one",selected?.id) }
    }

    @Test fun savedEpisodeShowsRealProgressWithoutChangingSeasonNavigation() {
        val numbered = item.copy(id = "22",seriesId = "800",episodes = listOf(
            VodEpisode("21","First",1,1,"mp4"),VodEpisode("22","Second",1,2,"mp4")))
        val rows = listOf(VodProgressEntry(VodKind.SERIES,"22","Second","mp4",60_000L,
            120_000L,1_000L,"800"))
        compose.setContent { IPTVAppTheme { LocalizedResources(HomeLanguage.GERMAN) {
            VodEpisodePanel(numbered,VodPlaybackLabels(HomeLanguage.GERMAN),{}, {},progressRows = rows)
        } } }
        compose.onNodeWithTag("vod-episode-progress-22",useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("vod-episode-21").assertExists()
    }

    @Test fun choosingTheCurrentlyPlayingEpisodeDismissesRatherThanRestartingIt() {
        var dismissed = 0
        var selections = 0
        compose.setContent { IPTVAppTheme { LocalizedResources(HomeLanguage.GERMAN) {
            VodEpisodePanel(item,VodPlaybackLabels(HomeLanguage.GERMAN),{ selections++ },{ dismissed++ })
        } } }
        compose.onNodeWithTag("vod-episode-current").performClick()
        compose.runOnIdle { assertEquals(0,selections);assertEquals(1,dismissed) }
    }
}