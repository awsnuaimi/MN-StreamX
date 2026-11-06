package mnstudio.apps.mnstreamx.data.source.xtream

import mnstudio.apps.mnstreamx.domain.model.*
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

class XtreamBrowseCatalogTest {
    private val server = MockWebServer()
    private lateinit var account: AppSettings
    private val categories = """[{"category_id":"7","category_name":"Synthetic category"}]"""
    private val movie = """[{"stream_id":7,"name":"Synthetic film","category_id":"7"}]"""
    private fun json(body: String) = MockResponse().setBody(body).addHeader("Content-Type","application/json")
    @Before fun setup() {
        server.start()
        account = AppSettings(source = PlaylistSource.XTREAM,xtreamServer = server.url("/provider/").toString(),
            xtreamUsername = "synthetic",xtreamPassword = "synthetic")
    }
    @After fun cleanup() { server.shutdown() }
    private fun source() = XtreamVodDataSource(OkHttpClient.Builder().callTimeout(2,java.util.concurrent.TimeUnit.SECONDS).build())

    @Test fun browsingMoviesAndSeriesNeverRequestsTheBulkListWhenCategoriesExist() = runBlocking {
        for (kind in VodKind.entries) {
            server.enqueue(json(categories))
            val result = source().browseCatalog(account,kind)
            assertTrue(result.categoryScoped);assertTrue(result.items.isEmpty())
            assertEquals("7",result.categories.single().id)
            assertTrue(server.takeRequest().requestUrl!!.queryParameter("action")!!.endsWith("_categories"))
        }
        assertEquals(2,server.requestCount)
    }
    @Test fun missingCategoryMetadataStillFallsBackToProviderItems() = runBlocking {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = if (request.requestUrl!!.queryParameter("action")!!.endsWith("_categories"))
                MockResponse().setResponseCode(500) else json(movie)
        }
        val result = source().browseCatalog(account,VodKind.MOVIE)
        assertFalse(result.categoryScoped);assertTrue(result.categoriesUnavailable)
        assertEquals("7",result.items.single().id);assertEquals(2,server.requestCount)
    }
    @Test fun globalSearchKeepsTheFullCatalogContract() = runBlocking {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = if (request.requestUrl!!.queryParameter("action")!!.endsWith("_categories"))
                json(categories) else json(movie)
        }
        val source = source()
        assertEquals("7",source.catalog(account,VodKind.MOVIE).items.single().id)
        assertEquals(2,server.requestCount)
        assertTrue(source.browseCatalog(account,VodKind.MOVIE).categoryScoped)
        assertEquals(3,server.requestCount)
    }
}