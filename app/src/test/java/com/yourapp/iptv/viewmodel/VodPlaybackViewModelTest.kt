package com.yourapp.iptv.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.domain.repository.*
import com.yourapp.iptv.testing.SearchAccounts
import com.yourapp.iptv.testing.searchAccount
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class VodPlaybackViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val owner = ViewModelStore()
    private val jobs = mutableListOf<Job>()
    private val settings = SearchAccounts()
    private val urls = java.util.Collections.synchronizedList(mutableListOf<String>())
    private val records = mutableListOf<Pair<VodProgressEntry,Boolean>>()
    private var resumes = emptyList<VodProgressEntry>()
    private val requests = java.util.Collections.synchronizedList(mutableListOf<String>())
    private var loadDetails: suspend () -> VodDetails = { metadata() }
    private val vod = object : VodRepository {
        override fun supports(settings: AppSettings) = true
        override suspend fun catalog(settings: AppSettings, kind: VodKind) = error("Unexpected catalog")
        override suspend fun category(settings: AppSettings, kind: VodKind, categoryId: String) = error("Unexpected category")
        override suspend fun details(settings: AppSettings, kind: VodKind, item: VodItem): VodDetails {
            requests.add(item.id); return loadDetails()
        }
        override fun playbackUrl(settings: AppSettings, kind: VodKind, id: String, extension: String): String {
            urls.add(id); return "https://synthetic.invalid/${settings.xtreamUsername}/$id.$extension"
        }
    }
    private val progress = object : VodProgressRepository {
        override fun observe(sourceKey: String) = flowOf(resumes)
        override fun record(sourceKey: String, entry: VodProgressEntry, completed: Boolean) {
            assertEquals(settings.key,sourceKey);records.add(entry to completed)
        }
    }
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() {
        owner.clear()
        try { runBlocking { withTimeout(5000) { jobs.forEach { it.join() } } } }
        finally { Dispatchers.resetMain() }
    }
    private fun saved(series: String? = "55", kind: String = "series") = SavedStateHandle(mapOf<String,Any?>(
        "kind" to kind,"id" to "10","ext" to "mp4","title" to "Episode 10","sourceKey" to settings.key,"seriesId" to series))
    private fun model(saved: SavedStateHandle = saved()) = VodPlaybackViewModel(saved,settings,vod,progress)
        .also { owner.put(java.util.UUID.randomUUID().toString(),it); jobs.add(requireNotNull(it.viewModelScope.coroutineContext[Job])) }
    private fun ready(model: VodPlaybackViewModel, accept: (VodPlaybackState.Ready) -> Boolean = { true }) = runBlocking {
        withTimeout(5000) { model.state.filterIsInstance<VodPlaybackState.Ready>().first(accept) }
    }
    private fun metadata() = VodDetails(VodItem("55","Series",null),listOf(
        VodSeason(2,"Two",listOf(VodEpisode("30","Next season",2,1,"mkv"))),
        VodSeason(1,"One",listOf(VodEpisode("20","Episode 20",1,2,"mp4"),VodEpisode("10","Episode 10",1,1,"mp4")))))

    @Test fun legacyMovieRoutesKeepTheirResumePositionAndDoNotRequestEpisodes() {
        resumes = listOf(VodProgressEntry(VodKind.MOVIE,"10","Movie","mp4",60000,200000,100))
        val model = model(saved(null,"movie"));val value = ready(model)
        assertEquals(60000L,value.resumeMs);assertNull(value.next);assertTrue(requests.isEmpty())
        assertFalse(model.autoNext.value)
    }
    @Test fun seriesMetadataUsesTheParentIdAndOrdersEpisodesAcrossSeasons() {
        val model = model();val first = ready(model) { it.next != null }
        assertEquals(listOf("55"),requests);assertEquals("20",first.next?.id)
        model.playNext();val second = ready(model) { it.id == "20" }
        assertEquals("30",second.next?.id);assertEquals(1,requests.size)
        model.playNext();assertNull(ready(model) { it.id == "30" }.next)
        model.playNext();assertEquals(listOf("10","20","30"),urls)
    }
    @Test fun disposingThePreviousPlayerCannotWriteItsPositionIntoTheNextEpisode() {
        val model = model();val first = ready(model) { it.next != null }
        model.playNext();val second = ready(model) { it.id == "20" }
        model.capture(first,70000,200000)
        model.capture(second,200000,200000,completed = true)
        assertEquals(listOf("10","20"),records.map { it.first.id })
        assertEquals(70000L,records.first().first.positionMs)
        assertTrue(records.all { it.first.seriesId == "55" });assertTrue(records.last().second)
    }
    @Test fun aLateClickFromThePreviousEpisodeCannotSkipTheEpisodeThatHasJustStarted() {
        val model = model();val first = ready(model) { it.next != null }
        model.playNext(first);val second = ready(model) { it.id == "20" }
        model.playNext(first)
        assertEquals("20",(model.state.value as VodPlaybackState.Ready).id)
        assertEquals(listOf("10","20"),urls)
        model.playNext(second);assertEquals("30",ready(model) { it.id == "30" }.id)
    }
    @Test fun existingSavedEpisodesWithoutAParentStillPlayAndNewSavedEpisodesRecoverTheirParent() {
        val legacy = model(saved(null));assertNull(ready(legacy).seriesId);assertTrue(requests.isEmpty())
        resumes = listOf(VodProgressEntry(VodKind.SERIES,"10","Episode","mp4",60000,200000,100,"55"))
        val restored = model(saved(null));val value = ready(restored) { it.next != null }
        assertEquals("55",value.seriesId);assertEquals(60000L,value.resumeMs)
    }
    @Test fun explicitRestartRemovesThePreviousResumeEvenIfTheUserLeavesBeforeTenSeconds() {
        val model = model(saved(null,"movie"));val current = ready(model)
        model.restartProgress(current,200000);model.capture(current,3000,200000)
        assertEquals(1,records.size);assertEquals(0L,records.single().first.positionMs)
        assertFalse(records.single().second)
        val previous = VodProgressEntry(VodKind.MOVIE,"10","Movie","mp4",60000,200000,100)
        assertTrue(VodProgress.record(listOf(previous),records.single().first).isEmpty())
    }
    @Test fun metadataFailureDoesNotStopTheVideoAndCanBeRetriedWithoutResolvingItAgain() {
        loadDetails = { error("Synthetic metadata failure") }
        val model = model();val first = ready(model) { it.episodesFailed }
        assertEquals("10",first.id);assertFalse(first.episodesLoading)
        loadDetails = { metadata() };model.retryEpisodes()
        assertEquals("20",ready(model) { it.next != null }.next?.id)
        assertEquals(2,requests.size);assertEquals(listOf("10"),urls)
    }
    @Test fun aSubscriptionChangeBlocksPlaybackAndIgnoresLateMetadataFromThePreviousSource() = runBlocking {
        val gate = CompletableDeferred<Unit>();val finished = CompletableDeferred<Unit>()
        loadDetails = { withContext(NonCancellable) { gate.await() };finished.complete(Unit);metadata() }
        val model = model();ready(model)
        withTimeout(5000) { while (requests.isEmpty()) delay(10) }
        settings.values.value = searchAccount("B")
        assertEquals(VodPlaybackState.Error,model.state.value)
        gate.complete(Unit);withTimeout(5000) { finished.await() }
        model.playNext();assertEquals(VodPlaybackState.Error,model.state.value);assertEquals(listOf("10"),urls)
    }
    @Test fun theCurrentEpisodeAndExplicitAutoplayChoiceSurviveViewModelRecreation() {
        val handle = saved();val first = model(handle);ready(first) { it.next != null }
        first.setAutoNext(true);first.playNext();ready(first) { it.id == "20" }
        owner.clear()
        val second = model(handle);assertEquals("20",ready(second).id);assertTrue(second.autoNext.value)
        assertEquals("55",handle.get<String>("seriesId"));assertEquals(settings.key,handle.get<String>("sourceKey"))
        assertFalse(handle.keys().any { it.contains("password",true) || it.contains("url",true) })
    }
    @Test fun clearingTheViewModelCancelsPendingMetadataWork() = runBlocking {
        val started = CompletableDeferred<Unit>();val cancelled = CompletableDeferred<Unit>()
        loadDetails = { try { started.complete(Unit);awaitCancellation() } finally { cancelled.complete(Unit) } }
        model();withTimeout(5000) { started.await() };owner.clear()
        withTimeout(5000) { cancelled.await() }
    }
}