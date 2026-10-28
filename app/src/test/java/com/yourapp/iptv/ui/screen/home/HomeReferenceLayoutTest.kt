package com.yourapp.iptv.ui.screen.home

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color as AndroidColor
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
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
import androidx.test.core.app.ApplicationProvider
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.ui.component.player.LiveFrameCapture
import com.yourapp.iptv.ui.component.player.FrameCaptureResult
import com.yourapp.iptv.ui.state.HomeProgramUiState
import com.yourapp.iptv.ui.state.CaptionUiState
import com.yourapp.iptv.ui.state.HomeGuideState
import com.yourapp.iptv.ui.theme.IPTVAppTheme
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w960dp-h540dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalComposeUiApi::class, ExperimentalTestApi::class)
class HomeReferenceLayoutTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val context get() = ApplicationProvider.getApplicationContext<Application>()
    private val captions = CaptionUiState(CaptionSettings(mode = CaptionMode.GENERATED), loaded = true)
    private val oldZone = TimeZone.getDefault()
    private val channels = listOf("AR| AD SPORT 1", "PL| TVN STYLE HD", "AR| AD ASIA 2", "PL| TVN FABULA", "DE| Sender 5")
        .mapIndexed { i, name -> Channel("reference-$i", name, streamUrl = "https://synthetic.invalid/$i", group = "Synthetic group") }
    private val now = ZonedDateTime.of(2026, 10, 28, 19, 7, 0, 0, ZoneId.of("Europe/Berlin")).toInstant().toEpochMilli()
    private val guide = HomeProgramUiState(channels[1].id, channels[1].streamUrl,
        EpgSchedule(listOf(Program("Current programme", now - 27 * 60_000L, now + 23 * 60_000L,
            "A short synthetic programme description, displayed in two readable lines."),
            Program("Next programme", now + 23 * 60_000L, now + 68 * 60_000L),
            Program("Evening programme", now + 68 * 60_000L, now + 113 * 60_000L),
            Program("Late programme", now + 113 * 60_000L, now + 158 * 60_000L))), now)
    @Before fun setup() {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Berlin"))
        context.cacheDir.resolve("live-hero-frames-v2").deleteRecursively()
    }
    @After fun cleanup() {
        TimeZone.setDefault(oldZone)
        context.cacheDir.resolve("live-hero-frames-v2").deleteRecursively()
    }

    private fun board(language: HomeLanguage = HomeLanguage.GERMAN, scale: Float = 1f,
        onAction: (HomeSection) -> Unit = {}, onSearch: () -> Unit = {}) {
        compose.setContent {
            IPTVAppTheme {
                val input = LocalInputModeManager.current
                SideEffect { input.requestInputMode(InputMode.Keyboard) }
                val density = LocalDensity.current
                val appearance = HomeAppearance(language = language)
                val labels = HomeLabels(language)
                val focus = remember { FocusRequester() }
                val rail = remember { HomeSection.entries.associateWith { FocusRequester() } }
                CompositionLocalProvider(LocalDensity provides Density(density.density, scale),
                    LocalLayoutDirection provides if (appearance.isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                    Surface(Modifier.fillMaxSize().testTag("reference-board")) {
                        Row(Modifier.fillMaxSize()) {
                            HomeSidebar(appearance, labels, HomeSection.DASHBOARD, false, rail, {}, onAction,
                                dashboardFocus = focus)
                            Column(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 30.dp, vertical = 20.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                HomeDashboardTopBar(labels, "Bisheriges Profil", {}, onSearch, {},
                                    translationLabel = homeTranslationLabel(captions, labels), translationSelected = true)
                                Box(Modifier.weight(1f)) {
                                    HomeDashboard(labels, HomeDashboardSelection(channels.take(2), channels.take(1), channels[1]),
                                        7490, focus, channels, true, false, {}, {}, {}, {}, {}, {}, programState = guide,
                                        catalogChannels = channels, captionState = captions, interfaceLanguage = language,
                                        guideOwner = "synthetic-owner", accessEpoch = 0,
                                        guideState = HomeGuideState("synthetic-owner", 0, channels.map { guide.copy(channelId = it.id, streamUrl = it.streamUrl) }))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Test fun theHeroComesFirstAndTheNavigationRailLeavesRoomForFourCards() {
        board()
        val hero = compose.onNodeWithTag("home-hero", true).fetchSemanticsNode().boundsInRoot
        val first = compose.onNodeWithTag("home-channel-reference-0", true).fetchSemanticsNode().boundsInRoot
        val rail = compose.onNodeWithTag("rail-DASHBOARD", true).fetchSemanticsNode().boundsInRoot
        assertTrue(hero.top < first.top && hero.bottom <= first.top)
        assertTrue(rail.right < hero.left)
        assertTrue(hero.width > 800f)
        compose.onNodeWithTag("home-channel-reference-3").assertIsDisplayed()
        compose.onNodeWithText("Entdecken").assertDoesNotExist()
        compose.onNodeWithTag("home-profile").assertIsDisplayed()
        compose.onNodeWithTag("home-search").assertIsDisplayed().performClick()
    }

    @Test fun aRealGuideShowsTitleDescriptionRemainingTimeAndTheCurrentTimeline() {
        board()
        compose.onNodeWithTag("hero-description", true).assertExists()
        compose.onNodeWithText("18:40 – 19:30 · noch 23 Min.", true).assertExists()
        compose.onNodeWithText("Jetzt: Current programme", true).assertExists()
        compose.onNodeWithText("19:30 · Next programme", true).assertExists()
        compose.onNodeWithText("7490 Sender verfügbar").assertDoesNotExist()
    }

    @Test fun overflowKeepsSeriesAndGuideReachableWithoutLeavingHiddenFocusTargets() {
        val clicked = mutableListOf<HomeSection>()
        board(onAction = { clicked += it })
        compose.onNodeWithTag("rail-MORE").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithTag("rail-MORE").performClick()
        compose.onNodeWithTag("rail-menu-SERIES").performClick()
        compose.runOnIdle { assertEquals(listOf(HomeSection.SERIES), clicked) }
        assertEquals(HomeSection.MORE, HomeFocusPolicy.railTarget(HomeSection.SERIES))
        assertEquals(HomeSection.SETTINGS, HomeFocusPolicy.railTarget(HomeSection.SETTINGS))
        compose.onNodeWithTag("rail-SETTINGS").assertIsDisplayed()
    }

    @Test fun theRailMovesFocusToTheHeroWatchActionWithTheRemote() {
        board()
        compose.onNodeWithTag("rail-DASHBOARD").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithTag("rail-DASHBOARD").performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag("hero-continue").assertIsFocused()
    }

    @Test fun nativePreviewUsesSavedFramesInGermanAndKeepsArabicLargeTextNavigable() {
        val image = Bitmap.createBitmap(640, 360, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(image)
            canvas.drawColor(AndroidColor.rgb(212, 223, 216))
            canvas.drawRect(0f, 210f, 640f, 360f, Paint().apply { color = AndroidColor.rgb(168, 100, 58) })
            canvas.drawCircle(400f, 140f, 70f, Paint().apply { color = AndroidColor.rgb(160, 35, 35) })
            assertEquals(FrameCaptureResult.SAVED, runBlocking { LiveFrameCapture.saveCopiedFrame(context, channels[1], image) })
        } finally { if (!image.isRecycled) image.recycle() }
        board()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("saved-channel-frame", true).fetchSemanticsNodes().isNotEmpty() }
        export("home-reference-de.png")
    }

    @Test fun arabicWithLargeSystemFontKeepsWatchAndOverflowFocusable() {
        board(HomeLanguage.ARABIC, 1.5f)
        compose.onNodeWithTag("rail-DASHBOARD").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithTag("rail-DASHBOARD").performKeyInput { pressKey(Key.DirectionLeft) }
        compose.onNodeWithTag("hero-continue").assertIsFocused()
        compose.onNodeWithTag("rail-MORE").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithTag("rail-MORE").assertIsFocused()
        export("home-reference-ar-large.png")
    }

    private fun export(name: String) {
        val dir = System.getProperty("mnstreamx.uiPreviewDir")?.let(::File) ?: return
        dir.mkdirs()
        fun owner(view: View): View? {
            if (view.javaClass.name == "androidx.compose.ui.platform.AndroidComposeView") return view
            if (view is ViewGroup) for (index in 0 until view.childCount) owner(view.getChildAt(index))?.let { return it }
            return null
        }
        val bounds = compose.onNodeWithTag("reference-board", true).fetchSemanticsNode().boundsInRoot
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