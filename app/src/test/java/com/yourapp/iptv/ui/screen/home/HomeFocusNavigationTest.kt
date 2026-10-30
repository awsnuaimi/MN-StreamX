package com.yourapp.iptv.ui.screen.home

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.ui.component.common.rememberedFocusGroup
import com.yourapp.iptv.ui.theme.IPTVAppTheme
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
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
class HomeFocusNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val channels = (0 until 6).map {
        Channel("$it", "DE| Synthetic $it", streamUrl = "https://synthetic.invalid/$it")
    }
    private var plays = 0

    private fun board(language: HomeLanguage = HomeLanguage.GERMAN) {
        compose.setContent {
            IPTVAppTheme {
                val input = LocalInputModeManager.current
                SideEffect { input.requestInputMode(InputMode.Keyboard) }
                val appearance = HomeAppearance(language = language)
                val labels = HomeLabels(language)
                val rail = remember { HomeSection.entries.associateWith { FocusRequester() } }
                val entry = remember { FocusRequester() }
                val header = remember { FocusRequester() }
                val body = remember { FocusRequester() }
                val hero = remember { FocusRequester() }
                val sidebar = rail.getValue(HomeSection.DASHBOARD)
                CompositionLocalProvider(LocalLayoutDirection provides
                    if (appearance.isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                    Surface {
                        Row(Modifier.fillMaxSize()) {
                            HomeSidebar(appearance, labels, HomeSection.DASHBOARD, false, rail, {}, {}, dashboardFocus = entry)
                            Column(Modifier.weight(1f).fillMaxHeight().padding(20.dp).rememberedFocusGroup(entry, hero),
                                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                HomeDashboardTopBar(labels, "Synthetic", {}, {}, {},
                                    modifier = Modifier.rememberedFocusGroup(header) { direction ->
                                        homeRegionExit(direction, below = body, sidebar = sidebar,
                                            sidebarOnRight = appearance.sidebarOnRight, rememberBelow = true, fallbackBelow = hero)
                                    })
                                Box(Modifier.weight(1f)) {
                                    HomeDashboard(labels, HomeDashboardSelection(channels, channels.take(3), channels[0]),
                                        channels.size, body, channels, true, false, { plays++ }, {}, {}, {}, {}, {},
                                        catalogChannels = channels, interfaceLanguage = language,
                                        headerFocus = header, sidebarFocus = sidebar, sidebarOnRight = appearance.sidebarOnRight,
                                        heroEntry = hero)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun focus(tag: String) {
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.onNodeWithTag(tag).assertIsFocused()
    }
    private fun press(tag: String, key: Key) {
        compose.onNodeWithTag(tag).performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }
    private fun focused(tag: String) = compose.onNodeWithTag(tag).assertIsFocused().assertIsDisplayed()

    @Test fun verticalNavigationRemembersTheColumnInsteadOfRestartingAtTheFirstCard() {
        board(); focus("hero-continue")
        press("hero-continue", Key.DirectionDown); focused("home-channel-0")
        press("home-channel-0", Key.DirectionRight); focused("home-channel-1")
        press("home-channel-1", Key.DirectionUp); focused("hero-continue")
        press("hero-continue", Key.DirectionDown); focused("home-channel-1")
        press("home-channel-1", Key.DirectionDown); focused("recent-channel-1")
        press("recent-channel-1", Key.DirectionUp); focused("home-channel-1")
        compose.runOnIdle { assertEquals(0, plays) }
    }

    @Test fun theSidebarReturnsToTheLastContentRegion() {
        board(); focus("hero-continue")
        press("hero-continue", Key.DirectionDown); focused("home-channel-0")
        press("home-channel-0", Key.DirectionDown); focused("recent-channel-0")
        press("recent-channel-0", Key.DirectionLeft); focused("rail-DASHBOARD")
        press("rail-DASHBOARD", Key.DirectionRight); focused("recent-channel-0")
        compose.runOnIdle { assertEquals(0, plays) }
    }

    @Test fun theHeaderReturnsToItsLastButtonAndThenToThePreviousCard() {
        board(); focus("home-search")
        press("home-search", Key.DirectionDown); focused("hero-continue")
        press("hero-continue", Key.DirectionDown); focused("home-channel-0")
        press("home-channel-0", Key.DirectionRight); focused("home-channel-1")
        press("home-channel-1", Key.DirectionUp); focused("hero-continue")
        press("hero-continue", Key.DirectionUp); focused("home-search")
        press("home-search", Key.DirectionDown); focused("hero-continue")
        press("hero-continue", Key.DirectionDown); focused("home-channel-1")
    }

    @Test fun arabicNavigationUsesTheActualPositionOfTheSidebarAndCards() {
        board(HomeLanguage.ARABIC); focus("rail-DASHBOARD")
        press("rail-DASHBOARD", Key.DirectionLeft); focused("hero-continue")
        press("hero-continue", Key.DirectionDown); focused("home-channel-0")
        press("home-channel-0", Key.DirectionLeft); focused("home-channel-1")
        press("home-channel-1", Key.DirectionRight); focused("home-channel-0")
        press("home-channel-0", Key.DirectionRight); focused("rail-DASHBOARD")
        compose.runOnIdle { assertEquals(0, plays) }
    }
}