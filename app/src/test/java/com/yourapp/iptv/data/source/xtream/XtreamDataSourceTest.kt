package com.yourapp.iptv.data.source.xtream

import com.yourapp.iptv.domain.model.PlaylistLoadException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.RecordedRequest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class XtreamDataSourceTest {
    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient
    private val active = """{"user_info":{"auth":1,"status":"Active","allowed_output_formats":["ts","m3u8"]}}"""
    private val categories = """[{"category_id":"7","category_name":"News"}]"""
    private val streams = """[{"stream_id":123,"name":"Test","stream_icon":"icons/test.png","category_id":7}]"""

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

    @Test fun authenticatesThenLoadsNamedCategoriesAndPlayableChannels() = runBlocking {
        success()
        val channel = fetch().single()
        assertEquals("News", channel.categoryName)
        assertEquals("Test", channel.name)
        assertEquals(server.url("/live/user/password/123.m3u8").toString(), channel.streamUrl)
        assertEquals(server.url("/icons/test.png").toString(), channel.icon)
        assertEquals("123", channel.streamId)
        assertTrue(channel.channelId.startsWith("xtream_"))
        val requests = (1..3).map { server.takeRequest().requestUrl!! }
        assertTrue(requests.all { it.encodedPath == "/player_api.php" })
        assertEquals(null, requests.first().queryParameter("action"))
        assertEquals(setOf("get_live_categories", "get_live_streams"), requests.drop(1).map { it.queryParameter("action") }.toSet())
    }

    @Test fun reservedCredentialCharactersAndServerSubdirectoryAreEncodedCorrectly() = runBlocking {
        success()
        val username = "u+ &"
        val password = " p/a?+&#% "
        val channel = XtreamDataSource(client).fetchChannels(
            server.url("/panel/player_api.php?old=discard#fragment").toString(), username, password
        ).single()
        repeat(3) {
            val request = server.takeRequest().requestUrl!!
            assertEquals("/panel/player_api.php", request.encodedPath)
            assertEquals(username, request.queryParameter("username"))
            assertEquals(password, request.queryParameter("password"))
            assertEquals(null, request.queryParameter("old"))
        }
        val stream = channel.streamUrl.toHttpUrl()
        assertEquals(listOf("panel", "live", username, password, "123.m3u8"), stream.pathSegments)
    }

    @Test fun rejectedLoginStopsBeforeCategoriesAndDoesNotExposeCredentials() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"user_info":{"auth":0}}"""))
        val error = runCatching { fetch() }.exceptionOrNull()
        assertTrue(error is PlaylistLoadException)
        assertEquals(1, server.requestCount)
        assertFalse(error.toString().contains("password"))
        assertFalse(error.toString().contains(server.hostName + ":" + server.port))
    }

    @Test fun expiredAndDisabledAccountsHaveSpecificErrors() = runBlocking {
        for ((status, expected) in listOf("Expired" to "منتهي", "Disabled" to "معطّل", "Banned" to "موقوف")) {
            server.enqueue(MockResponse().setBody("""{"user_info":{"auth":1,"status":"$status"}}"""))
            val error = runCatching { fetch() }.exceptionOrNull()
            assertTrue(error is PlaylistLoadException)
            assertTrue(error?.message.orEmpty().contains(expected))
        }
        assertEquals(3, server.requestCount)
    }

    @Test fun tsOnlyAccountsUseTsAndStringAuthIsAccepted() = runBlocking {
        success("""{"user_info":{"auth":"1","status":"Active","allowed_output_formats":["ts"]}}""")
        assertTrue(fetch().single().streamUrl.endsWith("/123.ts"))
    }

    @Test fun unsupportedOutputFormatFailsBeforeLoadingChannels() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"user_info":{"auth":1,"status":"Active","allowed_output_formats":["rtmp"]}}"""))
        assertTrue(runCatching { fetch() }.exceptionOrNull() is PlaylistLoadException)
        assertEquals(1, server.requestCount)
    }

    @Test fun emptyLiveListIsAnExplicitFailure() = runBlocking {
        success(live = "[]")
        assertTrue(runCatching { fetch() }.exceptionOrNull() is PlaylistLoadException)
    }

    @Test fun duplicateIdsAndMalformedRowsDoNotBreakGridKeys() = runBlocking {
        success(live = """[null,{"stream_id":0},{"stream_id":123,"name":"First"},
            {"stream_id":"123","name":"Duplicate"},{"stream_id":456,"stream_type":"movie"}]""")
        val channels = fetch()
        assertEquals(1, channels.size)
        assertEquals("First", channels.single().name)
        assertEquals("غير مصنّف", channels.single().categoryName)
    }

    @Test fun htmlAndHttpFailuresAreNotImportedAsAnEmptySuccess() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503).setBody("password"))
        val http = runCatching { fetch() }.exceptionOrNull()
        assertTrue(http is PlaylistLoadException)
        assertTrue(http?.message.orEmpty().contains("503"))
        assertFalse(http.toString().contains("password"))
        server.enqueue(MockResponse().setBody("<html>password</html>"))
        assertTrue(runCatching { fetch() }.exceptionOrNull() is PlaylistLoadException)
    }

    @Test fun oversizedUnknownLengthResponseIsBounded() = runBlocking {
        val body = """{"padding":"${"x".repeat(256 * 1024)}","user_info":{"auth":1,"status":"Active"}}"""
        server.enqueue(MockResponse().setChunkedBody(body, 4096))
        val error = runCatching { fetch() }.exceptionOrNull()
        assertTrue(error is PlaylistLoadException)
        assertTrue(error?.message.orEmpty().contains("حجم"))
        assertEquals(1, server.requestCount)
    }

    @Test fun cancellationClosesTheInFlightCall() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val request = async { fetch() }
        withTimeout(5_000) { while (client.dispatcher.runningCallsCount() == 0) delay(10) }
        request.cancelAndJoin()
        withTimeout(5_000) { while (client.dispatcher.runningCallsCount() != 0) delay(10) }
        assertTrue(request.isCancelled)
    }

    @Test fun categoriesAndStreamsOverlapAfterAuthentication() = runBlocking {
        val listsStarted = CountDownLatch(2)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val action = request.requestUrl!!.queryParameter("action")
                if (action == null) return MockResponse().setBody(active)
                listsStarted.countDown()
                if (!listsStarted.await(5, TimeUnit.SECONDS)) return MockResponse().setResponseCode(504)
                return MockResponse().setBody(if (action == "get_live_categories") categories else streams)
            }
        }
        assertEquals("News", withTimeout(10_000) { fetch() }.single().categoryName)
        assertEquals(0L, listsStarted.count)
    }

    @Test fun failureOfOneListCancelsItsSibling() = runBlocking {
        val listsStarted = CountDownLatch(2)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val action = request.requestUrl!!.queryParameter("action")
                if (action == null) return MockResponse().setBody(active)
                listsStarted.countDown()
                listsStarted.await(5, TimeUnit.SECONDS)
                return if (action == "get_live_categories") MockResponse().setBody("invalid")
                    else MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE)
            }
        }
        assertTrue(withTimeout(10_000) { runCatching { fetch() }.exceptionOrNull() } is PlaylistLoadException)
        withTimeout(5_000) { while (client.dispatcher.runningCallsCount() != 0) delay(10) }
    }

    private suspend fun fetch() = XtreamDataSource(client).fetchChannels(server.url("/").toString(), "user", "password")

    private fun success(account: String = active, live: String = streams) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = MockResponse().setBody(
                when (request.requestUrl!!.queryParameter("action")) {
                    "get_live_categories" -> categories
                    "get_live_streams" -> live
                    else -> account
                })
        }
    }
}