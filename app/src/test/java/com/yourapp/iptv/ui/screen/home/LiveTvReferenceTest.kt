package com.yourapp.iptv.ui.screen.home

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.*
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.domain.organization.*
import com.yourapp.iptv.ui.state.*
import com.yourapp.iptv.ui.theme.IPTVAppTheme
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w960dp-h540dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class, ExperimentalCoroutinesApi::class)
class LiveTvReferenceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val channels = listOf("TTV", "TV4", "TV6", "TVN", "TVN 7", "TVN STYLE", "TVN FABULA", "TVP 1")
        .mapIndexed { i, name -> Channel("$i", "PL| $name FHD", streamUrl = "https://synthetic.invalid/$i", group = "PL| GENERAL") }
    private val state = HomeUiState(channels = channels, visibleChannels = channels,
        groups = listOf("PL| GENERAL"), selectedGroup = "PL| GENERAL", isLoading = false, browseScope = "owner")
    private val guides = HomeGuideState("owner", 7, channels.map {
        HomeProgramUiState(it.id, it.streamUrl, EpgSchedule(listOf(
            Program("Programme ${it.id}", 0, 100_000, "Synthetic current programme description."),
            Program("Next ${it.id}", 100_000, 200_000))), 50_000)
    })
    private val captions = CaptionUiState(CaptionSettings(mode = CaptionMode.GENERATED), loaded = true)
    private var played: String? = null
    private var translated: String? = null
    private var favorite: String? = null
    private var restored: Long? = null
    private var position: Pair<String, HomeGridPosition>? = null
    private val bound = mutableListOf<List<Channel>>()

    private fun board(value: HomeUiState = state, guide: HomeGuideState = guides,
        language: HomeLanguage = HomeLanguage.GERMAN, scale: Float = 1f,
        shown: () -> Boolean = { true }, onQuery: (String) -> Unit = {}, onGroup: (String?) -> Unit = {},
        projectionDispatcher: CoroutineDispatcher? = null, awaitReady: Boolean = true,
        valueProvider: () -> HomeUiState = { value }) {
        compose.setContent {
            IPTVAppTheme {
                val input = LocalInputModeManager.current
                SideEffect { input.requestInputMode(InputMode.Keyboard) }
                val density = LocalDensity.current
                val appearance = HomeAppearance(language = language, expandedSidebar = true)
                val labels = HomeLabels(language)
                val focus = remember { HomeSection.entries.associateWith { androidx.compose.ui.focus.FocusRequester() } }
                CompositionLocalProvider(LocalDensity provides Density(density.density, scale),
                    LocalLayoutDirection provides if (appearance.isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                    Surface(Modifier.fillMaxSize().testTag("live-reference-board")) {
                        Row(Modifier.fillMaxSize()) {
                            HomeSidebar(appearance, labels, HomeSection.LIVE, false, focus, {}, {}, liveBrowser = true)
                            if (shown()) LiveTvBrowser(valueProvider(), appearance, labels, guide, 7, captions,
                                onQuery, onGroup, { played = it.id }, { translated = it.id }, { favorite = it.id }, true,
                                { id, item -> position = id to item }, { restored = it }, { bound += it }, Modifier.weight(1f),
                                projectionDispatcher = projectionDispatcher ?: Dispatchers.Main.immediate)
                        }
                    }
                }
            }
        }
        if (shown() && awaitReady) awaitList()
        compose.waitForIdle()
    }
    private fun awaitList() {
        // Lazy content is ready even when item zero is outside the composed viewport.
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("live-channel-list").fetchSemanticsNodes().size == 1 }
        compose.waitForIdle()
    }
    private fun focus(tag: String) {
        if (tag.startsWith("live-channel-")) {
            compose.onNodeWithTag("live-channel-list").performScrollToNode(hasTestTag(tag))
        }
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.RequestFocus) { it() }
    }
    private fun preview(name: String) {
        val destination = System.getProperty("mnx.preview.dir") ?: return
        compose.runOnIdle {
            val view = compose.activity.findViewById<View>(android.R.id.content)
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap)); val file = File(destination, name); file.parentFile?.mkdirs()
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        }
    }
    @Test fun eightRowsAndRightDetailFollowReferenceProportions() {
        board(); focus("live-channel-5")
        val row = compose.onNodeWithTag("live-channel-5").fetchSemanticsNode().boundsInRoot
        val detail = compose.onNodeWithTag("live-detail").fetchSemanticsNode().boundsInRoot
        assertTrue(row.right < detail.left); assertEquals(2.04f, row.width / detail.width, 0.05f)
        compose.onNodeWithTag("live-channel-7").assertIsDisplayed()
        val bottom = compose.onNodeWithTag("live-channel-7").fetchSemanticsNode().boundsInRoot.bottom
        val viewport = compose.onNodeWithTag("live-channel-list").fetchSemanticsNode().boundsInRoot
        assertTrue("Eighth row must fit fully", bottom <= viewport.bottom + .1f)
        compose.onNodeWithTag("live-watch").assertIsDisplayed()
        compose.onNodeWithTag("live-translation").assertIsDisplayed()
        compose.onNodeWithTag("live-detail-title").assertTextEquals("TVN STYLE")
        compose.onNodeWithTag("live-now-5", true).assertTextEquals("Programme 5")
        compose.onNodeWithTag("live-next-5", true).assertTextContains("Next 5", substring = true)
        compose.onNodeWithTag("live-progress-5", true).assertExists()
        compose.onNodeWithText("4K", substring = true).assertDoesNotExist()
        preview("live-tv-reference-de.png")
    }
    @Test fun focusChangesPreviewWithoutStartingPlaybackAndWatchReturnsToSameRow() {
        board(); focus("live-channel-5"); assertNull(played)
        compose.onNodeWithTag("live-channel-5").performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag("live-watch").assertIsFocused()
        compose.onNodeWithTag("live-watch").performKeyInput { pressKey(Key.DirectionLeft) }
        compose.onNodeWithTag("live-channel-5").assertIsFocused()
        compose.onNodeWithTag("live-channel-5").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("live-detail-title").assertTextEquals("TVN FABULA")
        assertNull(played)
        compose.onNodeWithTag("live-watch").performClick()
        compose.runOnIdle { assertEquals("6", played) }
    }
    @Test fun differentOwnersAndEpochsDoNotDisplayOldProgrammeTitles() {
        board(guide = guides.copy(sourceKey = "other", accessEpoch = 6))
        compose.onNodeWithText("Programme 0", substring = true).assertDoesNotExist()
        compose.onNodeWithTag("live-progress-0", true).assertDoesNotExist()
        compose.onNodeWithTag("live-channel-0").performClick()
        compose.runOnIdle { assertEquals("0", played) }
    }
    @Test fun guidesAreBoundOnlyForVisibleWindowAndClearedOnExit() {
        var shown by mutableStateOf(true)
        val many = (0 until 7490).map { Channel("$it", "Channel $it HD", streamUrl = "https://synthetic.invalid/$it") }
        board(state.copy(channels = many, visibleChannels = many), shown = { shown })
        compose.runOnIdle { assertTrue(bound.last().size in 1..10) }
        compose.onNodeWithTag("live-channel-list").performScrollToIndex(100)
        compose.waitUntil(10_000) { bound.last().any { it.id == "100" } }
        compose.runOnIdle { assertTrue(bound.last().size <= 11); shown = false }
        compose.runOnIdle { assertTrue(bound.last().isEmpty()) }
    }
    @Test fun restoreSelectsTheExactVariantAndAcknowledgesThePosition() {
        val alternative = channels[5].copy(id = "hd", name = "PL| TVN STYLE HD", streamUrl = "https://synthetic.invalid/hd")
        board(state.copy(channels = channels + alternative, visibleChannels = channels + alternative,
            selectedChannelId = "hd", gridRestore = HomeGridRestore(91, HomeGridPosition("3", 0, 1))))
        compose.onNodeWithTag("live-channel-5").assertIsFocused()
        compose.runOnIdle { assertEquals(91L, restored); assertEquals("hd", position?.first) }
        compose.onNodeWithTag("live-watch").performClick()
        compose.runOnIdle { assertEquals("hd", played) }
    }
    @Test fun longPressOffersRealVariantsAndFavorites() {
        val alt = channels[0].copy(id = "hd", name = "PL| TTV HD", streamUrl = "https://synthetic.invalid/hd")
        board(state.copy(channels = channels + alt, visibleChannels = channels + alt))
        focus("live-channel-0")
        compose.onNodeWithTag("live-channel-0").performTouchInput { longClick() }
        compose.onNodeWithTag("live-menu-quality-hd").assertExists()
        compose.onNodeWithTag("live-menu-favorite").performClick()
        compose.runOnIdle { assertEquals("0", favorite) }
    }
    @Test fun searchSortAndCategoryFiltersKeepTheirActualCallbacks() {
        var query = ""; var group: String? = "untouched"
        board(onQuery = { query = it }, onGroup = { group = it })
        compose.onNodeWithTag("live-search").performClick()
        compose.onNodeWithTag("live-search-field").performTextInput("TVN")
        compose.runOnIdle { assertEquals("TVN", query) }
        compose.onNodeWithTag("live-search").performClick()
        compose.onNodeWithTag("live-sort").performClick()
        compose.onNodeWithTag("live-sort-NAME").performClick()
        compose.onNodeWithTag("live-category-all").performClick()
        compose.runOnIdle { assertEquals("", query); assertNull(group) }
    }
    @Test fun arabicLargeTextKeepsRemoteRowsAccessibleWithoutCrampedDetail() {
        board(language = HomeLanguage.ARABIC, scale = 2f)
        compose.onNodeWithTag("live-detail").assertDoesNotExist()
        focus("live-channel-0")
        repeat(7) { compose.onNodeWithTag("live-channel-$it").performKeyInput { pressKey(Key.DirectionDown) } }
        compose.onNodeWithTag("live-channel-7").assertIsDisplayed().assertIsFocused()
        preview("live-tv-reference-ar-large.png")
    }
    @Test fun arabicRestorationCanStartAtTheEndWhileTheFirstRowIsNotComposed() {
        board(state.copy(selectedChannelId = "7", gridRestore = HomeGridRestore(92, HomeGridPosition("5", 0, 1))),
            language = HomeLanguage.ARABIC, scale = 2f)
        compose.onNodeWithTag("live-channel-7").assertIsDisplayed().assertIsFocused()
        compose.onNodeWithTag("live-channel-0").assertDoesNotExist()
        compose.runOnIdle { assertEquals(92L, restored); assertNull(played) }
    }

    @Test fun queuedProjectionLoadsOnlyAfterItsWorkerRunsAndDropsThePreviousOwner() {
        val worker = StandardTestDispatcher()
        var current by mutableStateOf(state)
        board(projectionDispatcher = worker, awaitReady = false, valueProvider = { current })
        compose.onNodeWithTag("live-channel-list").assertDoesNotExist()
        compose.runOnIdle {
            val channel = channels[0].copy(id = "new", name = "New owner HD", streamUrl = "https://synthetic.invalid/new")
            current = state.copy(channels = listOf(channel), visibleChannels = listOf(channel), browseScope = "new-owner")
        }
        compose.waitForIdle()
        compose.onNodeWithTag("live-channel-list").assertDoesNotExist()
        worker.scheduler.runCurrent()
        awaitList()
        compose.onNodeWithTag("live-channel-new").assertIsDisplayed()
        compose.onNodeWithTag("live-channel-0").assertDoesNotExist()
        compose.onNodeWithText("Programme 0", substring = true).assertDoesNotExist()
    }

    @Test fun hierarchyExposesTheParentAndUsesStableFolderIdentifiers() {
        val folders = listOf(ChannelFolder("pl", name = "Poland"), ChannelFolder("general", "pl", name = "General"))
        val tree = OrganizationTree(true, folders, mapOf("pl" to 8, "general" to 8))
        var selected: String? = "untouched"
        board(state.copy(organization = tree, selectedGroup = FolderFilter.of("general")), onGroup = { selected = it })
        compose.onNodeWithTag("live-category-${FolderFilter.of("general")}").assertExists()
        compose.onNodeWithTag("live-category-parent").performClick()
        compose.runOnIdle { assertNull(selected) }
    }
    @Test fun holdingRemoteOkOpensActionsAndNeverAlsoStartsTheStream() {
        board(); focus("live-channel-0")
        compose.onNodeWithTag("live-channel-0").performKeyInput { keyDown(Key.DirectionCenter) }
        compose.mainClock.advanceTimeBy(800)
        compose.onNodeWithTag("live-channel-0").performKeyInput { keyUp(Key.DirectionCenter) }
        compose.onNodeWithTag("live-menu-favorite").assertExists()
        compose.runOnIdle { assertNull(played) }
    }

    @Test fun leavingATheRowCancelsItsPendingRemoteHold() {
        board(); focus("live-channel-0")
        compose.onNodeWithTag("live-channel-0").performKeyInput { keyDown(Key.DirectionCenter) }
        focus("live-search")
        compose.mainClock.advanceTimeBy(800)
        compose.onNodeWithTag("live-menu-favorite").assertDoesNotExist()
        compose.runOnIdle { assertNull(played) }
    }

    @Test fun existingSavedFramesAppearWithoutAnyPreviewPlayer() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<Application>()
        val image = Bitmap.createBitmap(640, 360, Bitmap.Config.ARGB_8888)
        Canvas(image).apply {
            drawColor(android.graphics.Color.rgb(207, 219, 218))
            drawRect(0f, 220f, 640f, 360f, android.graphics.Paint().apply { color = android.graphics.Color.rgb(136, 80, 55) })
            drawCircle(370f, 140f, 70f, android.graphics.Paint().apply { color = android.graphics.Color.rgb(164, 44, 49) })
        }
        try {
            val result = kotlinx.coroutines.runBlocking {
                com.yourapp.iptv.ui.component.player.LiveFrameCapture.saveCopiedFrame(context, channels[5], image)
            }
            assertEquals(com.yourapp.iptv.ui.component.player.FrameCaptureResult.SAVED, result)
            board(); focus("live-channel-5")
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("saved-channel-frame", true).fetchSemanticsNodes().isNotEmpty() }
            preview("live-tv-reference-saved-frame.png")
        } finally {
            if (!image.isRecycled) image.recycle()
            context.cacheDir.resolve("live-hero-frames-v2").deleteRecursively()
        }
    }

    @Test fun aLargeVariantMenuRemainsLazyAndEveryQualityCanStillBePlayed() {
        val variants = (0 until 1000).map { channels[0].copy(id = "$it", streamUrl = "https://synthetic.invalid/$it") }
        board(state.copy(channels = variants, visibleChannels = variants))
        compose.onNodeWithTag("live-channel-0").performTouchInput { longClick() }
        compose.onNodeWithTag("live-menu-quality-999").assertDoesNotExist()
        compose.onNodeWithTag("live-menu-variants").performScrollToIndex(1000)
        compose.onNodeWithTag("live-menu-quality-999").performClick()
        compose.runOnIdle { assertEquals("999", played) }
    }

    @Test fun translationActionUsesSelectedChannelAndConfiguredTarget() {
        board(); focus("live-channel-5")
        compose.onNodeWithTag("live-translation").performClick()
        compose.runOnIdle { assertEquals("5", translated); assertNull(played) }
    }
}