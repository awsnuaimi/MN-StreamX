package com.yourapp.iptv.ui.screen.home

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.*
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.ui.presentation.ChannelProgramInfo
import com.yourapp.iptv.ui.state.HomeGuideState
import com.yourapp.iptv.ui.state.HomeProgramUiState
import com.yourapp.iptv.ui.theme.IPTVAppTheme
import com.yourapp.iptv.ui.tv.TvChannelGrid
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w960dp-h540dp-mdpi")
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
class LiveChannelGuideTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val channels = (0 until 7490).map {
        Channel("$it", "Synthetic Sender $it · أخبار الرياضة", streamUrl = "https://synthetic.invalid/$it")
    }
    private val program = ChannelProgramInfo(Program("Actual current", 0, 100_000), Program("Actual next", 100_000, 200_000), 0.5f)

    @Test fun onlyTheVisibleGridWindowIsBoundAndLeavingClearsIt() {
        val visible = mutableListOf<List<Channel>>();var shown by mutableStateOf(true)
        compose.setContent { GuideTheme {
            if (shown) Box(Modifier.width(840.dp).height(380.dp)) {
                TvChannelGrid(channels, null, {}, Modifier.fillMaxSize(), columns = 5, showLogos = false,
                    showGuide = true, program = { program }, onVisibleChannels = { visible += it })
            }
        } }
        compose.runOnIdle { assertTrue(visible.last().isNotEmpty());assertTrue(visible.last().size <= 20) }
        compose.onNodeWithTag("channel-browser").performScrollToIndex(100)
        compose.runOnIdle { assertTrue(visible.last().all { it.id.toInt() >= 100 });assertTrue(visible.last().any { it.id == "100" }) }
        compose.onNodeWithTag("channel-guide-now-100", true).assertTextContains("Actual current", substring = true)
        compose.onNodeWithTag("channel-guide-next-100", true).assertTextContains("Actual next", substring = true)
        compose.onNodeWithTag("channel-guide-progress-100", true).assertExists()
        compose.runOnIdle { shown = false }
        compose.runOnIdle { assertTrue(visible.last().isEmpty()) }
    }

    @Test fun listAndLargeArabicFontsKeepTheGuideInsideItsCardAndRetainFocus() {
        var layout by mutableStateOf(HomeChannelLayout.GRID);var scale by mutableFloatStateOf(1f)
        compose.setContent { GuideTheme {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale), LocalLayoutDirection provides LayoutDirection.Rtl) {
                Box(Modifier.width(840.dp).height(440.dp)) {
                    TvChannelGrid(channels, "37", {}, Modifier.fillMaxSize(), columns = 5, showLogos = false,
                        layout = layout, showGuide = true, program = { program }, language = HomeLanguage.ARABIC)
                }
            }
        } }
        for (mode in HomeChannelLayout.entries) for (font in listOf(1f, 1.5f, 2f)) {
            compose.runOnIdle { layout = mode;scale = font }
            compose.onNodeWithTag("browse-channel-37").assertIsFocused().assertIsDisplayed()
            val card = compose.onNodeWithTag("browse-channel-37", true).fetchSemanticsNode().boundsInRoot
            val guide = compose.onNodeWithTag("channel-guide-37", true).fetchSemanticsNode().boundsInRoot
            assertTrue("Guide clipped at $mode/$font: $guide / $card", guide.bottom <= card.bottom + 0.1f)
            compose.onNodeWithTag("channel-guide-now-37", true).assertTextContains("الآن", substring = true)
        }
    }

    @Test fun missingOrForeignGuidesNeverPreventPlaybackOrShowOldTitles() {
        var guide by mutableStateOf(HomeGuideState("old-owner", 1, listOf(HomeProgramUiState(channels[0].id,
            channels[0].streamUrl, EpgSchedule(listOf(Program("Protected old title", 0, 100))), 50))))
        var clicked: String? = null
        compose.setContent { GuideTheme {
            Box(Modifier.width(680.dp).height(380.dp)) {
                TvChannelGrid(channels.take(5), null, { clicked = it.id }, Modifier.fillMaxSize(), showLogos = false,
                    showGuide = true, language = HomeLanguage.GERMAN,
                    program = { ChannelProgramInfo.from(it, guide.forChannel(it, "new-owner", 2)) })
            }
        } }
        compose.onNodeWithText("Protected old title", substring = true).assertDoesNotExist()
        compose.onNodeWithTag("channel-guide-progress-0", true).assertDoesNotExist()
        compose.onNodeWithTag("browse-channel-0").performClick()
        compose.runOnIdle { assertEquals("0", clicked);guide = HomeGuideState() }
        compose.onNodeWithTag("browse-channel-0").assertHasClickAction()
    }

    @Test fun programUpdatesAndDpadNavigationPreserveTheSelectedChannel() {
        var current by mutableStateOf(program)
        compose.setContent { GuideTheme {
            Box(Modifier.width(680.dp).height(380.dp)) {
                TvChannelGrid(channels, "37", {}, Modifier.fillMaxSize(), columns = 4, showLogos = false,
                    showGuide = true, program = { current })
            }
        } }
        compose.onNodeWithTag("browse-channel-37").assertIsFocused()
        compose.runOnIdle { current = program.copy(current = Program("Updated", 0, 100_000), progress = 0.8f) }
        compose.onNodeWithTag("browse-channel-37").assertIsFocused()
        compose.onNodeWithTag("channel-guide-now-37", true).assertTextContains("Updated", substring = true)
        compose.onNodeWithTag("browse-channel-37").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("browse-channel-41").assertIsFocused().assertIsDisplayed()
    }
    @Composable private fun GuideTheme(content: @Composable () -> Unit) {
        val input = LocalInputModeManager.current
        SideEffect { input.requestInputMode(InputMode.Keyboard) }
        IPTVAppTheme { Surface { content() } }
    }
}