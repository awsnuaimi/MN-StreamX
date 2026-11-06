package mnstudio.apps.mnstreamx.data.repository

import android.app.Application
import android.content.ContextWrapper
import mnstudio.apps.mnstreamx.data.cache.VodCatalogCachePolicy
import mnstudio.apps.mnstreamx.data.cache.VodCatalogSnapshot
import mnstudio.apps.mnstreamx.domain.model.*
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class VodRepositoryInstantLoadTest {
    private val account = AppSettings(source = PlaylistSource.XTREAM,
        xtreamServer = "https://synthetic.invalid", xtreamUsername = "synthetic-cache",
        xtreamPassword = "synthetic")

    private fun withDisk(block: (File, ContextWrapper) -> Unit) {
        val root = Files.createTempDirectory("synthetic-vod-swrevalidate").toFile()
        try {
            val context = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
                override fun getCacheDir(): File = root
            }
            block(root, context)
        } finally { root.deleteRecursively() }
    }

    private fun seed(root: File, kind: VodKind, categoryId: String? = null,
        at: Long = System.currentTimeMillis() - VodCatalogCachePolicy.FRESH_MS - 1_000L) {
        val key = VodCatalogCachePolicy.key(PlaylistCacheKey.of(account), kind.name, categoryId)
        VodCatalogSnapshot(root.resolve("vod_catalog_v1")).write(key,
            VodCatalog(emptyList(), listOf(VodItem("old", "Cached", categoryId))), at)
    }

    @Test fun staleCatalogEmitsBeforeNetworkResponseAndThenRefreshes() = withDisk { root, context ->
        seed(root, VodKind.MOVIE)
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        val repo = VodRepositoryImpl(context) { _, _, _ ->
            calls++
            gate.await()
            VodCatalog(emptyList(), listOf(VodItem("new", "Fresh", null)))
        }
        runBlocking {
            val results = mutableListOf<mnstudio.apps.mnstreamx.domain.repository.VodLoad<VodCatalog>>()
            val job = async { repo.catalogUpdates(account, VodKind.MOVIE).toList(results) }
            while (results.isEmpty()) yield()
            assertEquals("old", results.single().value.items.single().id)
            assertTrue(results.single().refreshing)
            gate.complete(Unit)
            job.await()
            assertEquals("new", results.last().value.items.single().id)
            assertFalse(results.last().refreshing)
            assertEquals(1, calls)
            assertEquals("new", repo.catalog(account, VodKind.MOVIE).items.single().id)
            assertEquals(1, calls)
        }
    }

    @Test fun staleCatalogSurvivesNetworkFailureWithoutFullScreenError() = withDisk { root, context ->
        seed(root, VodKind.SERIES)
        val repo = VodRepositoryImpl(context) { _, _, _ ->
            throw java.io.IOException("Synthetic provider unavailable")
        }
        runBlocking {
            val events = repo.catalogUpdates(account, VodKind.SERIES).toList()
            assertEquals(2, events.size)
            assertTrue(events.first().refreshing)
            assertTrue(events.last().refreshFailed)
            assertFalse(events.last().refreshing)
            assertEquals("old", events.last().value.items.single().id)
        }
    }

    @Test fun manualRefreshBypassesLocalCopyAndCategoryKeysStaySeparate() = withDisk { root, context ->
        seed(root, VodKind.MOVIE)
        seed(root, VodKind.MOVIE, categoryId = "a")
        var requests = 0
        val repo = VodRepositoryImpl(context) { _, _, category ->
            requests++
            VodCatalog(emptyList(), listOf(VodItem("fresh", "From network", category)))
        }
        runBlocking {
            val categoryEvents = repo.categoryUpdates(account, VodKind.MOVIE, "a").toList()
            assertEquals("old", categoryEvents.first().value.single().id)
            assertEquals("fresh", categoryEvents.last().value.single().id)
            assertEquals(1, requests)
            val forced = repo.catalogUpdates(account, VodKind.MOVIE, force = true).toList()
            assertEquals(1, forced.size)
            assertEquals("fresh", forced.single().value.items.single().id)
            assertEquals(2, requests)
        }
    }

    @Test fun concurrentReadersOfSameCatalogShareOneNetworkFetch() = withDisk { _, context ->
        val gate = CompletableDeferred<Unit>()
        val started = CompletableDeferred<Unit>()
        var calls = 0
        val repo = VodRepositoryImpl(context) { _, _, _ ->
            calls++
            started.complete(Unit)
            gate.await()
            VodCatalog(emptyList(), listOf(VodItem("1", "One", null)))
        }
        runBlocking {
            val first = async { repo.catalog(account, VodKind.MOVIE) }
            started.await()
            val second = async { repo.catalog(account, VodKind.MOVIE) }
            yield()
            gate.complete(Unit)
            assertEquals("One", first.await().items.single().name)
            assertEquals("One", second.await().items.single().name)
            assertEquals(1, calls)
        }
    }

    @Test fun providerResultsArePublishedBeforeSlowCachePersistenceFinishes() = withDisk { _, context ->
        val writing = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val repo = VodRepositoryImpl(context, fetch = { _, _, _ ->
            VodCatalog(emptyList(), listOf(VodItem("99", "Available", null)))
        }, persist = { _, _ ->
            writing.complete(Unit)
            release.await()
        })
        try {
            runBlocking {
                val loaded = withTimeout(5_000) { repo.catalog(account, VodKind.MOVIE) }
                assertEquals("99", loaded.items.single().id)
                withTimeout(5_000) { writing.await() }
                // The cache writer is still blocked; the library is already usable.
                assertFalse(release.isCompleted)
                assertEquals("99", repo.catalog(account, VodKind.MOVIE).items.single().id)
            }
        } finally { release.complete(Unit) }
    }

}