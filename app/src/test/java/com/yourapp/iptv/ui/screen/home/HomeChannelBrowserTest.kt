package com.yourapp.iptv.ui.screen.home

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import com.yourapp.iptv.ui.theme.IPTVAppTheme
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.ui.component.channel.ChannelLogo
import com.yourapp.iptv.ui.tv.TvChannelGrid
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w960dp-h540dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalComposeUiApi::class, ExperimentalTestApi::class)
class HomeChannelBrowserTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val labels = HomeLabels(HomeLanguage.GERMAN)
    private val channels = (0 until 7490).map { index ->
        Channel("id-$index", "Sender $index · أخبار", logoUrl = null,
            streamUrl = "https://synthetic.invalid/$index", group = "Nachrichten", isFavorite = index % 3 == 0)
    }
    private fun focus(tag: String) {
        compose.onNodeWithTag(tag, true).performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithTag(tag, true).assertIsFocused()
        compose.waitForIdle()
    }

    @Test fun searchIsCollapsedUntilRequestedAndBackClearsItAndRestoresButtonFocus() {
        var query by mutableStateOf("")
        compose.setContent {
            BrowserTheme {
                val input = LocalInputModeManager.current
                SideEffect { input.requestInputMode(InputMode.Keyboard) }
                HomeChannelToolbar("Live-TV", 7490, 7490, labels, query, { query = it }, HomeChannelLayout.GRID, {})
            }
        }
        compose.onNodeWithTag("browse-search-field").assertDoesNotExist()
        compose.onNodeWithContentDescription("Sender suchen").performClick()
        compose.onNodeWithTag("browse-search-field").assertIsFocused().performTextInput("Sender 42")
        compose.runOnIdle { assertEquals("Sender 42", query) }
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("browse-search-field").assertDoesNotExist()
        compose.onNodeWithTag("browse-search-toggle").assertIsFocused()
        compose.runOnIdle { assertEquals("", query) }
    }

    @Test fun restoredQueriesRemainVisibleAndTheToggleRemovesTheirFilter() {
        var query by mutableStateOf("أخبار")
        compose.setContent {
            BrowserTheme { HomeChannelToolbar("المفضلة", 42, 7490, HomeLabels(HomeLanguage.ARABIC), query,
                { query = it }, HomeChannelLayout.GRID, {}) }
        }
        compose.onNodeWithTag("browse-search-field").assertExists().assertTextContains("أخبار")
        compose.onNodeWithTag("browse-search-toggle").performClick()
        compose.onNodeWithTag("browse-search-field").assertDoesNotExist()
        compose.runOnIdle { assertEquals("", query) }
    }

    @Test fun gridListSwitchPreservesBrowsingPositionAndNeverStealsToolbarFocus() {
        var layout by mutableStateOf(HomeChannelLayout.GRID)
        var restore by mutableStateOf<HomeGridRestore?>(HomeGridRestore(1, HomeGridPosition("id-32", 12, 4)))
        val positions = mutableListOf<Pair<String, HomeGridPosition>>()
        compose.setContent {
            BrowserTheme {
                val input = LocalInputModeManager.current
                SideEffect { input.requestInputMode(InputMode.Keyboard) }
                Column(Modifier.width(680.dp).height(440.dp).testTag("browser-preview")) {
                    HomeChannelToolbar("Live-TV", channels.size, channels.size, labels, "", {}, layout, { layout = it })
                    TvChannelGrid(channels, "id-37", {}, Modifier.weight(1f).fillMaxWidth(), columns = 5,
                        restore = restore, onRestoreHandled = { restore = null },
                        onBrowsePosition = { id, position -> positions += id to position }, layout = layout, showLogos = false)
                }
            }
        }
        compose.onNodeWithTag("browse-channel-id-37").assertIsFocused()
        focus("browse-channel-id-38")
        focus("browse-layout-toggle")
        compose.onNodeWithTag("browse-layout-toggle").performClick()
        compose.onNodeWithTag("browse-layout-toggle").assertIsFocused()
        compose.onNodeWithTag("browse-channel-id-38").assertIsDisplayed()
        focus("browse-channel-id-38")
        compose.onNodeWithTag("browse-channel-id-38").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("browse-channel-id-39").assertIsFocused()
        compose.runOnIdle { assertEquals(1, positions.last().second.columns) }
        focus("browse-layout-toggle")
        compose.onNodeWithTag("browse-layout-toggle").performClick()
        compose.onNodeWithTag("browse-layout-toggle").assertIsFocused()
        compose.onNodeWithTag("browse-channel-id-39").assertIsDisplayed()
        focus("browse-channel-id-39")
        compose.runOnIdle { assertEquals("id-39", positions.last().first); assertEquals(4, positions.last().second.columns) }
        export("browser-preview", "compact-grid.png")
    }

    @Test fun listRestoresTheSelectedChannelFromAFiveColumnSessionInRtl() {
        val positions = mutableListOf<Pair<String, HomeGridPosition>>()
        compose.setContent {
            BrowserTheme {
                val input = LocalInputModeManager.current
                SideEffect { input.requestInputMode(InputMode.Keyboard) }
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Box(Modifier.width(680.dp).height(380.dp)) {
                        TvChannelGrid(channels, "id-7377", {}, Modifier.fillMaxSize(), columns = 5,
                            restore = HomeGridRestore(2, HomeGridPosition("id-7375", 80, 5)),
                            onBrowsePosition = { id, position -> positions += id to position },
                            layout = HomeChannelLayout.LIST, showLogos = false)
                    }
                }
            }
        }
        compose.onNodeWithTag("browse-channel-id-7377").assertIsFocused().assertIsDisplayed()
        compose.onNodeWithTag("browse-channel-id-7377").performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithTag("browse-channel-id-7376").assertIsFocused()
        compose.runOnIdle { assertEquals("id-7376", positions.last().first); assertEquals(1, positions.last().second.columns) }
    }

    @Test fun aLargeFontReducesColumnsAndKeepsTheSameFocusedChannelVisible() {
        var scale by mutableFloatStateOf(1f)
        var direction by mutableStateOf(LayoutDirection.Ltr)
        compose.setContent {
            BrowserTheme {
                val input = LocalInputModeManager.current
                SideEffect { input.requestInputMode(InputMode.Keyboard) }
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, scale),
                    LocalLayoutDirection provides direction) {
                    Box(Modifier.width(840.dp).height(440.dp).testTag("large-font-preview")) {
                        TvChannelGrid(channels, "id-37", {}, Modifier.fillMaxSize(), columns = 5, showLogos = false)
                    }
                }
            }
        }
        compose.onNodeWithTag("browse-channel-id-37").assertIsFocused()
        for (value in listOf(1.5f, 2f)) {
            compose.runOnIdle { scale = value; direction = LayoutDirection.Rtl }
            compose.onNodeWithTag("browse-channel-id-37").assertIsFocused().assertIsDisplayed()
            val card = compose.onNodeWithTag("browse-channel-id-37", true).fetchSemanticsNode().boundsInRoot
            assertTrue(card.width >= 144f * value - 0.1f)
        }
        export("large-font-preview", "compact-grid-rtl-large-font.png")
    }

    @Test fun hiddenLogoDoesNotCreateAnImageNodeAndBlankNamesHaveAFallback() {
        compose.setContent {
            BrowserTheme { ChannelLogo("   ", "https://synthetic.invalid/logo.png", Modifier.size(44.dp), showImage = false) }
        }
        compose.onNodeWithTag("channel-logo-image", true).assertDoesNotExist()
        compose.onNodeWithText("?", true).assertExists()
    }

    @Test fun disablingLogosAlsoRemovesBothDashboardLogoLayers() {
        val channel = channels[0].copy(logoUrl = "https://synthetic.invalid/logo.png")
        compose.setContent {
            BrowserTheme {
                HomeCinematicChannelCard(channel, HomeDashboardLayout.forWidth(720), HomeTvPalettes.forMode(true),
                    labels, showChannelLogos = false) { }
            }
        }
        compose.onNodeWithTag("channel-card-logo", true).assertDoesNotExist()
        compose.onNodeWithTag("channel-logo-image", true).assertDoesNotExist()
        compose.onNodeWithTag("home-channel-${channel.id}").assertHasClickAction()
    }

    @Test fun pendingLayoutSaveRetainsFocusAndDoesNotAcceptAnotherClick() {
        var saving by mutableStateOf(true)
        var calls = 0
        compose.setContent {
            BrowserTheme {
                val input = LocalInputModeManager.current
                SideEffect { input.requestInputMode(InputMode.Keyboard) }
                HomeChannelToolbar("Live-TV", 10, 10, labels, "", {}, HomeChannelLayout.GRID, { calls++ }, saving = saving)
            }
        }
        focus("browse-layout-toggle")
        compose.onNodeWithTag("browse-layout-toggle").performClick().assertIsFocused()
        compose.runOnIdle { assertEquals(0, calls); saving = false }
        compose.onNodeWithTag("browse-layout-toggle").performClick().assertIsFocused()
        compose.runOnIdle { assertEquals(1, calls) }
    }

    @Test fun leavingForPlaybackAndRecreatingTheBrowserRestoresThePlayedChannelInBothModes() {
        var layout by mutableStateOf(HomeChannelLayout.GRID)
        var showBrowser by mutableStateOf(true)
        var selected by mutableStateOf("id-37")
        var position = HomeGridPosition("id-32", 12, 4)
        var token = 1L
        compose.setContent {
            BrowserTheme {
                val input = LocalInputModeManager.current
                SideEffect { input.requestInputMode(InputMode.Keyboard) }
                if (showBrowser) Box(Modifier.width(680.dp).height(380.dp)) {
                    TvChannelGrid(channels, selected, { selected = it.id; showBrowser = false },
                        Modifier.fillMaxSize(), columns = 5, restore = HomeGridRestore(token, position),
                        onBrowsePosition = { _, value -> position = value }, layout = layout, showLogos = false)
                } else androidx.compose.material3.Button(onClick = { token++; showBrowser = true },
                    modifier = Modifier.testTag("return-from-player")) { androidx.compose.material3.Text("Back") }
            }
        }
        for (mode in HomeChannelLayout.entries) {
            compose.runOnIdle { layout = mode }
            focus("browse-channel-id-38")
            compose.onNodeWithTag("browse-channel-id-38").performClick()
            compose.onNodeWithTag("channel-browser").assertDoesNotExist()
            compose.onNodeWithTag("return-from-player").performClick()
            compose.onNodeWithTag("browse-channel-id-38").assertIsFocused().assertIsDisplayed()
        }
    }

    @Test fun hidingHeroLogosKeepsTheWatchActionAvailable() {
        val channel = channels[0].copy(logoUrl = "https://synthetic.invalid/logo.png")
        compose.setContent {
            BrowserTheme {
                Box(Modifier.width(720.dp)) {
                    DashboardWelcome(labels, channel, channels.size, HomeDashboardLayout.forWidth(720),
                        HomeTvPalettes.forMode(true), showChannelLogos = false) { }
                }
            }
        }
        compose.onNodeWithTag("hero-continue", true).assertExists().assertHasClickAction()
        compose.onNodeWithTag("hero-channel-name", true).assertTextEquals(channel.name)
    }

    @Test fun anExistingQueryNeverRequestsFocusJustBecauseItsToolbarIsRecreated() {
        var showToolbar by mutableStateOf(false)
        compose.setContent {
            BrowserTheme {
                val input = LocalInputModeManager.current
                SideEffect { input.requestInputMode(InputMode.Keyboard) }
                Column(Modifier.width(680.dp).height(440.dp)) {
                    if (showToolbar) HomeChannelToolbar("Live-TV", 100, 7490, labels, "Sender", {},
                        HomeChannelLayout.GRID, {})
                    TvChannelGrid(channels.take(100), "id-37", {}, Modifier.weight(1f).fillMaxWidth(), showLogos = false)
                }
            }
        }
        compose.onNodeWithTag("browse-channel-id-37").assertIsFocused()
        compose.runOnIdle { showToolbar = true }
        compose.onNodeWithTag("browse-search-field").assertExists()
        compose.onNodeWithTag("browse-channel-id-37").assertIsFocused().assertIsDisplayed()
    }

    @Composable
    private fun BrowserTheme(content: @Composable () -> Unit) {
        IPTVAppTheme { Surface(Modifier.fillMaxSize()) { Box(Modifier.fillMaxSize()) { content() } } }
    }

    private fun export(tag: String, name: String) {
        val dir = System.getProperty("mnstreamx.uiPreviewDir")?.let(::File) ?: return
        dir.mkdirs()
        fun owner(view: View): View? {
            if (view.javaClass.name == "androidx.compose.ui.platform.AndroidComposeView") return view
            if (view is ViewGroup) for (index in 0 until view.childCount) owner(view.getChildAt(index))?.let { return it }
            return null
        }
        val bounds = compose.onNodeWithTag(tag, true).fetchSemanticsNode().boundsInRoot
        compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(bounds.width.toInt(), bounds.height.toInt(), Bitmap.Config.ARGB_8888)
            try {
                val canvas = Canvas(bitmap); canvas.translate(-bounds.left, -bounds.top)
                checkNotNull(owner(compose.activity.window.decorView)).draw(canvas)
                dir.resolve(name).outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            } finally { bitmap.recycle() }
        }
    }
}