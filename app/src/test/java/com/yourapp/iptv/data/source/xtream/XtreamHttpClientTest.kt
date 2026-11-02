package com.yourapp.iptv.data.source.xtream

import com.yourapp.iptv.data.diagnostics.DiagnosticFailures
import com.yourapp.iptv.domain.diagnostics.DiagnosticIssue
import com.yourapp.iptv.domain.model.PlaylistLoadException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class XtreamHttpClientTest {
    private val server = MockWebServer()

    @Before fun start() { server.start() }
    @After fun stop() { server.shutdown() }

    private suspend fun read(limit: Int, client: OkHttpClient = OkHttpClient()): String =
        XtreamHttpClient(client).read(server.url("/player_api.php"), limit) { reader, _ ->
            reader.readText()
        }

    @Test fun utf8ByteLimitIsNotMistakenForCharacterLimit() = runBlocking {
        val json = "\"éé\"" // Four characters, six UTF-8 bytes.
        server.enqueue(MockResponse().setBody(json))
        val error = try { read(5); null } catch (e: PlaylistLoadException) { e }
        assertNotNull(error)
        assertEquals(DiagnosticIssue.LIMIT, DiagnosticFailures.classify(error!!).first)
        server.enqueue(MockResponse().setBody(json))
        assertEquals(json, read(6))
    }

    @Test fun chunkedResponseWithoutContentLengthStillEnforcesByteLimit() = runBlocking {
        server.enqueue(MockResponse().setChunkedBody("é".repeat(10), 3))
        val error = try { read(19); null } catch (e: PlaylistLoadException) { e }
        assertNotNull(error)
        assertEquals(DiagnosticIssue.LIMIT, DiagnosticFailures.classify(error!!).first)
    }

    @Test fun jsonWithUtf8BomKeepsExistingOkHttpCharsetBehavior() = runBlocking {
        val payload = "\uFEFF{\"name\":\"ä\"}"
        server.enqueue(MockResponse().setBody(okio.Buffer().writeUtf8(payload)))
        assertEquals("{\"name\":\"ä\"}", read(payload.toByteArray(Charsets.UTF_8).size))
    }

    @Test fun slowParserDoesNotBlockOkHttpDispatcherFromServingAnotherRequest() = runBlocking {
        val dispatcher = Dispatcher().apply {
            maxRequests = 1
            maxRequestsPerHost = 1
        }
        val client = OkHttpClient.Builder().dispatcher(dispatcher).build()
        val parsing = CountDownLatch(1)
        val release = CountDownLatch(1)
        server.enqueue(MockResponse().setBody("first"))
        server.enqueue(MockResponse().setBody("second"))
        val first = async(Dispatchers.Default) {
            XtreamHttpClient(client).read(server.url("/first"), 100) { reader, _ ->
                parsing.countDown()
                check(release.await(5, TimeUnit.SECONDS))
                reader.readText()
            }
        }
        try {
            assertTrue(withContext(Dispatchers.IO) { parsing.await(5, TimeUnit.SECONDS) })
            val second = async(Dispatchers.Default) { read(100, client) }
            assertEquals("second", withTimeout(4000) { second.await() })
        } finally {
            release.countDown()
        }
        assertEquals("first", withTimeout(4000) { first.await() })
    }

    @Test fun cancellingDuringParsingStopsTheActiveReader() = runBlocking {
        server.enqueue(MockResponse().setBody("still open"))
        val started = CountDownLatch(1)
        val stopped = CountDownLatch(1)
        val job = async(Dispatchers.Default) {
            XtreamHttpClient(OkHttpClient()).read(server.url("/slow"), 100) { _, active ->
                started.countDown()
                try {
                    while (active()) Thread.sleep(5)
                } finally {
                    stopped.countDown()
                }
            }
        }
        assertTrue(withContext(Dispatchers.IO) { started.await(5, TimeUnit.SECONDS) })
        job.cancelAndJoin()
        assertTrue(withContext(Dispatchers.IO) { stopped.await(5, TimeUnit.SECONDS) })
    }
}