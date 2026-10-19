package com.yourapp.iptv.data.source.xtream

import com.yourapp.iptv.domain.model.PlaylistLoadException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class XtreamEpgDataSourceTest {
    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient
    @Before fun setup() { server = MockWebServer(); server.start(); client = OkHttpClient() }
    @After fun cleanup() {
        client.dispatcher.cancelAll()
        client.connectionPool.evictAll()
        client.dispatcher.executorService.shutdown()
        server.shutdown()
    }

    @Test fun loadsAShortScheduleThroughTheCancellableHttpClient() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"epg_listings":[{"title":"News!",
            "start_timestamp":"1700000000","stop_timestamp":"1700003600"}]}"""))
        assertEquals("News!", source().fetch(server.url("/player_api.php")).single().title)
        assertEquals("application/json", server.takeRequest().getHeader("Accept"))
    }

    @Test fun httpAndMalformedPayloadFailuresDoNotLeakUrlsOrCredentials() = runBlocking {
        for (response in listOf(MockResponse().setResponseCode(401).setBody("private-password"),
            MockResponse().setBody("<html>private-password</html>"))) {
            server.enqueue(response)
            val failure = runCatching { source().fetch(server.url("/player_api.php?password=private-password")) }
                .exceptionOrNull()
            assertTrue(failure is PlaylistLoadException)
            assertFalse(failure.toString().contains("private-password"))
        }
    }

    @Test fun chunkedResponsesCannotExceedTheMemoryLimit() = runBlocking {
        server.enqueue(MockResponse().setChunkedBody("{\"padding\":\"${"x".repeat(512 * 1024)}\"}", 4096))
        val failure = runCatching { source().fetch(server.url("/player_api.php")) }.exceptionOrNull()
        assertTrue(failure is PlaylistLoadException)
    }

    @Test fun leavingTheChannelCancelsTheNetworkCall() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val work = async { source().fetch(server.url("/player_api.php")) }
        withTimeout(5_000) { while (client.dispatcher.runningCallsCount() == 0) delay(10) }
        work.cancelAndJoin()
        withTimeout(5_000) { while (client.dispatcher.runningCallsCount() > 0) delay(10) }
        assertTrue(work.isCancelled)
    }

    private fun source() = XtreamEpgDataSource(client)
}