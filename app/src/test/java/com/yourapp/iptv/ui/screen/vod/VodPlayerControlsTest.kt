package com.yourapp.iptv.ui.screen.vod

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.media3.common.Format
import androidx.media3.common.TrackGroup
import androidx.media3.common.util.UnstableApi
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.player.vod.*
import com.yourapp.iptv.ui.component.common.LocalizedResources
import com.yourapp.iptv.ui.theme.IPTVAppTheme
import com.yourapp.iptv.viewmodel.VodPlaybackState
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34],application = Application::class,qualifiers = "w960dp-h540dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalComposeUiApi::class,ExperimentalTestApi::class)
@androidx.annotation.OptIn(UnstableApi::class)
class VodPlayerControlsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val item = VodPlaybackState.Ready("https://synthetic.invalid/10.mp4","Serie — Staffel 1, Folge 1","a".repeat(64),
        VodKind.SERIES,"10","mp4",0,"55",VodEpisode("20","Folge 2",1,2,"mp4"))
    private val snapshot = mutableStateOf(VodPlayerSnapshot(position = 60000,duration = 200000,playing = true,seekable = true,
        buffering = false,playWhenReady = true,speedSupported = true,buffered = 120000))
    private val steps = mutableListOf<Long>()
    private var next = 0
    private var automatic: Boolean? = null
    private var cancelled = 0
    private fun show(language: HomeLanguage = HomeLanguage.GERMAN, countdown: Int? = null, fontScale: Float = 1f, movie: Boolean = false) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,fontScale)) {
            IPTVAppTheme(theme = HomeTheme.RED) {
                LocalizedResources(language) {
                    val input = LocalInputModeManager.current
                    SideEffect { input.requestInputMode(InputMode.Keyboard) }
                    Box(Modifier.fillMaxSize().background(Color.Black)) {
                        VodPlayerControls(if (movie) item.copy(kind = VodKind.MOVIE,seriesId = null,next = null) else item,snapshot.value,VodPlaybackLabels(language),countdown != null,countdown,
                            VodPlayerActions({}, {}, { delta ->
                                steps.add(delta);snapshot.value = snapshot.value.copy(position = snapshot.value.position + delta)
                            }, {}, {}, {}, {}, { next++ },{ automatic = it },{ cancelled++ },{}),remember { FocusRequester() })
                    }
                }
            }
        } }
    }
    @Test fun remoteDownAndUpKeepTheFocusBetweenTheMainActionAndAudioChoice() {
        show()
        compose.onNodeWithTag("vod-toggle").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithTag("vod-toggle").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("vod-audio").assertIsFocused()
        compose.onNodeWithTag("vod-audio").performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithTag("vod-toggle").assertIsFocused()
        compose.onNodeWithTag("vod-toggle").performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithTag("vod-timeline").assertIsFocused()
    }
    @Test fun theRemoteTimelineSeeksTenSecondsPerPressWithoutAlsoApplyingTheDefaultSliderStep() {
        show()
        compose.onNodeWithTag("vod-timeline").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithTag("vod-timeline").performKeyInput { pressKey(Key.DirectionRight);pressKey(Key.DirectionLeft) }
        compose.runOnIdle { assertEquals(listOf(10000L,-10000L),steps);assertEquals(60000L,snapshot.value.position) }
    }
    @Test fun nextAndAutoplayRemainSeparateExplicitActions() {
        show();compose.onNodeWithTag("vod-next").performClick();compose.onNodeWithTag("vod-auto-next").performClick()
        compose.runOnIdle { assertEquals(1,next);assertEquals(true,automatic) }
    }
    @Test fun germanControlsAndTheCancelableNextPromptFitASmallTv() {
        snapshot.value = snapshot.value.copy(ended = true,playing = false)
        show(countdown = 7)
        listOf("vod-back","vod-timeline","vod-toggle","vod-audio","vod-subtitles","vod-next","vod-auto-next","vod-cancel-next")
            .forEach { compose.onNodeWithTag(it).assertIsDisplayed() }
        compose.onNodeWithText("Nächste Folge startet in 7 Sekunden").assertExists()
        export("de-small")
        compose.onNodeWithTag("vod-cancel-next").performClick();compose.runOnIdle { assertEquals(1,cancelled) }
    }
    @Test fun arabicControlsKeepTheSameTimelineDirectionsAndAllActionsVisible() {
        show(HomeLanguage.ARABIC)
        listOf("vod-timeline","vod-rewind","vod-forward","vod-next","vod-audio","vod-subtitles","vod-auto-next")
            .forEach { compose.onNodeWithTag(it).assertIsDisplayed() }
        compose.onNodeWithTag("vod-timeline").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithTag("vod-timeline").performKeyInput { pressKey(Key.DirectionRight) }
        compose.runOnIdle { assertEquals(listOf(10000L),steps) }
        val rewind = compose.onNodeWithTag("vod-rewind").fetchSemanticsNode().boundsInRoot
        val forward = compose.onNodeWithTag("vod-forward").fetchSemanticsNode().boundsInRoot
        assertTrue(rewind.left < forward.left)
        export("ar-small")
    }
    @Test fun trackDialogProvidesSupportedChoicesAutomaticAndSubtitleOffInTheChosenLanguage() {
        var selected: VodTrackChoice? = null;var off = 0
        val track = VodTrackChoice(TrackGroup("sub",Format.Builder().setSampleMimeType("text/vtt").setLanguage("ar").build()),0,"العربية",false)
        compose.setContent { IPTVAppTheme(theme = HomeTheme.RED) { LocalizedResources(HomeLanguage.GERMAN) {
            VodTrackDialog("Untertitel",VodTrackList(listOf(track)),true,VodPlaybackLabels(HomeLanguage.GERMAN),
                { selected = it },{ off++ },{})
        } } }
        compose.onNodeWithTag("vod-track-0").performClick();compose.onNodeWithTag("vod-track-off").performClick()
        compose.runOnIdle { assertEquals(track,selected);assertEquals(1,off) }
        compose.onNodeWithTag("vod-track-auto").performClick();compose.runOnIdle { assertNull(selected) }
        compose.onNodeWithText("Untertitel").assertExists();compose.onNodeWithText("Schließen").assertIsDisplayed()
    }
    @Test fun newToolsAndRemainingTimeAreVisibleWithoutInventingMediaMetadata() {
        show(HomeLanguage.ENGLISH)
        listOf("vod-speed","vod-display","vod-restart","vod-remaining").forEach { compose.onNodeWithTag(it).assertIsDisplayed() }
        compose.onNodeWithText("02:20 remaining").assertIsDisplayed()
        compose.onNodeWithTag("vod-buffered").assertRangeInfoEquals(ProgressBarRangeInfo(.6f,0f..1f))
        export("en-redesigned")
    }
    @Test fun unknownDurationDisablesSeekAndKeepsTheElapsedAndRemainingValuesHonest() {
        snapshot.value = snapshot.value.copy(duration = -1,seekable = false)
        show(HomeLanguage.ENGLISH)
        compose.onNodeWithTag("vod-timeline").assertIsNotEnabled()
        compose.onNodeWithTag("vod-rewind").assertIsNotEnabled()
        compose.onNodeWithTag("vod-restart").assertIsNotEnabled()
        compose.onNodeWithText("–:–– remaining").assertIsDisplayed()
        compose.onNodeWithTag("vod-elapsed").assertTextEquals("01:00")
        compose.onNodeWithTag("vod-buffered").assertRangeInfoEquals(ProgressBarRangeInfo(0f,0f..1f))
    }
    @Test fun speedModalAppliesAChoiceAndReturnsRemoteFocusToTheSpeedButton() {
        options()
        compose.onNodeWithTag("vod-speed").performClick()
        compose.onNodeWithTag("vod-speed-4").performScrollTo().performClick()
        compose.onNodeWithTag("vod-option-dialog").assertDoesNotExist()
        compose.onNodeWithTag("vod-speed").assertIsFocused()
        compose.runOnIdle { assertEquals(1.5f,snapshot.value.speed,0f) }
    }
    @Test fun pictureSelectionAndRestartCancellationReturnToTheirOwnControls() {
        options()
        compose.onNodeWithTag("vod-display").performClick()
        compose.onNodeWithTag("vod-display-1").performClick()
        compose.onNodeWithTag("vod-display").assertIsFocused()
        compose.onNodeWithText("Zoom").assertIsDisplayed()
        compose.onNodeWithTag("vod-restart").performClick()
        compose.onNodeWithTag("vod-restart-stay").assertIsFocused().performClick()
        compose.onNodeWithTag("vod-restart").assertIsFocused()
        compose.runOnIdle { assertEquals(60000L,snapshot.value.position) }
    }
    @Test fun restartOnlyRunsAfterItsSeparateConfirmation() {
        options()
        compose.onNodeWithTag("vod-restart").performClick()
        compose.onNodeWithTag("vod-restart-confirm").performClick()
        compose.runOnIdle { assertEquals(0L,snapshot.value.position) }
        compose.onNodeWithTag("vod-restart").assertIsFocused()
    }
    @Test fun largeTextKeepsTheScrollableToolsAndPrimaryControlsReachable() {
        show(HomeLanguage.ARABIC,fontScale = 1.6f)
        compose.onNodeWithTag("vod-toggle").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithTag("vod-toggle").assertIsDisplayed()
        compose.onNodeWithTag("vod-auto-next").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithTag("vod-auto-next").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(true,automatic) }
        export("ar-large-text")
    }
    @Test fun openingTracksStartsAtTheSelectedSupportedLanguage() {
        val track = VodTrackChoice(TrackGroup("text",Format.Builder().setSampleMimeType("text/vtt").setLanguage("en").build()),0,"English",true)
        compose.setContent { IPTVAppTheme { VodTrackDialog("Subtitles",VodTrackList(listOf(track),automatic = false),true,
            VodPlaybackLabels(HomeLanguage.ENGLISH),{}, {}, {}) } }
        compose.onNodeWithTag("vod-track-0").assertIsFocused()
    }
    @Test fun moviesKeepAllPlaybackToolsAndDoNotShowEpisodeActions() {
        show(HomeLanguage.ENGLISH,movie = true)
        compose.onNodeWithTag("vod-next").assertDoesNotExist()
        compose.onNodeWithTag("vod-auto-next").assertDoesNotExist()
        listOf("vod-toggle","vod-speed","vod-display","vod-audio","vod-subtitles").forEach {
            compose.onNodeWithTag(it).assertIsDisplayed()
        }
        export("movie-en")
    }
    private fun options() {
        val state = VodControlsState()
        compose.setContent { IPTVAppTheme { LocalizedResources(HomeLanguage.ENGLISH) {
            val input = LocalInputModeManager.current
            SideEffect { input.requestInputMode(InputMode.Keyboard) }
            var display by remember { mutableStateOf(VodDisplayMode.FIT) }
            val labels = VodPlaybackLabels(HomeLanguage.ENGLISH)
            fun open(panel: VodPlayerPanel,target: VodControlTarget) { state.open(panel,target) }
            VodPlayerControls(item,snapshot.value,labels,false,null,VodPlayerActions({}, {}, {}, {},
                { open(VodPlayerPanel.RESTART,VodControlTarget.RESTART) },{}, {},{}, {}, {}, {},
                speed = { open(VodPlayerPanel.SPEED,VodControlTarget.SPEED) },
                display = { open(VodPlayerPanel.DISPLAY,VodControlTarget.DISPLAY) }),remember { FocusRequester() },
                display,state.restore,state.focusEpoch,state.panel == null)
            state.panel?.let { panel -> VodPlayerOptionDialog(panel,labels,snapshot.value.speed,display,
                { snapshot.value = snapshot.value.copy(speed = it);state.closePanel() },
                { display = it;state.closePanel() },
                { snapshot.value = snapshot.value.copy(position = 0);state.closePanel() },state::closePanel) }
        } } }
    }
    private fun export(name: String) {
        val directory = System.getProperty("mnstreamx.uiPreviewDir")?.let(::File) ?: return
        val bounds = compose.onNodeWithTag("vod-controls",true).fetchSemanticsNode().boundsInRoot
        fun owner(view: View): View? {
            if (view.javaClass.name == "androidx.compose.ui.platform.AndroidComposeView") return view
            if (view is ViewGroup) for (i in 0 until view.childCount) owner(view.getChildAt(i))?.let { return it }
            return null
        }
        compose.runOnIdle {
            directory.mkdirs()
            val bitmap = Bitmap.createBitmap(bounds.width.toInt(),bounds.height.toInt(),Bitmap.Config.ARGB_8888)
            try {
                val canvas = Canvas(bitmap);canvas.translate(-bounds.left,-bounds.top)
                requireNotNull(owner(compose.activity.window.decorView)).draw(canvas)
                directory.resolve("vod-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
            } finally { bitmap.recycle() }
        }
    }
}