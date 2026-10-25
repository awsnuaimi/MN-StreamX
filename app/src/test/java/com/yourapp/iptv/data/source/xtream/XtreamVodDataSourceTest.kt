package com.yourapp.iptv.data.source.xtream

import com.yourapp.iptv.domain.model.*
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class XtreamVodDataSourceTest {
    private lateinit var server: MockWebServer
    private lateinit var settings: AppSettings
    private lateinit var source: XtreamVodDataSource
    private val categories = """[{"category_id":"7","category_name":"Movies"}]"""
    private val movie = """[{"stream_id":123,"name":"One","category_ids":["7","8"]}]"""
    @Before fun setup() {
        server = MockWebServer(); server.start()
        settings = AppSettings(xtreamServer = server.url("/provider/").toString(),
            xtreamUsername = "synthetic user", xtreamPassword = "synthetic&password", source = PlaylistSource.XTREAM)
        source = XtreamVodDataSource(OkHttpClient())
    }
    @After fun cleanup() { server.shutdown() }

    private fun responses(block: (RecordedRequest) -> MockResponse) {
        server.dispatcher = object : Dispatcher() { override fun dispatch(request: RecordedRequest) = block(request) }
    }
    private fun json(body: String) = MockResponse().setBody(body).addHeader("Content-Type", "application/json")

    @Test fun failedCategoryMetadataDoesNotDiscardAValidMovieList() = runBlocking {
        responses { if (it.requestUrl!!.queryParameter("action") == "get_vod_categories")
            MockResponse().setResponseCode(500) else json(movie) }
        val result = source.catalog(settings, VodKind.MOVIE)
        assertEquals("One", result.items.single().name)
        assertTrue(result.categoriesUnavailable)
        assertEquals(listOf("7", "8"), result.categories.map { it.id })
        assertFalse(result.categoryScoped)
    }

    @Test fun aRejectedBulkListLoadsOnlyTheSelectedCategoryOnDemand() = runBlocking {
        responses {
            val url = it.requestUrl!!
            when {
                url.queryParameter("action") == "get_vod_categories" -> json(categories)
                url.queryParameter("category_id") != null -> json("""[{"stream_id":9,"name":"Scoped film"}]""")
                else -> MockResponse().setResponseCode(413)
            }
        }
        val result = source.catalog(settings, VodKind.MOVIE)
        assertTrue(result.categoryScoped)
        assertTrue(result.items.isEmpty())
        assertEquals(2, server.requestCount)
        val items = source.category(settings, VodKind.MOVIE, result.categories.single().id)
        assertEquals("Scoped film", items.single().name)
        assertTrue(items.single().belongsTo("7"))
        val requests = List(3) { server.takeRequest() }
        val categoryUrl = requests.single { it.requestUrl!!.queryParameter("category_id") == "7" }.requestUrl!!
        assertEquals("/provider/player_api.php", categoryUrl.encodedPath)
        assertEquals("synthetic user", categoryUrl.queryParameter("username"))
        assertEquals("synthetic&password", categoryUrl.queryParameter("password"))
    }

    @Test fun oversizedBulkCatalogUsesCategoryModeWithoutRaisingTheMemoryLimit() = runBlocking {
        source = XtreamVodDataSource(OkHttpClient(), catalogByteLimit = 128)
        responses { if (it.requestUrl!!.queryParameter("action") == "get_vod_categories") json(categories)
            else json(" ".repeat(200) + movie) }
        assertTrue(source.catalog(settings, VodKind.MOVIE).categoryScoped)
        assertEquals(2, server.requestCount)
    }

    @Test fun anEmptyBulkListWithCategoriesAlsoEnablesCategoryRequests() = runBlocking {
        responses { if (it.requestUrl!!.queryParameter("action")!!.endsWith("_categories")) json(categories) else json("[]") }
        for (kind in VodKind.entries) assertTrue(source.catalog(settings, kind).categoryScoped)
    }

    @Test fun serverErrorObjectsAreNotPresentedAsAnEmptySubscription() = runBlocking {
        responses { if (it.requestUrl!!.queryParameter("action") == "get_vod_categories") json("[]")
            else json("""{"user_info":{"auth":0},"message":"Denied"}""") }
        assertTrue(runCatching { source.catalog(settings, VodKind.MOVIE) }.exceptionOrNull() is PlaylistLoadException)
    }

    @Test fun aGenuinelyEmptyCatalogRemainsEmptyWithoutExtraRequests() = runBlocking {
        responses { json("[]") }
        val result = source.catalog(settings, VodKind.MOVIE)
        assertTrue(result.items.isEmpty()); assertFalse(result.categoryScoped)
        assertEquals(2, server.requestCount)
    }

    @Test fun providerM3uCredentialsWorkForCatalogAndPlaybackWithoutChangingSavedSource() = runBlocking {
        val link = server.url("/provider/get.php").newBuilder().addQueryParameter("username", "u s")
            .addQueryParameter("password", "p&x").addQueryParameter("type", "m3u_plus").build()
        val m3u = AppSettings(m3uUrl = link.toString(), source = PlaylistSource.M3U)
        responses { if (it.requestUrl!!.queryParameter("action") == "get_vod_categories") json(categories) else json(movie) }
        assertTrue(source.supports(m3u))
        assertEquals("123", source.catalog(m3u, VodKind.MOVIE).items.single().id)
        val playback = source.playbackUrl(m3u, VodKind.MOVIE, "123", "mkv").toHttpUrl()
        assertEquals(listOf("provider", "movie", "u s", "p&x", "123.mkv"), playback.pathSegments)
        assertEquals(PlaylistSource.M3U, m3u.source)
        assertFalse(source.supports(AppSettings(m3uUrl = server.url("/generic/list.m3u").toString())))
    }

    @Test fun malformedOrAmbiguousM3uCredentialsAreNotGuessed() {
        for (suffix in listOf("get.php?username=u", "get.php?username=u&password=p&username=x",
            "list.m3u?username=u&password=p")) {
            assertNull(XtreamVodAccount.resolve(AppSettings(m3uUrl = server.url("/$suffix").toString())))
        }
    }
}