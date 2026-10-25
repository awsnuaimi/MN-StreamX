package com.yourapp.iptv.ui.screen.home

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color as AndroidColor
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.yourapp.iptv.domain.model.Channel
import com.yourapp.iptv.domain.model.EpgSchedule
import com.yourapp.iptv.domain.model.HomeDashboardSelection
import com.yourapp.iptv.domain.model.HomeLanguage
import com.yourapp.iptv.domain.model.Program
import com.yourapp.iptv.ui.component.player.FrameCaptureResult
import com.yourapp.iptv.ui.component.player.LiveFrameCapture
import com.yourapp.iptv.ui.state.HomeProgramUiState
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
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
@OptIn(ExperimentalComposeUiApi::class)
class HomeDashboardPreviewTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val a = Channel("preview-a", "First Channel", streamUrl = "https://example.invalid/a")
    private val b = Channel("preview-b", "Second Channel", streamUrl = "https://example.invalid/b")
    private val labels = HomeLabels(HomeLanguage.GERMAN)
    private val colors = HomeTvPalettes.forMode(true)
    private val context get() = ApplicationProvider.getApplicationContext<Application>()

    @Before fun setup() { context.cacheDir.resolve("live-hero-frames-v2").deleteRecursively() }
    @After fun cleanup() { context.cacheDir.resolve("live-hero-frames-v2").deleteRecursively() }

    private fun dashboard(onPreview: (Channel?) -> Unit = {}, onPlay: (Channel) -> Unit = {}) {
        compose.setContent {
            MaterialTheme {
                val input = LocalInputModeManager.current
                SideEffect { input.requestInputMode(InputMode.Keyboard) }
                Box(Modifier.width(720.dp)) {
                    HomeDashboard(labels, HomeDashboardSelection(listOf(a, b), emptyList(), a), 2,
                        remember { FocusRequester() }, listOf(a, b), true, false,
                        onPlay, {}, {}, {}, {}, {}, onPreviewChannel = onPreview)
                }
            }
        }
    }

    private fun focus(tag: String) {
        compose.onNodeWithTag(tag, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithTag(tag, useUnmergedTree = true).assertIsFocused()
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
    }

    @Test fun briefFocusDoesNotChangeHeroButSettledFocusAndWatchUseTheSameChannel() {
        val previews = mutableListOf<String?>()
        var played: Channel? = null
        dashboard({ previews += it?.id }, { played = it })
        compose.mainClock.autoAdvance = false
        focus("home-channel-${b.id}")
        compose.mainClock.advanceTimeBy(100)
        focus("home-channel-${a.id}")
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithTag("hero-channel-name", true).assertTextEquals(a.name)
        assertFalse(previews.contains(b.id))
        focus("home-channel-${b.id}")
        compose.mainClock.advanceTimeBy(320)
        compose.onNodeWithTag("hero-channel-name", true).assertTextEquals(b.name)
        compose.onNodeWithTag("hero-continue", true).performClick()
        compose.runOnIdle { assertEquals(b, played) }
    }

    @Test fun leavingACardBeforeTheDelayCancelsItsPendingPreview() {
        dashboard()
        compose.mainClock.autoAdvance = false
        focus("home-channel-${b.id}")
        compose.mainClock.advanceTimeBy(100)
        focus("hero-continue")
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithTag("hero-channel-name", true).assertTextEquals(a.name)
    }

    @Test fun currentProgramIsOptionalAndNeverComesFromAnotherSource() {
        val now = 1_700_000_060_000L
        val guide = mutableStateOf(HomeProgramUiState(a.id, a.streamUrl,
            EpgSchedule(listOf(Program("Synthetic programme", now - 60_000, now + 60_000))), now))
        compose.setContent {
            MaterialTheme {
                Box(Modifier.width(720.dp)) {
                    DashboardWelcome(labels, a, 2, HomeDashboardLayout.forWidth(720), colors, guide.value) { }
                }
            }
        }
        compose.onNodeWithText("Jetzt: Synthetic programme", useUnmergedTree = true).assertExists()
        compose.runOnIdle { guide.value = guide.value.copy(streamUrl = "https://example.invalid/foreign") }
        compose.onNodeWithTag("home-now-playing", true).assertDoesNotExist()
        compose.onNodeWithTag("hero-continue", true).assertExists()
        compose.runOnIdle { guide.value = guide.value.copy(streamUrl = a.streamUrl, nowMillis = now + 120_000) }
        compose.onNodeWithTag("home-now-playing", true).assertDoesNotExist()
    }

    @Test fun heroPreservesTopAndBottomOfTheSavedFrameInBothDirections() {
        // Visible edge bands make a cropped image fail even when its centre looks correct.
        val image = Bitmap.createBitmap(640, 360, Bitmap.Config.ARGB_8888)
        image.eraseColor(AndroidColor.GREEN)
        val canvas = Canvas(image)
        canvas.drawRect(0f, 0f, 640f, 36f, Paint().apply { color = AndroidColor.RED })
        canvas.drawRect(0f, 324f, 640f, 360f, Paint().apply { color = AndroidColor.BLUE })
        canvas.drawRect(0f, 36f, 160f, 324f, Paint().apply { color = AndroidColor.YELLOW })
        assertEquals(FrameCaptureResult.SAVED, runBlocking {
            LiveFrameCapture.saveCopiedFrame(context, a, image)
        })
        val direction = mutableStateOf(LayoutDirection.Ltr)
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalLayoutDirection provides direction.value) {
                    Box(Modifier.width(720.dp)) {
                        DashboardWelcome(labels, a, 2, HomeDashboardLayout.forWidth(720), colors) { }
                    }
                }
            }
        }
        for (value in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl)) {
            compose.runOnIdle { direction.value = value }
            compose.waitUntil(10_000) {
                compose.onAllNodesWithTag("saved-channel-frame", true).fetchSemanticsNodes().size == 1
            }
            val frame = draw("saved-channel-frame")
            try {
                val pictureHeight = frame.width * 9f / 16f
                val top = (frame.height - pictureHeight) / 2f
                val x = if (value == LayoutDirection.Ltr) frame.width - 12 else 12
                val red = frame.getPixel(x, (top + pictureHeight * 0.05f).toInt())
                val blue = frame.getPixel(x, (top + pictureHeight * 0.95f).toInt())
                assertTrue("Top band was cropped in $value", AndroidColor.red(red) > AndroidColor.green(red) * 2)
                assertTrue("Bottom band was cropped in $value", AndroidColor.blue(blue) > AndroidColor.green(blue) * 2)
                val hero = compose.onNodeWithTag("hero-artwork", true).fetchSemanticsNode().boundsInRoot
                assertTrue("Hero is too tall: $hero", hero.height <= 240f)
                export(value.toString())
            } finally { frame.recycle() }
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
        val image = draw("hero-artwork")
        try { directory.resolve("preview-$name.png").outputStream().use {
            check(image.compress(Bitmap.CompressFormat.PNG, 100, it))
        } } finally { image.recycle() }
    }
}