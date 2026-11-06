package mnstudio.apps.mnstreamx.data.image

import android.app.Application
import android.content.ComponentCallbacks2
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import coil.ImageLoader
import coil.decode.DataSource
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.request.ErrorResult
import coil.size.SizeResolver
import mnstudio.apps.mnstreamx.domain.image.ImageLoadRecorder
import kotlinx.coroutines.*
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalCoroutinesApi::class)
class AppImageLoaderTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private val server = MockWebServer()
    private lateinit var loader: ImageLoader
    private val recorder = ImageLoadRecorder()
    private val green = android.graphics.Color.rgb(0, 190, 60)
    private val red = android.graphics.Color.rgb(200, 20, 20)

    @Before fun setup() {
        // execute() switches to Main; plain JVM network tests must not block Android's looper.
        Dispatchers.setMain(UnconfinedTestDispatcher())
        server.start(); loader = AppImageLoader.create(app, recorder)
    }
    @After fun cleanup() {
        try { loader.shutdown(); server.shutdown() } finally { Dispatchers.resetMain() }
    }
    private fun png(color: Int = green): ByteArray {
        val bitmap = Bitmap.createBitmap(96, 48, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(color)
        return ByteArrayOutputStream().use { output ->
            try { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)); output.toByteArray() }
            finally { bitmap.recycle() }
        }
    }
    private fun response(bytes: ByteArray = png(), cache: String = "public, max-age=3600") =
        MockResponse().setHeader("Content-Type", "image/png").setHeader("Cache-Control", cache)
            .setBody(Buffer().write(bytes))
    private fun request(path: String, memory: CachePolicy = CachePolicy.ENABLED) = ImageRequest.Builder(app)
        .data(server.url(path)).size(96, 48).allowHardware(false).memoryCachePolicy(memory).build()
    private suspend fun load(path: String, memory: CachePolicy = CachePolicy.ENABLED): SuccessResult {
        val result = withTimeout(15_000) { loader.execute(request(path, memory)) }
        assertTrue("An image must really decode: $result", result is SuccessResult)
        return result as SuccessResult
    }

    @Test fun returningToTheSameImageUsesMemoryThenDiskWithoutAnotherHttpRequest() = runBlocking {
        server.enqueue(response())
        assertEquals(DataSource.NETWORK, load("/poster.png").dataSource)
        assertEquals(DataSource.MEMORY_CACHE, load("/poster.png").dataSource)
        loader.memoryCache!!.clear()
        assertEquals(DataSource.DISK, load("/poster.png").dataSource)
        assertEquals(1, server.requestCount)
        val metrics = recorder.snapshot()
        assertEquals(3L, metrics.started); assertEquals(0L, metrics.inFlight)
        assertEquals(1L, metrics.network); assertEquals(1L, metrics.memory); assertEquals(1L, metrics.disk)
    }
    @Test fun nullDataAndInvalidImagesAreFailuresWithoutRetainingRequestDetails() = runBlocking {
        assertTrue(loader.execute(ImageRequest.Builder(app).data(null).build()) is ErrorResult)
        server.enqueue(MockResponse().setHeader("Content-Type", "image/png").setBody("synthetic invalid image"))
        assertTrue(loader.execute(request("/synthetic-invalid.png")) is ErrorResult)
        val metrics = recorder.snapshot()
        assertEquals(2L, metrics.failed); assertEquals(0L, metrics.inFlight)
        assertNull(metrics.p95Ms)
        assertFalse(metrics.toString().contains("synthetic-invalid"))
    }
    @Test fun cancellationWhileWaitingForImageSizeReleasesThePendingMetricWithoutAnHttpCall() = runBlocking {
        val resolving = CompletableDeferred<Unit>()
        val image = request("/never-fetched.png").newBuilder().size(SizeResolver {
            resolving.complete(Unit); awaitCancellation()
        }).build()
        val task = launch(start = CoroutineStart.UNDISPATCHED) { loader.execute(image) }
        withTimeout(5000) { resolving.await() }
        assertEquals(1L, recorder.snapshot().inFlight)
        task.cancelAndJoin()
        assertEquals(1L, recorder.snapshot().cancelled)
        assertEquals(0L, recorder.snapshot().inFlight); assertEquals(0, server.requestCount)
    }
    @Test fun distinctQueryUrlsKeepTheirOwnArtwork() = runBlocking {
        server.enqueue(response(png(green))); server.enqueue(response(png(red)))
        val first = load("/poster.png?variant=a")
        val second = load("/poster.png?variant=b")
        assertEquals(green, (first.drawable as BitmapDrawable).bitmap.getPixel(0, 0))
        assertEquals(red, (second.drawable as BitmapDrawable).bitmap.getPixel(0, 0))
        assertEquals(DataSource.MEMORY_CACHE, load("/poster.png?variant=a").dataSource)
        assertEquals(2, server.requestCount)
    }
    @Test fun aProviderNoStoreResponseIsNotReusedFromDisk() = runBlocking {
        server.enqueue(response(cache = "no-store")); server.enqueue(response(cache = "no-store"))
        assertEquals(DataSource.NETWORK, load("/private.png", CachePolicy.DISABLED).dataSource)
        assertEquals(DataSource.NETWORK, load("/private.png", CachePolicy.DISABLED).dataSource)
        assertEquals(2, server.requestCount)
    }
    @Suppress("DEPRECATION") // Legacy trim events remain relevant to the supported Android 10+ devices.
    @Test fun androidMemoryPressureReleasesTheSharedMemoryCache() = runBlocking {
        server.enqueue(response())
        val image = load("/memory.png")
        assertTrue(loader.memoryCache!!.size > 0)
        app.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_COMPLETE)
        assertEquals(0, loader.memoryCache!!.size)
        // A visible drawable remains valid even when its cached reference is released.
        assertEquals(green, (image.drawable as BitmapDrawable).bitmap.getPixel(0, 0))
    }
    @Test fun visibleImageBurstsRespectTheDeviceHostLimitAndDrainTheirQueue() = runBlocking {
        val bytes = png()
        val active = AtomicInteger()
        val highest = AtomicInteger()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val count = active.incrementAndGet()
                highest.accumulateAndGet(count, ::maxOf)
                try { Thread.sleep(80); return response(bytes) }
                finally { active.decrementAndGet() }
            }
        }
        val manager = checkNotNull(app.getSystemService(android.app.ActivityManager::class.java))
        val budget = ImageCacheBudget.forDevice(manager.memoryClass, manager.isLowRamDevice)
        withTimeout(20_000) { (0 until 12).map { i -> async { load("/burst-$i.png") } }.awaitAll() }
        assertEquals(12, server.requestCount)
        assertTrue("Actual simultaneous requests exceeded the host budget", highest.get() <= budget.maxRequestsPerHost)
        assertTrue("The image loader should still load concurrently", highest.get() > 1)
        assertEquals(0, active.get())
    }
}