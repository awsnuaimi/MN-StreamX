package com.yourapp.iptv.ui.theme

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.ui.component.channel.ChannelCard
import com.yourapp.iptv.ui.component.common.AppButton
import com.yourapp.iptv.ui.component.common.LocalizedResources
import com.yourapp.iptv.ui.component.organization.OrganizerRow
import com.yourapp.iptv.ui.screen.appearance.AppearanceToggle
import com.yourapp.iptv.ui.screen.epg.EpgProgramCard
import com.yourapp.iptv.ui.screen.player.PlayerInfoOverlay
import com.yourapp.iptv.ui.screen.vod.VodPosterCard
import com.yourapp.iptv.ui.state.PlayerEpgUiState
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Renders production controls and player panels with native Skia; no player or network is created. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w1280dp-h720dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalComposeUiApi::class)
class ThemeRenderingTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val mode = mutableStateOf(true to false)
    private val language = mutableStateOf(HomeLanguage.GERMAN)
    private val channel = Channel("theme-synthetic", "MN Vorschau", streamUrl = "", group = "Test")
    private val program = Program("Programm Vorschau", 0, 60_000)

    private fun board(keyboard: Boolean = false) {
        compose.setContent {
            IPTVAppTheme(mode.value.first, mode.value.second) {
                LocalizedResources(language.value) {
                    if (keyboard) {
                        val input = LocalInputModeManager.current
                        SideEffect { input.requestInputMode(InputMode.Keyboard) }
                    }
                    Surface(Modifier.fillMaxSize().testTag("theme-board"), color = MaterialTheme.colorScheme.background) {
                        Row(Modifier.padding(24.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                AppButton("MN StreamX", {}, Modifier.fillMaxWidth().testTag("theme-button"))
                                ChannelCard(channel, {}, Modifier.testTag("theme-channel"))
                                OrganizerRow("Ordner", Modifier.testTag("theme-organizer"), subtitle = "Beschreibung") { }
                                Box(Modifier.testTag("theme-toggle")) { AppearanceToggle("Option", false, true) { } }
                                EpgProgramCard(program, false, 30_000,
                                    DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneOffset.UTC), {}, {},
                                    Modifier.testTag("theme-program"))
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Box(Modifier.fillMaxWidth().height(240.dp).testTag("theme-player").background(Color.Black)) {
                                    PlayerInfoOverlay(channel, 1, listOf(channel), PlayerAppearance(showLogo = false),
                                        PlayerEpgUiState(channel.id, EpgSchedule(listOf(program)), 30_000), PlaybackTechnicalInfo(),
                                        false, null, PlayerEpgUiState(), 0, false, {}, {}, {})
                                }
                                Box(Modifier.fillMaxWidth().testTag("theme-vod")) {
                                    VodPosterCard(VodItem(id = "theme-vod", name = "Film Vorschau", categoryId = null)) { }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Test fun theSameControlsAndPlayerPanelFollowEveryThemeWithoutReopening() {
        board()
        for ((dark, night) in listOf(true to false, false to false, true to true, false to true)) {
            compose.runOnIdle { mode.value = dark to night }
            val c = AppThemeColors.forMode(dark, night)
            pixel("theme-board", 3, 3, c.background)
            top("theme-button", c.surfaceContainerHigh)
            top("theme-channel", c.surfaceContainerLow)
            top("theme-organizer", c.surface)
            top("theme-toggle", c.surfaceContainer)
            top("theme-program", c.tertiaryContainer)
            top("theme-vod", c.surfaceContainerLow)
            val frame = draw("theme-player")
            try {
                assertPixel(frame.getPixel(frame.width / 2, frame.height - 3),
                    c.surfaceContainerLowest.copy(alpha = 0.98f).compositeOver(Color.Black))
            } finally { frame.recycle() }
            export("$dark-$night")
        }
    }

    @Test fun focusedButtonsUseTheCorrectForegroundAndAccentInEveryMode() {
        board(keyboard = true)
        compose.onNodeWithTag("theme-button", true).performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithTag("theme-button", true).assertIsFocused()
        for ((dark, night) in listOf(false to false, true to false, true to true)) {
            compose.runOnIdle { mode.value = dark to night }
            val colors = AppThemeColors.forMode(dark, night)
            // Material 3 draws a 10% focus state layer using the content color.
            val focused = colors.onPrimary.copy(alpha = 0.1f).compositeOver(colors.primary)
            top("theme-button", focused)
        }
    }

    @Test
    @Config(qualifiers = "w1920dp-h1080dp-mdpi")
    fun arabicDirectionAndFullHdViewportKeepTheThemeAndControlsAccessible() {
        board()
        compose.runOnIdle { language.value = HomeLanguage.ARABIC; mode.value = false to false }
        compose.onNodeWithTag("theme-button", true).assertExists().assertIsEnabled()
        top("theme-channel", AppThemeColors.forMode(false).surfaceContainerLow)
        export("rtl-light")
    }

    private fun top(tag: String, color: Color) {
        val bitmap = draw(tag)
        try { assertPixel(bitmap.getPixel(bitmap.width / 2, 6), color) } finally { bitmap.recycle() }
    }
    private fun pixel(tag: String, x: Int, y: Int, color: Color) {
        val bitmap = draw(tag)
        try { assertPixel(bitmap.getPixel(x, y), color) } finally { bitmap.recycle() }
    }
    private fun assertPixel(actual: Int, expected: Color) {
        val value = expected.toArgb()
        for (shift in listOf(16, 8, 0)) {
            assertTrue("Pixel ${actual.toUInt().toString(16)} != ${value.toUInt().toString(16)}",
                kotlin.math.abs(((actual ushr shift) and 255) - ((value ushr shift) and 255)) <= 3)
        }
    }

    private fun draw(tag: String): Bitmap {
        val bounds = compose.onNodeWithTag(tag, true).fetchSemanticsNode().boundsInRoot
        fun owner(view: View): View? {
            if (view.javaClass.name == "androidx.compose.ui.platform.AndroidComposeView") return view
            if (view is ViewGroup) for (index in 0 until view.childCount) {
                owner(view.getChildAt(index))?.let { return it }
            }
            return null
        }
        return compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(bounds.width.toInt(), bounds.height.toInt(), Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.translate(-bounds.left, -bounds.top)
            checkNotNull(owner(compose.activity.window.decorView)).draw(canvas)
            bitmap
        }
    }

    private fun export(name: String) {
        val directory = System.getProperty("mnstreamx.uiPreviewDir")?.let(::File) ?: return
        directory.mkdirs()
        val bitmap = draw("theme-board")
        try { directory.resolve("theme-$name.png").outputStream().use {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        } } finally { bitmap.recycle() }
    }
}