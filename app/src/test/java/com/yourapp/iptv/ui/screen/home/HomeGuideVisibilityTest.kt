package com.yourapp.iptv.ui.screen.home

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.ui.state.CaptionUiState
import com.yourapp.iptv.ui.state.HomeGuideState
import com.yourapp.iptv.ui.state.HomeProgramUiState
import com.yourapp.iptv.ui.theme.IPTVAppTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w960dp-h540dp-mdpi")
class HomeGuideVisibilityTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val rows = (0 until 30).map { Channel("$it", "AR| Sender $it HD", streamUrl = "https://synthetic.invalid/$it") }
    private val captions = CaptionUiState(CaptionSettings(mode = CaptionMode.GENERATED), loaded = true)
    private fun content(shown: State<Boolean>, calls: MutableList<List<Channel>>) {
        compose.setContent {
            IPTVAppTheme { Surface {
                if (shown.value) Box(Modifier.width(834.dp).height(420.dp)) {
                    HomeDashboard(HomeLabels(HomeLanguage.GERMAN), HomeDashboardSelection(rows.takeLast(5), rows.take(12), rows[0]),
                        7490, remember { FocusRequester() }, rows.take(5), true, false, {}, {}, {}, {}, {}, {},
                        catalogChannels = rows, captionState = captions, interfaceLanguage = HomeLanguage.GERMAN,
                        showChannelLogos = false, onGuideChannels = { calls += it })
                }
            } }
        }
    }
    @Test fun onlyVisibleCardsAreRequestedAndHorizontalScrollingReplacesTheWindow() {
        val calls = mutableListOf<List<Channel>>();content(mutableStateOf(true), calls)
        compose.runOnIdle {
            val current = calls.last();assertTrue(current.isNotEmpty());assertTrue(current.size <= 8)
            assertTrue("Unexpected visible request: ${current.map { it.id }}", current.all { it.id.toInt() < 6 })
        }
        compose.onNodeWithTag("home-channel-row").performScrollToIndex(8)
        compose.runOnIdle { assertTrue(calls.last().all { it.id.toInt() >= 7 });assertTrue(calls.last().any { it.id == "8" }) }
    }
    @Test fun verticalScrollingAddsTheRecentRowAndDisposalClearsEveryRequest() {
        val shown = mutableStateOf(true);val calls = mutableListOf<List<Channel>>();content(shown, calls)
        compose.onNodeWithTag("recent-channel-row").performScrollTo()
        compose.runOnIdle { assertTrue(calls.last().size <= 8);assertTrue(calls.last().any { it.id.toInt() >= 25 }) }
        compose.runOnIdle { shown.value = false }
        compose.runOnIdle { assertTrue(calls.last().isEmpty()) }
    }
    @Test fun guideWindowsRejectForeignOwnersEpochsAndStreamsWithoutPrintingSecrets() {
        val row = rows[0]
        val entry = HomeProgramUiState(row.id, row.streamUrl, EpgSchedule(listOf(Program("Synthetic", 0, Long.MAX_VALUE))), 100)
        val state = HomeGuideState("synthetic-owner", 5, listOf(entry))
        assertTrue(state.forChannel(row, "synthetic-owner", 5).matches(row))
        assertFalse(state.forChannel(row, "other-owner", 5).matches(row))
        assertFalse(state.forChannel(row, "synthetic-owner", 6).matches(row))
        assertFalse(state.forChannel(row.copy(streamUrl = "https://other.invalid"), "synthetic-owner", 5).matches(row))
        assertFalse(state.toString().contains("https://"));assertFalse(state.toString().contains("Synthetic"))
    }
}