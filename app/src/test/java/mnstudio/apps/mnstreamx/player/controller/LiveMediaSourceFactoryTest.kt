package mnstudio.apps.mnstreamx.player.controller

import android.app.Application
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import mnstudio.apps.mnstreamx.data.network.HttpClient
import mnstudio.apps.mnstreamx.domain.performance.*
import okhttp3.*
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.IOException
import java.util.concurrent.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34],application = Application::class)
@androidx.annotation.OptIn(UnstableApi::class)
class LiveMediaSourceFactoryTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun read(
        server: MockWebServer,
        path: String,
        trace: LiveStartupTrace,
        requests: LivePlaybackRequests = LivePlaybackRequests()
    ): String {
        val source = LiveMediaSourceFactory.dataSource(context, trace, requests).createDataSource()
        try {
            source.open(DataSpec(Uri.parse(server.url(path).toString())))
            val bytes = ByteArray(64);val output = java.io.ByteArrayOutputStream()
            while (true) {
                val count = source.read(bytes,0,bytes.size)
                if (count == -1) break
                output.write(bytes,0,count)
            }
            return output.toString("UTF-8")
        } finally { source.close() }
    }

    @Test fun liveUsesTheSharedPoolAndKeepsBroadcastCallsUnlimitedWithoutMutatingApiTimeouts() {
        val api = HttpClient.create();val live = LiveMediaSourceFactory.client
        assertSame(api.connectionPool,live.connectionPool)
        assertNotSame(api.dispatcher,live.dispatcher)
        assertEquals(0,live.callTimeoutMillis)
        assertEquals(8_000,live.connectTimeoutMillis);assertEquals(8_000,live.readTimeoutMillis)
        assertEquals(90_000,api.callTimeoutMillis);assertEquals(20_000,api.connectTimeoutMillis)
        assertFalse(live.followSslRedirects)
    }

    @Test fun successfulTransfersReuseConnectionsAndRecordResponseAndFirstBytesSeparately() {
        val server = MockWebServer();server.start()
        val oldAgent = System.getProperty("http.agent")
        System.setProperty("http.agent","Synthetic-TV-Agent")
        try {
            server.enqueue(MockResponse().setBody("first"));server.enqueue(MockResponse().setBody("second"))
            val recorder = PerformanceRecorder()
            val first = LiveStartupTrace(recorder)
            assertEquals("first",read(server,"/one",first));first.close()
            val next = LiveStartupTrace(recorder)
            assertEquals("second",read(server,"/two",next));next.close()
            val a = server.takeRequest();val b = server.takeRequest()
            assertEquals(0,a.sequenceNumber);assertEquals(1,b.sequenceNumber)
            assertEquals("Synthetic-TV-Agent",b.getHeader("User-Agent"))
            assertEquals("GET",a.method);assertEquals(2,server.requestCount)
            val rows = recorder.snapshot().associateBy { it.stage }
            assertEquals(2,rows.getValue(PerformanceStage.LIVE_HTTP_OPEN).complete)
            assertEquals(2,rows.getValue(PerformanceStage.LIVE_FIRST_BYTE).complete)
            assertEquals(0,rows.getValue(PerformanceStage.LIVE_PLAYER_READY).complete)
        } finally {
            if (oldAgent == null) System.clearProperty("http.agent") else System.setProperty("http.agent",oldAgent)
            server.shutdown()
        }
    }

    @Test fun aBusyApiDispatcherCannotHoldTheSelectedLiveStreamInItsQueue() {
        val server = MockWebServer();server.start()
        val api = HttpClient.create();val previousLimit = api.dispatcher.maxRequestsPerHost
        val executor = Executors.newSingleThreadExecutor()
        api.dispatcher.maxRequestsPerHost = 1
        server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: RecordedRequest) = if (request.path == "/api")
                MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE) else MockResponse().setBody("live")
        }
        val call = api.newCall(Request.Builder().url(server.url("/api")).build())
        try {
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) = Unit
                override fun onResponse(call: Call, response: Response) { response.close() }
            })
            assertEquals("/api",server.takeRequest(5,TimeUnit.SECONDS)?.path)
            val result = executor.submit(Callable { read(server,"/live",LiveStartupTrace()) })
            assertEquals("live",result.get(5,TimeUnit.SECONDS))
            assertFalse(call.isCanceled())
        } finally {
            call.cancel();api.dispatcher.maxRequestsPerHost = previousLimit
            executor.shutdownNow();server.shutdown()
        }
    }

    @Test fun retiredPlaybackInterruptsAnOpenResponseBodyWithoutTouchingTheNewAttempt() {
        val executor = Executors.newSingleThreadExecutor()
        val opened = CountDownLatch(1)
        val retired = LivePlaybackRequests()
        val current = LivePlaybackRequests()
        try {
            MockWebServer().use { server ->
                server.enqueue(MockResponse().setBody("ab").throttleBody(1, 4, TimeUnit.SECONDS))
                val transfer = executor.submit(Callable {
                    val source = LiveMediaSourceFactory.dataSource(context, LiveStartupTrace(), retired).createDataSource()
                    try {
                        source.open(DataSpec(Uri.parse(server.url("/old").toString())))
                        val bytes = ByteArray(1)
                        assertEquals(1, source.read(bytes, 0, 1))
                        opened.countDown()
                        source.read(bytes, 0, 1)
                    } finally {
                        source.close()
                    }
                })
                assertTrue("The old response should have started delivering its body", opened.await(5, TimeUnit.SECONDS))

                retired.cancel()
                retired.cancel()
                assertTrue(assertThrows(ExecutionException::class.java) {
                    transfer.get(3, TimeUnit.SECONDS)
                }.cause is IOException)

                server.enqueue(MockResponse().setBody("new live stream"))
                assertEquals("new live stream", read(server, "/new", LiveStartupTrace(), current))
                assertEquals("/old", server.takeRequest(1, TimeUnit.SECONDS)?.path)
                assertEquals("/new", server.takeRequest(1, TimeUnit.SECONDS)?.path)
            }
        } finally {
            retired.cancel()
            current.cancel()
            executor.shutdownNow()
        }
    }

    @Test fun retiringTheSourceCancelsCallsWaitingForHeadersButLeavesTheApiCallActive() {
        val executor = Executors.newSingleThreadExecutor()
        val requests = LivePlaybackRequests()
        try {
            MockWebServer().use { server ->
                server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
                val apiCall = HttpClient.create().newCall(Request.Builder().url(server.url("/api-held")).build())
                apiCall.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) = Unit
                    override fun onResponse(call: Call, response: Response) { response.close() }
                })
                try {
                    assertEquals("/api-held", server.takeRequest(5, TimeUnit.SECONDS)?.path)
                    server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
                    val old = executor.submit(Callable { read(server, "/live-held", LiveStartupTrace(), requests) })
                    assertEquals("/live-held", server.takeRequest(5, TimeUnit.SECONDS)?.path)
                    requests.cancel()
                    assertTrue(assertThrows(ExecutionException::class.java) {
                        old.get(3, TimeUnit.SECONDS)
                    }.cause is IOException)
                    assertFalse(apiCall.isCanceled())
                } finally {
                    apiCall.cancel()
                }
            }
        } finally {
            requests.cancel()
            executor.shutdownNow()
        }
    }

    @Test fun aLoaderFromARetiredSourceCannotOpenAnotherHttpConnection() {
        MockWebServer().use { server ->
            val requests = LivePlaybackRequests()
            val prepared = requests.client.newCall(Request.Builder().url(server.url("/prepared")).build())
            requests.cancel()

            assertThrows(IOException::class.java) { prepared.execute().close() }
            assertThrows(IOException::class.java) { read(server, "/late", LiveStartupTrace(), requests) }
            assertTrue(prepared.isCanceled())
            assertEquals(0, server.requestCount)
        }
    }

    @Test fun retiringTheSourceLeavesItsCompletedCallsAndIdleConnectionsAlone() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("first"))
            val requests = LivePlaybackRequests()
            val finished = requests.client.newCall(Request.Builder().url(server.url("/first")).build())
            finished.execute().use { assertEquals("first", it.body?.string()) }
            requests.cancel()
            assertFalse(finished.isCanceled())

            server.enqueue(MockResponse().setBody("second"))
            assertEquals("second", read(server, "/second", LiveStartupTrace()))
            assertEquals(0, server.takeRequest(1, TimeUnit.SECONDS)?.sequenceNumber)
            assertEquals(1, server.takeRequest(1, TimeUnit.SECONDS)?.sequenceNumber)
        }
    }

    @Test fun retiringAnAttemptPreventsLateHttpCallbacksFromBecomingSuccessfulMilestones() {
        val server = MockWebServer();server.start()
        try {
            server.enqueue(MockResponse().setBody("late"))
            val recorder = PerformanceRecorder();val old = LiveStartupTrace(recorder);old.close()
            assertEquals("late",read(server,"/late",old))
            assertTrue(recorder.snapshot().all { it.complete == 0 && it.cancelled == 1 })
        } finally { server.shutdown() }
    }
}