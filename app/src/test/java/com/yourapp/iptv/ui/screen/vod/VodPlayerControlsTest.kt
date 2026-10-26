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
import androidx.compose.ui.semantics.SemanticsActions
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
        buffering = false,playWhenReady = true))
    private val steps = mutableListOf<Long>()
    private var next = 0
    private var automatic: Boolean? = null
    private var cancelled = 0
    private fun show(language: HomeLanguage = HomeLanguage.GERMAN, countdown: Int? = null) {
        compose.setContent {
            IPTVAppTheme(theme = HomeTheme.RED) {
                LocalizedResources(language) {
                    val input = LocalInputModeManager.current
                    SideEffect { input.requestInputMode(InputMode.Keyboard) }
                    Box(Modifier.fillMaxSize().background(Color.Black)) {
                        VodPlayerControls(item,snapshot.value,VodPlaybackLabels(language),countdown != null,countdown,
                            VodPlayerActions({}, {}, { delta ->
                                steps.add(delta);snapshot.value = snapshot.value.copy(position = snapshot.value.position + delta)
                            }, {}, {}, {}, {}, { next++ },{ automatic = it },{ cancelled++ },{}),remember { FocusRequester() })
                    }
                }
            }
        }
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