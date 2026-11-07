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
    private fun read(server: MockWebServer, path: String, trace: LiveStartupTrace): String {
        val source = LiveMediaSourceFactory.dataSource(context,trace).createDataSource()
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