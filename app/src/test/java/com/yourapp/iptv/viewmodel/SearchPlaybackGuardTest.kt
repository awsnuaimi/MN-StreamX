package com.yourapp.iptv.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.domain.repository.*
import com.yourapp.iptv.testing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SearchPlaybackGuardTest {
    private val dispatcher = StandardTestDispatcher()
    private val owner = ViewModelStore()
    private val settings = SearchAccounts()
    private var urls = 0
    private var progressReads = 0
    private val vod = object : VodRepository {
        override fun supports(settings: AppSettings) = true
        override suspend fun catalog(settings: AppSettings, kind: VodKind) = error("Unexpected catalog")
        override suspend fun category(settings: AppSettings, kind: VodKind, categoryId: String) = error("Unexpected category")
        override suspend fun details(settings: AppSettings, kind: VodKind, item: VodItem) = error("Unexpected details")
        override fun playbackUrl(settings: AppSettings, kind: VodKind, id: String, extension: String): String {
            urls++; return "https://synthetic.invalid/${settings.xtreamUsername}/$id.$extension"
        }
    }
    private val progress = object : VodProgressRepository {
        override fun observe(sourceKey: String) = flowOf(listOf(VodProgressEntry(VodKind.MOVIE, "42", "Shared", "mp4",
            70_000, 200_000, 100))).also { progressReads++ }
        override fun record(sourceKey: String, entry: VodProgressEntry, completed: Boolean) = Unit
    }
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { owner.clear(); Dispatchers.resetMain() }
    private fun model(expected: String?): VodPlaybackViewModel {
        val values = mutableMapOf<String, Any?>("kind" to "movie", "id" to "42", "ext" to "mp4", "title" to "Shared")
        if (expected != null) values["sourceKey"] = expected
        return VodPlaybackViewModel(SavedStateHandle(values), settings, vod, progress).also { owner.put("player", it) }
    }
    @Test fun aStaleSearchResultCannotResolveTheSameMovieIdFromAnotherSubscription() = runTest(dispatcher) {
        val old = settings.key; settings.values.value = searchAccount("B")
        val model = model(old); runCurrent()
        assertEquals(VodPlaybackState.Error, model.state.value)
        assertEquals(0, urls); assertEquals(0, progressReads)
    }
    @Test fun matchingSearchResultsKeepTheExistingResumePipeline() = runTest(dispatcher) {
        val model = model(settings.key); runCurrent()
        val ready = model.state.value as VodPlaybackState.Ready
        assertEquals(70_000L, ready.resumeMs); assertEquals(settings.key, ready.sourceKey)
        assertEquals("https://synthetic.invalid/A/42.mp4", ready.url)
    }
    @Test fun existingMovieAndSeriesRoutesWithoutASourceArgumentRemainSupported() = runTest(dispatcher) {
        val model = model(null); runCurrent()
        assertTrue(model.state.value is VodPlaybackState.Ready); assertEquals(1, urls)
    }
}