package com.yourapp.iptv.ui.screen.home

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.*
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.ui.state.*
import com.yourapp.iptv.ui.theme.IPTVAppTheme
import kotlinx.coroutines.Dispatchers
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w960dp-h540dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
class LiveTvPreferencesUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val channels = listOf(Channel("fhd", "PL| News FHD", streamUrl = "fhd", group = "News"),
        Channel("hd", "PL| News HD", streamUrl = "hd", group = "News"),
        Channel("sport", "Sport HD", streamUrl = "sport", group = "Sports"))
    private val personal = mutableStateOf(LiveTvPreferencesState("a".repeat(64), ready = true))
    private val catalog = mutableStateOf(HomeUiState(channels = channels, visibleChannels = channels,
        groups = listOf("News", "Sports"), browseScope = "a".repeat(64), isLoading = false))
    private var played: String? = null
    private fun board(arabic: Boolean = false, scale: Float = 1f) {
        compose.setContent {
            IPTVAppTheme {
                val input = LocalInputModeManager.current
                SideEffect { input.requestInputMode(InputMode.Keyboard) }
                val density = LocalDensity.current
                val language = if (arabic) HomeLanguage.ARABIC else HomeLanguage.GERMAN
                CompositionLocalProvider(LocalDensity provides Density(density.density, scale),
                    LocalLayoutDirection provides if (arabic) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                    Surface(Modifier.fillMaxSize()) {
                        LiveTvBrowser(catalog.value, HomeAppearance(language = language), HomeLabels(language),
                            HomeGuideState(), 0, CaptionUiState(), {}, {}, { played = it.id }, {}, {}, true,
                            { _, _ -> }, {}, {}, projectionDispatcher = Dispatchers.Main.immediate,
                            preferences = personal.value, onPreference = { transform ->
                                personal.value = personal.value.copy(value = transform(personal.value.value))
                            })
                    }
                }
            }
        }
        ready()
    }
    private fun ready() {
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("live-channel-list").fetchSemanticsNodes().size == 1 }
        compose.waitForIdle()
    }
    private fun preview(name: String) {
        val directory = System.getProperty("mnx.preview.dir") ?: return
        compose.runOnIdle {
            val view = compose.activity.findViewById<android.view.View>(android.R.id.content)
            val image = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(image))
            val output = java.io.File(directory, name); output.parentFile?.mkdirs()
            output.outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }; image.recycle()
        }
    }
    private fun menu() { compose.onNodeWithTag("live-channel-fhd").performTouchInput { longClick() } }
    private fun action(tag: String) {
        compose.onNodeWithTag("live-menu-variants").performScrollToNode(hasTestTag(tag))
        compose.onNodeWithTag(tag).performClick(); ready()
    }
    @Test fun allHiddenStillOffersManagementAndShowAllRestoresTheList() {
        personal.value = personal.value.copy(value = LiveTvPreferences(hiddenChannels = channels.map { it.id }.toSet()))
        board(); compose.onNodeWithTag("live-channel-fhd").assertDoesNotExist()
        compose.onNodeWithTag("live-hidden").performClick()
        compose.onNodeWithTag("live-hidden-restore-all").performClick()
        compose.onNodeWithTag("live-hidden-close").performClick(); ready()
        compose.onNodeWithTag("live-channel-fhd").assertIsDisplayed(); assertNull(played)
    }
    @Test fun selectingDefaultQualityDoesNotPlayButTheNextOkUsesThatVariant() {
        board(); menu(); action("live-prefer-hd")
        compose.runOnIdle { assertEquals(setOf("hd"), personal.value.value.preferredQualities.values.toSet()); assertNull(played) }
        compose.onNodeWithTag("live-channel-fhd").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.onNodeWithTag("live-channel-fhd").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals("hd", played) }
    }
    @Test fun hidingAMiddleRowRetainsTheNearestSurvivingRowAsTheRemoteTarget() {
        val many = (0 until 40).map { Channel("row$it", "Station $it HD", streamUrl = "stream$it", group = "Many") }
        catalog.value = catalog.value.copy(channels = many, visibleChannels = many, groups = listOf("Many"))
        board()
        compose.onNodeWithTag("live-channel-list").performScrollToNode(hasTestTag("live-channel-row20"))
        compose.onNodeWithTag("live-channel-row20").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.onNodeWithTag("live-channel-row20").performTouchInput { longClick() }
        action("live-menu-hide")
        compose.onNodeWithTag("live-channel-row21").assertIsFocused()
        assertNull(played)
    }
    @Test fun hidingGroupedChannelHidesAllQualitiesAndLeavesTheNextRowFocusable() {
        board(); menu(); action("live-menu-hide")
        compose.onNodeWithTag("live-channel-fhd").assertDoesNotExist()
        compose.onNodeWithTag("live-channel-sport").assertIsDisplayed()
        compose.runOnIdle { assertEquals(setOf("fhd", "hd"), personal.value.value.hiddenChannels); assertNull(played) }
        compose.onNodeWithTag("live-channel-sport").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals("sport", played) }
    }
    @Test fun hiddenRawCategoryDisappearsAndCanBeRestoredIndividually() {
        board(); menu(); action("live-menu-hide-category")
        compose.onNodeWithTag("live-category-News").assertDoesNotExist()
        compose.onNodeWithTag("live-hidden").performClick()
        compose.onNodeWithTag("live-hidden-list").performScrollToNode(hasTestTag("live-hidden-group-News"))
        compose.onNodeWithTag("live-hidden-group-News").performClick()
        compose.onNodeWithTag("live-hidden-close").assertIsFocused()
        compose.onNodeWithTag("live-hidden-close").performClick(); ready()
        compose.onNodeWithTag("live-category-News").assertIsDisplayed()
    }
    @Test fun sortingWritesPersonalPreferenceAndSurvivesRecomposition() {
        board(); preview("live-customize-de.png"); compose.onNodeWithTag("live-sort").performClick()
        compose.onNodeWithTag("live-sort-NAME").performClick(); ready()
        compose.runOnIdle { assertEquals(LiveTvSort.NAME, personal.value.value.sort) }
        compose.onNodeWithTag("live-hidden").performClick(); compose.onNodeWithTag("live-hidden-close").performClick()
        compose.runOnIdle { assertEquals(LiveTvSort.NAME, personal.value.value.sort); assertNull(played) }
    }
    @Test fun arabicLargeTextKeepsRestoreAndCloseReachableWithTheRemote() {
        personal.value = personal.value.copy(value = LiveTvPreferences(hiddenGroups = setOf("News")))
        board(arabic = true, scale = 2f); preview("live-customize-ar-large.png")
        compose.onNodeWithTag("live-hidden").assertIsDisplayed().performClick()
        compose.onNodeWithTag("live-hidden-close").assertIsFocused()
        compose.onNodeWithTag("live-hidden-list").performScrollToNode(hasTestTag("live-hidden-restore-all"))
        compose.onNodeWithTag("live-hidden-restore-all").assertIsDisplayed().performClick()
        compose.onNodeWithTag("live-hidden-close").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        ready(); compose.onNodeWithTag("live-hidden-close").assertDoesNotExist(); assertNull(played)
    }
    @Test fun switchingScopesClosesManagementAndNeverListsPreviousOwnersHiddenNames() {
        personal.value = personal.value.copy(value = LiveTvPreferences(hiddenGroups = setOf("News")))
        board(); compose.onNodeWithTag("live-hidden").performClick()
        compose.runOnIdle {
            catalog.value = catalog.value.copy(browseScope = "b".repeat(64), channels = emptyList(), visibleChannels = emptyList(), groups = emptyList())
            personal.value = LiveTvPreferencesState("b".repeat(64), ready = true)
        }
        ready(); compose.onNodeWithTag("live-hidden-close").assertDoesNotExist()
        compose.onNodeWithTag("live-hidden").performClick()
        compose.onNodeWithTag("live-hidden-group-News").assertDoesNotExist()
    }
}