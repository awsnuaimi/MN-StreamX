package com.yourapp.iptv.data.source.m3u

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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class M3uDataSourceTest {
    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
        client = OkHttpClient()
    }

    @After fun tearDown() {
        client.dispatcher.cancelAll()
        client.connectionPool.evictAll()
        client.dispatcher.executorService.shutdown()
        server.shutdown()
    }

    @Test fun redirectedPlaylistResolvesRelativeStreamAgainstFinalUrl() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", "/folder/list.m3u"))
        server.enqueue(MockResponse().setBody("#EXTM3U\n#EXTINF:-1,Test\nstream.ts"))
        val channels = M3uDataSource(client).fetchChannels(server.url("/start").toString())
        assertEquals(server.url("/folder/stream.ts").toString(), channels.single().url)
    }

    @Test fun httpFailureDoesNotBecomeAnEmptySuccessfulPlaylist() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("Denied"))
        val error = runCatching { M3uDataSource(client).fetchChannels(server.url("/list").toString()) }.exceptionOrNull()
        assertTrue(error is PlaylistLoadException)
        assertTrue(error?.message.orEmpty().contains("401"))
    }

    @Test fun cancellingCoroutineCancelsNetworkCall() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val operation = async { M3uDataSource(client).fetchChannels(server.url("/wait").toString()) }
        withTimeout(5_000) {
            while (client.dispatcher.runningCallsCount() == 0) delay(10)
        }
        operation.cancelAndJoin()
        withTimeout(5_000) {
            while (client.dispatcher.runningCallsCount() != 0) delay(10)
        }
        assertTrue(operation.isCancelled)
    }
}