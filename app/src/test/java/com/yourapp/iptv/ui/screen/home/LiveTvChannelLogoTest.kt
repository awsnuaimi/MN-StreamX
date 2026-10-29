package com.yourapp.iptv.ui.screen.home

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.yourapp.iptv.domain.model.HomeAppearance
import com.yourapp.iptv.domain.model.HomeLanguage
import com.yourapp.iptv.ui.state.*
import com.yourapp.iptv.ui.theme.IPTVAppTheme
import kotlinx.coroutines.Dispatchers
import androidx.compose.runtime.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.yourapp.iptv.domain.model.Channel
import com.yourapp.iptv.ui.presentation.LiveChannelRow
import okhttp3.mockwebserver.*
import okio.Buffer
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w960dp-h540dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LiveTvChannelLogoTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val server = MockWebServer()
    private val colors = HomeTvPalettes.forMode(true)
    private var row by mutableStateOf(LiveChannelRow("test", "Test channel", listOf(Channel("hd", "Test HD", streamUrl = "https://synthetic.invalid/live"))))
    private fun png(): ByteArray {
        val bitmap = Bitmap.createBitmap(96, 48, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.rgb(0, 200, 80))
        val out = ByteArrayOutputStream();bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);bitmap.recycle()
        return out.toByteArray()
    }
    @Before fun setup() { server.start() }
    @After fun finish() { server.shutdown() }
    private fun good() { server.enqueue(MockResponse().setHeader("Content-Type", "image/png").setBody(Buffer().write(png()))) }
    private fun setLogo(url: String?) { row = row.copy(variants = listOf(row.variants.first().copy(logoUrl = url))) }
    private fun board(show: Boolean = true, preferred: String? = null) {
        compose.setContent { MaterialTheme { LiveTvChannelLogo(row, preferred, colors, show) } }
    }
    private fun loaded() {
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("live-logo-loaded-test", true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("live-logo-fallback-test", true).assertDoesNotExist()
    }
    @Test fun providerLogoIsActuallyDecodedAndDrawnWithoutAChannelLetter() {
        good();setLogo(server.url("/logo.png").toString());board();loaded()
        compose.onNodeWithText("T").assertDoesNotExist()
        compose.runOnIdle {
            val view = compose.activity.findViewById<View>(android.R.id.content)
            val image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(image)); val pixel = image.getPixel(24, 16)
            assertTrue(android.graphics.Color.green(pixel) > 150); assertTrue(android.graphics.Color.red(pixel) < 60)
            image.recycle()
        }
    }
    @Test fun missingLogosUseATvIconAndNeverLetters() {
        board();compose.onNodeWithTag("live-logo-fallback-test", true).assertExists()
        compose.onNodeWithText("T").assertDoesNotExist();assertEquals(0, server.requestCount)
    }
    @Test fun aFailedLogoKeepsTheIconInsteadOfBreakingTheRow() {
        server.enqueue(MockResponse().setResponseCode(404));setLogo(server.url("/missing.png").toString());board()
        compose.waitUntil(10_000) { server.requestCount == 1 && compose.onAllNodesWithTag("live-logo-loading-test", true).fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("live-logo-fallback-test", true).assertExists()
    }
    @Test fun disablingLogosDoesNotStartANetworkRequest() {
        setLogo(server.url("/not-requested.png").toString());board(show = false)
        compose.onNodeWithTag("live-logo-fallback-test", true).assertExists();assertEquals(0, server.requestCount)
    }
    @Test fun qualityFallbackUsesOnlyTheSameChannelFamily() {
        server.enqueue(MockResponse().setResponseCode(404));good()
        val base = row.variants.first()
        row = row.copy(variants = listOf(base.copy(id = "fhd", logoUrl = server.url("/broken.png").toString()),
            base.copy(id = "hd", logoUrl = server.url("/valid.png").toString())))
        board(preferred = "fhd");loaded();assertEquals(2, server.requestCount)
    }
    @Test fun aLargeCatalogLoadsOnlyVisibleLogosAndTheNextVisibleWindow() {
        val bytes = png()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = MockResponse()
                .setHeader("Content-Type", "image/png").setBody(Buffer().write(bytes))
        }
        val catalog = (0 until 200).map { Channel("$it", "Synthetic $it HD", logoUrl = server.url("/$it.png").toString(),
            streamUrl = "https://synthetic.invalid/$it") }
        val state = HomeUiState(channels = catalog, visibleChannels = catalog, isLoading = false, browseScope = "scope")
        compose.setContent { IPTVAppTheme {
            LiveTvBrowser(state, HomeAppearance(language = HomeLanguage.GERMAN), HomeLabels(HomeLanguage.GERMAN),
                HomeGuideState(), 1, CaptionUiState(), {}, {}, {}, {}, {}, true, { _, _ -> }, {}, {},
                Modifier.fillMaxSize(), projectionDispatcher = Dispatchers.Main.immediate)
        } }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("live-logo-loaded-0", true).fetchSemanticsNodes().isNotEmpty() }
        assertTrue("Offscreen catalog must stay unloaded", server.requestCount in 1..12)
        compose.onNodeWithTag("live-channel-list").performScrollToIndex(100)
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("live-logo-loaded-100", true).fetchSemanticsNodes().isNotEmpty() }
        assertTrue("Only two visible windows may request images", server.requestCount <= 28)
    }

    @Test fun aNewLogoUrlCanRecoverFromAMissingLogo() {
        board();compose.onNodeWithTag("live-logo-fallback-test", true).assertExists()
        good();compose.runOnIdle { setLogo(server.url("/changed.png").toString()) };loaded()
    }
}