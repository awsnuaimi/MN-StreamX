package com.yourapp.iptv.ui.screen.home

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import android.view.ViewGroup
import android.graphics.Color as AndroidColor
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.yourapp.iptv.domain.model.Channel
import com.yourapp.iptv.domain.model.HomeLanguage
import com.yourapp.iptv.ui.component.player.ChannelFrameStore
import com.yourapp.iptv.ui.component.player.LiveFrameCapture
import com.yourapp.iptv.ui.component.player.FrameCaptureResult
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLog
import java.io.ByteArrayOutputStream
import java.io.File

/** Real Compose layout and JPEG decoding; no stream, real channel or provider fixture. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w960dp-h540dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeFrameRenderingTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var server: MockWebServer
    private lateinit var channel: Channel
    private val context get() = ApplicationProvider.getApplicationContext<Application>()
    private val metrics = HomeDashboardLayout.forWidth(720)
    private val colors = HomeTvPalettes.forMode(true)

    @Before fun start() {
        ShadowLog.stream = System.err
        context.cacheDir.resolve("live-hero-frames-v2").deleteRecursively()
        server = MockWebServer()
        val logo = Bitmap.createBitmap(64, 32, Bitmap.Config.ARGB_8888)
        logo.eraseColor(AndroidColor.WHITE)
        val bytes = ByteArrayOutputStream().use { out ->
            logo.compress(Bitmap.CompressFormat.PNG, 100, out); out.toByteArray()
        }
        logo.recycle()
        repeat(12) { server.enqueue(MockResponse().setHeader("Content-Type", "image/png")
            .setBody(Buffer().write(bytes))) }
        server.start()
        channel = Channel(id = "synthetic-frame-test", name = "Test Channel", streamUrl = "https://example.invalid/stream",
            logoUrl = server.url("/logo.png").toString(), group = "Test group")
    }

    @After fun stop() {
        ShadowLog.stream = null
        server.shutdown()
        context.cacheDir.resolve("live-hero-frames-v2").deleteRecursively()
    }

    private fun hero(direction: LayoutDirection) {
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalLayoutDirection provides direction) {
                    Box(Modifier.width(720.dp)) {
                        DashboardWelcome(HomeLabels(HomeLanguage.GERMAN), channel, 42, metrics, colors) { }
                    }
                }
            }
        }
        val logo = compose.onNodeWithTag("hero-logo", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val action = compose.onNodeWithTag("hero-continue", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("Continue overlaps logo: $action / $logo", action.right <= logo.left || logo.right <= action.left)
        assertTrue(action.height >= 35f && action.height <= 50f)
        export("hero-$direction", "hero-artwork")
    }

    private fun export(name: String, tag: String) {
        val directory = System.getProperty("mnstreamx.uiPreviewDir")?.let(::File) ?: return
        directory.mkdirs()
        val bitmap = draw(tag)
        directory.resolve("$name.png").outputStream().use {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        bitmap.recycle()
    }

    /** Software Canvas renders Compose with native Skia; it needs no hardware window/PixelCopy. */
    private fun draw(tag: String): Bitmap {
        val bounds = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
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

    @Test fun fallbackHeroKeepsLogoSeparateFromActionInGerman() = hero(LayoutDirection.Ltr)
    @Test fun fallbackHeroKeepsLogoSeparateFromActionInArabicDirection() = hero(LayoutDirection.Rtl)

    private fun card() {
        compose.setContent {
            MaterialTheme {
                HomeCinematicChannelCard(channel, metrics, colors, HomeLabels(HomeLanguage.ENGLISH)) { }
            }
        }
    }

    @Test fun fallbackCardDoesNotPlaceLogoUnderItsCaption() {
        card()
        val logo = compose.onNodeWithTag("channel-card-logo", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val caption = compose.onNodeWithTag("channel-card-caption", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("Caption overlaps logo", logo.bottom <= caption.top)
    }

    private fun savePicture() {
        val image = Bitmap.createBitmap(640, 360, Bitmap.Config.ARGB_8888)
        image.eraseColor(AndroidColor.rgb(240, 30, 30))
        Canvas(image).drawRect(0f, 0f, 160f, 360f,
            Paint().apply { color = AndroidColor.rgb(25, 110, 230) })
        try { runBlocking {
            assertEquals(FrameCaptureResult.SAVED, LiveFrameCapture.saveCopiedFrame(context, channel, image))
        } } finally { if (!image.isRecycled) image.recycle() }
    }

    private fun awaitRenderedPicture() {
        try { compose.waitUntil(10000) {
            compose.onAllNodesWithTag("saved-channel-frame", useUnmergedTree = true).fetchSemanticsNodes().size == 1
        } } catch (error: ComposeTimeoutException) {
            System.err.println(compose.onRoot(useUnmergedTree = true).printToString())
            System.err.println("Cached JPEG: ${ChannelFrameStore.fileFor(context, channel)?.length()} bytes")
            throw error
        }
        val image = draw("saved-channel-frame")
        try {
            val pixel = image.getPixel((image.width * 0.75f).toInt(), (image.height * 0.25f).toInt())
            assertTrue("JPEG was not rendered: $pixel", AndroidColor.red(pixel) > 200 && AndroidColor.green(pixel) < 76)
        } finally { image.recycle() }
    }

    @Test fun aLateSavedJpegAppearsWithoutReopeningTheHomeScreen() {
        card()
        compose.onNodeWithTag("saved-channel-frame").assertDoesNotExist()
        savePicture()
        awaitRenderedPicture()
    }

    @Test fun corruptCacheCanRecoverOnTheNextCapture() {
        savePicture()
        val file = checkNotNull(ChannelFrameStore.fileFor(context, channel))
        file.writeText("not a jpeg"); file.setLastModified(System.currentTimeMillis() - 2000)
        assertNull(runBlocking { ChannelFrameStore.validFileFor(context, channel) })
        card()
        compose.waitUntil(10000) {
            compose.onAllNodesWithTag("loading-channel-frame", useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithTag("channel-card-logo", useUnmergedTree = true).assertExists()
        savePicture()
        awaitRenderedPicture()
    }
}