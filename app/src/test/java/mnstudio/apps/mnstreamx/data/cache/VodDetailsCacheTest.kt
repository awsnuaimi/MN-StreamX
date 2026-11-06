package mnstudio.apps.mnstreamx.data.cache

import mnstudio.apps.mnstreamx.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VodDetailsCacheTest {
    private val item = VodItem("1","Synthetic",null)
    @Test fun concurrentReadersShareOneFetchAndReuseTheCompletedMetadata() = runTest {
        val cache = VodDetailsCache { testScheduler.currentTime }
        val release = CompletableDeferred<Unit>();var calls = 0
        suspend fun read() = cache.get("one",15_000) { calls++;release.await();VodDetails(item) }
        val jobs = List(8) { async { read() } }
        runCurrent();assertEquals(1,calls)
        release.complete(Unit);val result = jobs.awaitAll()
        result.forEach { assertSame(result.first(),it) }
        assertSame(result.first(),read());assertEquals(1,calls)
    }
    @Test fun expiryAndExplicitRefreshNeverReturnAnExpiredSignedTarget() = runTest {
        var now = 0L;var calls = 0;val cache = VodDetailsCache { now }
        suspend fun read(refresh: Boolean = false) = cache.get("one",15_000,refresh) {
            calls++;VodDetails(item,playback = VodPlaybackSource("target:$calls","mp4"))
        }
        assertEquals("target:1",read().playback?.url)
        now = 14_999;assertEquals("target:1",read().playback?.url)
        now = 15_000;assertEquals("target:2",read().playback?.url)
        assertEquals("target:3",read(true).playback?.url)
        now = -1;assertEquals("target:4",read().playback?.url)
    }
    @Test fun aFailedRefreshDropsThePreviousTargetAndCanBeRetried() = runTest {
        val cache = VodDetailsCache { 0 }
        cache.get("one",15_000) { VodDetails(item,playback = VodPlaybackSource("old","mp4")) }
        try { cache.get("one",15_000,true) { throw java.io.IOException("Synthetic failure") };fail() }
        catch (_: java.io.IOException) { }
        assertEquals("new",cache.get("one",15_000) {
            VodDetails(item,playback = VodPlaybackSource("new","mp4"))
        }.playback?.url)
    }
    @Test fun cancellingAFetchCannotPublishIncompleteMetadataOrLockOutTheNextReader() = runTest {
        val cache = VodDetailsCache { 0 };val started = CompletableDeferred<Unit>()
        val job = launch { cache.get("one",15_000) { started.complete(Unit);awaitCancellation() } }
        started.await();job.cancelAndJoin()
        assertEquals(item,cache.get("one",15_000) { VodDetails(item) }.item)
    }
    @Test fun independentTitlesDoNotWaitBehindASlowRequest() = runTest {
        val cache = VodDetailsCache { 0 };val started = CompletableDeferred<Unit>()
        val slow = launch { cache.get("slow",15_000) { started.complete(Unit);awaitCancellation() } }
        started.await()
        assertEquals(item,cache.get("fast",15_000) { VodDetails(item) }.item)
        slow.cancelAndJoin()
    }
    @Test fun boundedEntriesEvictOldTitlesAndOversizedSeriesAreNotRetained() = runTest {
        val cache = VodDetailsCache { 0 };var calls = 0
        repeat(13) { n -> cache.get("title:$n",15_000) { calls++;VodDetails(item) } }
        cache.get("title:0",15_000) { calls++;VodDetails(item) }
        assertEquals(14,calls)
        val huge = VodDetails(item,listOf(VodSeason(1,"One",List(5_001) {
            VodEpisode("$it","Episode $it",1,it,"mp4")
        })))
        repeat(2) { cache.get("huge",15_000) { calls++;huge } }
        assertEquals(16,calls)
    }
    @Test fun aSingleLongPlotCannotConsumeTheMetadataCacheBudget() = runTest {
        val cache = VodDetailsCache { 0 };var calls = 0
        val large = VodDetails(item.copy(plot = "x".repeat(2_200_000)))
        repeat(2) { cache.get("large",15_000) { calls++;large } }
        assertEquals(2,calls)
    }
}