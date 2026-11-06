package mnstudio.apps.mnstreamx.data.source.xtream

import mnstudio.apps.mnstreamx.domain.model.*
import mnstudio.apps.mnstreamx.domain.repository.VodRepository
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*

class XtreamVodPlaybackTest {
    private val server = MockWebServer()
    private val client = OkHttpClient()
    private val source = XtreamVodDataSource(client)
    private lateinit var settings: AppSettings
    private val repository = object : VodRepository {
        override fun supports(settings: AppSettings) = source.supports(settings)
        override suspend fun catalog(settings: AppSettings,kind: VodKind) = source.catalog(settings,kind)
        override suspend fun category(settings: AppSettings,kind: VodKind,categoryId: String) = source.category(settings,kind,categoryId)
        override suspend fun details(settings: AppSettings,kind: VodKind,item: VodItem) = source.details(settings,kind,item)
        override fun playbackUrl(settings: AppSettings,kind: VodKind,id: String,extension: String) = source.playbackUrl(settings,kind,id,extension)
    }
    @Before fun setup() {
        server.start()
        settings = AppSettings(xtreamServer = server.url("/provider/").toString(),xtreamUsername = "u+&",
            xtreamPassword = "p/a%",source = PlaylistSource.XTREAM)
    }
    @After fun cleanup() { server.shutdown();client.dispatcher.executorService.shutdown();client.connectionPool.evictAll() }
    private fun get(url: String) = client.newCall(Request.Builder().url(url).build()).execute().use { it.code }
    private suspend fun resolve() = repository.resolvePlayback(settings,VodKind.MOVIE,"123","mp4")

    @Test fun freshInfoCorrectsAStaleMp4RouteThatTheProviderRejectsWith400() = runBlocking {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.requestUrl!!.queryParameter("action") == "get_vod_info" -> MockResponse().setBody(
                    """{"movie_data":{"stream_id":123,"container_extension":"mkv"}}""")
                request.requestUrl!!.encodedPath.endsWith("123.mkv") -> MockResponse().setBody("synthetic media")
                else -> MockResponse().setResponseCode(400)
            }
        }
        assertEquals(400,get(source.playbackUrl(settings,VodKind.MOVIE,"123","mp4")))
        val result = resolve();assertEquals("mkv",result.extension);assertEquals(200,get(result.url))
        val requests = List(3) { server.takeRequest() }
        assertEquals("get_vod_info",requests[1].requestUrl!!.queryParameter("action"))
        assertEquals("u+&",requests[1].requestUrl!!.queryParameter("username"))
        assertEquals("/provider/movie/u%2B%26/p%2Fa%25/123.mkv",requests[2].requestUrl!!.encodedPath)
    }

    @Test fun providerDirectTargetsAreUsedExactlyWithoutLeakingXtreamCredentials() = runBlocking {
        val direct = server.url("/signed/play?sig=a%2Bb&part=1").toString()
        server.enqueue(MockResponse().setBody("""{"movie_data":{"container_extension":"m3u8","direct_source":"$direct"}}"""))
        server.enqueue(MockResponse().setBody("synthetic media"))
        val result = resolve();assertEquals(direct,result.url);assertEquals(200,get(result.url))
        server.takeRequest();val playback = server.takeRequest()
        assertEquals("/signed/play?sig=a%2Bb&part=1",playback.path)
        assertNull(playback.requestUrl!!.queryParameter("username"));assertNull(playback.requestUrl!!.queryParameter("password"))
    }

    @Test fun relativeDirectTargetsResolveAgainstTheProviderWithoutProbingMedia() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"movie_data":{"direct_source":"/signed/123?sig=x"}}"""))
        assertEquals(server.url("/signed/123?sig=x").toString(),resolve().url)
        assertEquals(1,server.requestCount)
    }

    @Test fun rejectedMovieInfoFallsBackWithoutTryingEveryContainer() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503))
        assertTrue(resolve().url.endsWith("/123.mp4"));assertEquals(1,server.requestCount)
    }
}