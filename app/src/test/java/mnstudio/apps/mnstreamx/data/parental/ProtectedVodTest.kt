package mnstudio.apps.mnstreamx.data.parental

import mnstudio.apps.mnstreamx.domain.model.*
import mnstudio.apps.mnstreamx.domain.parental.*
import mnstudio.apps.mnstreamx.domain.repository.*
import mnstudio.apps.mnstreamx.testing.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test

class ProtectedVodTest {
    private val source = searchAccount("A")
    private val access = Access(ParentalState(ready = true,configured = true,rules = ParentalRules(setOf(ParentalSection.MOVIES))))
    private val raw = ParentalVod()
    private val vod = ProtectedVod(raw,access)
    @Test fun blockedCatalogAndCategoriesDoNotMakeProviderRequests() = runBlocking {
        assertTrue(vod.catalog(source,VodKind.MOVIE).items.isEmpty());assertTrue(vod.category(source,VodKind.MOVIE,"10").isEmpty())
        assertEquals(0,raw.requests);assertEquals(1,vod.catalog(source,VodKind.SERIES).items.size)
    }
    @Test fun detailsAndDirectPlaybackUrlsRequireAccessEvenOutsideNavigation() = runBlocking {
        try { vod.details(source,VodKind.MOVIE,raw.item);fail("Details exposed") } catch (_: ParentalDenied) { }
        try { vod.playbackUrl(source,VodKind.MOVIE,"1","mp4");fail("Playback exposed") } catch (_: ParentalDenied) { }
        assertEquals(0,raw.requests)
        access.state.value = access.state.value.copy(unlocked = true)
        assertTrue(vod.playbackUrl(source,VodKind.MOVIE,"1","mp4").contains("/1.mp4"))
    }
    @Test fun aCatalogResultArrivingAfterRelockContainsNoProtectedMetadata() = runBlocking {
        access.state.value = access.state.value.copy(unlocked = true)
        raw.beforeReturn = { access.lock() }
        val result = vod.catalog(source,VodKind.MOVIE)
        assertTrue(result.categories.isEmpty());assertTrue(result.items.isEmpty())
    }
    @Test fun resumeAndSearchHistoryAreHiddenWithoutDeletingTheirStoredValues() = runBlocking {
        val rows = MutableStateFlow(listOf(VodProgressEntry(VodKind.MOVIE,"1","Protected movie","mp4",60000,200000,100)))
        var records = 0
        val rawProgress = object : VodProgressRepository {
            override fun observe(sourceKey: String) = rows
            override fun record(sourceKey: String, entry: VodProgressEntry, completed: Boolean) { records++ }
        }
        val progress = ProtectedProgress(rawProgress,access)
        val rawHistory = SearchHistoryMemory();val key = PlaylistCacheKey.of(source)
        rawHistory.record(key,"Protected movie");val history = ProtectedSearchHistory(rawHistory,access)
        assertTrue(progress.observe(key).first().isEmpty());assertTrue(history.observe(key).first().isEmpty())
        progress.record(key,rows.value.single(),false);assertEquals(1,records)
        access.state.value = access.state.value.copy(unlocked = true)
        assertEquals(rows.value,progress.observe(key).first());assertEquals(listOf("Protected movie"),history.observe(key).first())
        assertEquals(1,rawHistory.values.value[key]?.size)
    }

    @Test fun playbackResolutionChecksAccessBeforeRequestingMetadata() = runBlocking {
        try { vod.resolvePlayback(source,VodKind.MOVIE,"1","mp4");fail("Playback exposed") } catch (_: ParentalDenied) { }
        assertEquals(0,raw.requests)
    }

    @Test fun relockingWhileResolvingADirectTargetPreventsItsReturn() = runBlocking {
        access.state.value = access.state.value.copy(unlocked = true)
        val delayed = object : VodRepository by raw {
            override suspend fun resolvePlayback(settings: AppSettings,kind: VodKind,id: String,extension: String): VodPlaybackSource {
                access.lock();return VodPlaybackSource("https://cdn.test/signed","mkv")
            }
        }
        try { ProtectedVod(delayed,access).resolvePlayback(source,VodKind.MOVIE,"1","mp4");fail("Locked URL exposed") }
        catch (_: ParentalDenied) { }
    }

    @Test fun categoryFirstBrowsingAndFreshRetriesCannotBypassProtection() = runBlocking {
        assertTrue(vod.browseCatalogUpdates(source,VodKind.MOVIE).first().value.categories.isEmpty())
        try { vod.refreshDetails(source,VodKind.MOVIE,raw.item);fail("Details exposed") } catch (_: ParentalDenied) { }
        try { vod.refreshPlayback(source,VodKind.MOVIE,"1","mp4");fail("Playback exposed") } catch (_: ParentalDenied) { }
        assertEquals(0,raw.requests)
        access.state.value = access.state.value.copy(unlocked = true)
        val delayed = object : VodRepository by raw {
            override suspend fun refreshPlayback(settings: AppSettings,kind: VodKind,id: String,extension: String): VodPlaybackSource {
                access.lock();return VodPlaybackSource("https://cdn.invalid/fresh","mp4")
            }
        }
        try { ProtectedVod(delayed,access).refreshPlayback(source,VodKind.MOVIE,"1","mp4");fail("Locked playback exposed") }
        catch (_: ParentalDenied) { }
    }
}