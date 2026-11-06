package mnstudio.apps.mnstreamx.ui.image

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import coil.Coil
import coil.compose.AsyncImage
import coil.decode.DataSource
import coil.request.ImageRequest
import mnstudio.apps.mnstreamx.IptvApplication
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = IptvApplication::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ImageReturnUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val server = MockWebServer()
    private var visible by mutableStateOf(true)
    private var phase by mutableIntStateOf(0)
    private val loaded = CopyOnWriteArrayList<Pair<Int, DataSource>>()
    private val errors = CopyOnWriteArrayList<Throwable>()
    @Before fun setup() { server.start() }
    @After fun cleanup() {
        compose.runOnIdle { visible = false }
        Coil.imageLoader(compose.activity).shutdown()
        server.shutdown()
    }
    private fun png(): ByteArray {
        val image = Bitmap.createBitmap(96, 48, Bitmap.Config.ARGB_8888)
        return ByteArrayOutputStream().use { out ->
            try { image.eraseColor(android.graphics.Color.GREEN)
                check(image.compress(Bitmap.CompressFormat.PNG, 100, out)); out.toByteArray() }
            finally { image.recycle() }
        }
    }
    @Test fun leavingAndRecreatingArtworkReusesTheApplicationCaches() {
        server.enqueue(MockResponse().setHeader("Content-Type", "image/png")
            .setHeader("Cache-Control", "public, max-age=3600").setBody(Buffer().write(png())))
        val url = server.url("/artwork.png").toString()
        compose.setContent {
            MaterialTheme {
                if (visible) {
                    val context = LocalContext.current
                    val currentPhase = phase
                    val request = remember(context, url) { ImageRequest.Builder(context).data(url)
                        .size(256, 350).allowHardware(false).crossfade(false).build() }
                    AsyncImage(request, "Artwork", Modifier.size(128.dp, 175.dp),
                        onSuccess = { loaded.add(currentPhase to it.result.dataSource) },
                        onError = { errors.add(it.result.throwable) })
                }
            }
        }
        awaitImage(0, DataSource.NETWORK)
        compose.runOnIdle { visible = false }
        compose.onNodeWithContentDescription("Artwork").assertDoesNotExist()
        compose.runOnIdle { phase = 1; visible = true }
        awaitImage(1, DataSource.MEMORY_CACHE)
        compose.runOnIdle { visible = false }
        compose.onNodeWithContentDescription("Artwork").assertDoesNotExist()
        compose.runOnIdle { Coil.imageLoader(compose.activity).memoryCache!!.clear(); phase = 2; visible = true }
        awaitImage(2, DataSource.DISK)
        assertEquals(1, server.requestCount)
    }
    private fun awaitImage(expectedPhase: Int, source: DataSource) {
        try {
            compose.waitUntil(10_000) {
                // Synchronize Android's main looper as well as Compose's virtual frame clock.
                // Coil posts completion to Main after the background fetch/decode finishes.
                compose.onAllNodesWithContentDescription("Artwork").fetchSemanticsNodes()
                loaded.any { it.first == expectedPhase } || errors.isNotEmpty()
            }
        } catch (error: Throwable) {
            throw AssertionError("No image result; phase=$expectedPhase loaded=$loaded errors=$errors HTTP=${server.requestCount}", error)
        }
        assertTrue("Decode errors: $errors", errors.isEmpty())
        assertEquals(source, loaded.last { it.first == expectedPhase }.second)
    }

}