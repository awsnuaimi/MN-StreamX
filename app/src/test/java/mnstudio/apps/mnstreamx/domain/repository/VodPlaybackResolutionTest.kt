package mnstudio.apps.mnstreamx.domain.repository

import mnstudio.apps.mnstreamx.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VodPlaybackResolutionTest {
    private val settings = AppSettings()
    private var requests = 0
    private var read: suspend (VodItem) -> VodDetails = { VodDetails(it.copy(extension = "mkv")) }
    private val repository = object : VodRepository {
        override fun supports(settings: AppSettings) = true
        override suspend fun catalog(settings: AppSettings,kind: VodKind): VodCatalog = error("Unexpected catalog")
        override suspend fun category(settings: AppSettings,kind: VodKind,categoryId: String): List<VodItem> = error("Unexpected category")
        override suspend fun details(settings: AppSettings,kind: VodKind,item: VodItem): VodDetails { requests++;return read(item) }
        override fun playbackUrl(settings: AppSettings,kind: VodKind,id: String,extension: String) = "https://unit.test/$id.$extension"
    }
    private suspend fun resolve() = repository.resolvePlayback(settings,VodKind.MOVIE,"7","mp4")

    @Test fun freshMovieDetailsCorrectAStaleCatalogExtension() = runTest {
        assertEquals(VodPlaybackSource("https://unit.test/7.mkv","mkv"),resolve())
        assertEquals(1,requests)
    }
    @Test fun providerDirectTargetTakesPriorityWithoutChangingTheMovieIdentity() = runTest {
        read = { VodDetails(it,playback = VodPlaybackSource("https://cdn.test/opaque?sig=x","m3u8")) }
        assertEquals("https://cdn.test/opaque?sig=x",resolve().url)
    }
    @Test fun unavailableMetadataFallsBackToTheKnownCatalogUrl() = runTest {
        read = { throw java.io.IOException("Synthetic failure") }
        assertEquals("https://unit.test/7.mp4",resolve().url)
    }
    @Test fun foreignMovieMetadataCannotReplaceTheRequestedMovie() = runTest {
        read = { VodDetails(it.copy(id = "8"),playback = VodPlaybackSource("https://cdn.test/8","mkv")) }
        assertEquals("https://unit.test/7.mp4",resolve().url)
    }
    @Test fun aMetadataTimeoutIsBoundedAndFallsBackWithoutASecondProbe() = runTest {
        read = { awaitCancellation() }
        assertEquals("https://unit.test/7.mp4",resolve().url)
        assertEquals(10_000L,testScheduler.currentTime);assertEquals(1,requests)
    }
    @Test fun externalCancellationIsNeverConvertedIntoPlaybackFallback() = runTest {
        val started = CompletableDeferred<Unit>();val stopped = CompletableDeferred<Unit>()
        read = { try { started.complete(Unit);awaitCancellation() } finally { stopped.complete(Unit) } }
        val job = launch { resolve();fail("Cancelled playback resumed") }
        started.await();job.cancelAndJoin();assertTrue(stopped.isCompleted)
    }
    @Test fun episodesKeepTheirOwnIdsAndNeverRequestMovieInfo() = runTest {
        val result = repository.resolvePlayback(settings,VodKind.SERIES,"19","ts")
        assertEquals("https://unit.test/19.ts",result.url);assertEquals(0,requests)
    }
}