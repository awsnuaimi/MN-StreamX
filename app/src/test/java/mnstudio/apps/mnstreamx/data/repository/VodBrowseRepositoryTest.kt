package mnstudio.apps.mnstreamx.data.repository

import android.app.Application
import android.content.ContextWrapper
import mnstudio.apps.mnstreamx.domain.model.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.last
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34],application = Application::class)
class VodBrowseRepositoryTest {
    @Test fun browseAndGlobalSearchUseSeparateKeysAndWarmCategoriesRemainAvailable() {
        val server = MockWebServer()
        val directory = Files.createTempDirectory("synthetic-browse-cache").toFile()
        fun json(value: String) = MockResponse().setBody(value).addHeader("Content-Type","application/json")
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val url = request.requestUrl!!
                return when {
                    url.queryParameter("action") == "get_vod_categories" -> json("""[{"category_id":"7","category_name":"Synthetic"}]""")
                    url.queryParameter("category_id") == "7" -> json("""[{"stream_id":20,"name":"Category film","category_id":"7"}]""")
                    else -> json("""[{"stream_id":10,"name":"Full catalog film","category_id":"7"}]""")
                }
            }
        }
        server.start()
        val context = object : ContextWrapper(RuntimeEnvironment.getApplication()) { override fun getCacheDir() = directory }
        try {
            runBlocking {
                val account = AppSettings(source = PlaylistSource.XTREAM,xtreamServer = server.url("/").toString(),
                    xtreamUsername = "synthetic",xtreamPassword = "synthetic")
                val repo = VodRepositoryImpl(context)
                assertEquals("10",repo.catalog(account,VodKind.MOVIE).items.single().id)
                assertEquals(2,server.requestCount)
                val browse = repo.browseCatalogUpdates(account,VodKind.MOVIE).last().value
                assertTrue(browse.categoryScoped);assertTrue(browse.items.isEmpty())
                assertEquals(3,server.requestCount)
                assertEquals("10",repo.catalog(account,VodKind.MOVIE).items.single().id)
                assertEquals("20",repo.category(account,VodKind.MOVIE,"7").single().id)
                repo.browseCatalogUpdates(account,VodKind.MOVIE).last()
                repo.category(account,VodKind.MOVIE,"7")
                assertEquals(4,server.requestCount)
                repo.browseCatalogUpdates(account,VodKind.MOVIE,force = true).last()
                assertEquals(5,server.requestCount)
            }
        } finally { server.shutdown();directory.deleteRecursively() }
    }
}