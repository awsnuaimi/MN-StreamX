package com.yourapp.iptv.viewmodel

import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.domain.search.SearchMatches
import com.yourapp.iptv.testing.*
import com.yourapp.iptv.ui.state.SearchVodState
import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchVodControllerTest {
    @Test fun categoryOnlyProvidersAreSearchedInExplicitBatchesAndEventuallyReportCompleteCoverage() = runTest {
        val repo = SearchCatalogMemory().apply { catalogBlock = { _, _ -> scoped(12) } }
        var state = SearchVodState()
        val controller = SearchVodController(VodKind.MOVIE, backgroundScope, repo, StandardTestDispatcher(testScheduler)) { state = it }
        controller.bind(searchAccount("A")); controller.updateQuery("shared"); controller.search(); runCurrent()
        assertTrue(state.categoryScoped); assertFalse(state.complete); assertEquals(0, state.categoriesSearched)
        assertTrue(repo.categoryRequests.isEmpty())
        controller.scanMore(); runCurrent()
        assertEquals(10, state.categoriesSearched); assertFalse(state.complete); assertTrue(state.canScan)
        controller.scanMore(); runCurrent()
        assertEquals(12, state.categoriesSearched); assertTrue(state.complete); assertEquals(12, state.results.size)
    }
    @Test fun failedCategoriesKeepCoverageIncompleteAndCanBeRetriedWithoutDuplicatingHits() = runTest {
        val repo = SearchCatalogMemory().apply { catalogBlock = { _, _ -> scoped(2) } }
        repo.categoryBlock = { id, _ -> if (id == "2") throw IOException("Synthetic"); SearchMatches(listOf(VodItem("42", "Shared", id))) }
        var state = SearchVodState()
        val controller = SearchVodController(VodKind.MOVIE, backgroundScope, repo, StandardTestDispatcher(testScheduler)) { state = it }
        controller.bind(searchAccount("A")); controller.updateQuery("shared"); controller.search(); runCurrent()
        controller.scanMore(); runCurrent()
        assertEquals(1, state.categoriesFailed); assertFalse(state.complete); assertEquals(1, state.results.size)
        repo.categoryBlock = { id, _ -> SearchMatches(listOf(VodItem("42", "Shared", id))) }
        controller.scanMore(); runCurrent()
        assertEquals(0, state.categoriesFailed); assertTrue(state.complete); assertEquals(1, state.results.size)
    }
    @Test fun changingTheQueryOrStoppingPreventsLateCategoryResponsesFromAppearing() = runTest {
        val repo = SearchCatalogMemory().apply { catalogBlock = { _, _ -> scoped(12) } }
        repo.categoryBlock = { id, _ -> withContext(NonCancellable) { delay(1000) }; SearchMatches(listOf(VodItem(id, "Old", id))) }
        var state = SearchVodState()
        val controller = SearchVodController(VodKind.MOVIE, backgroundScope, repo, StandardTestDispatcher(testScheduler)) { state = it }
        controller.bind(searchAccount("A")); controller.updateQuery("old"); controller.search(); runCurrent()
        controller.scanMore(); runCurrent(); assertTrue(state.scanning)
        controller.updateQuery("new"); controller.search(); advanceTimeBy(1001); runCurrent()
        assertTrue(state.results.isEmpty()); assertEquals(0, state.categoriesSearched)
        controller.scanMore(); runCurrent(); controller.stopScan(); advanceTimeBy(1001); runCurrent()
        assertFalse(state.scanning); assertTrue(state.results.isEmpty()); assertTrue(state.canScan)
    }
    @Test fun repeatedIdsAndLargeMatchesNeverGrowTheRetainedResultListBeyondOneHundred() = runTest {
        val repo = SearchCatalogMemory().apply { catalogBlock = { _, _ -> scoped(20) } }
        repo.categoryBlock = { id, _ -> SearchMatches((1..100).map { VodItem(it.toString(), "Shared $it", id) }, more = true) }
        var state = SearchVodState()
        val controller = SearchVodController(VodKind.MOVIE, backgroundScope, repo, StandardTestDispatcher(testScheduler)) { state = it }
        controller.bind(searchAccount("A")); controller.updateQuery("shared"); controller.search(); runCurrent()
        controller.scanMore(); runCurrent()
        assertEquals(100, state.results.size); assertTrue(state.more); assertFalse(state.complete)
        assertEquals(1, repo.categoryRequests.size); assertFalse(state.canScan)
    }
    @Test fun aLimitedCategoryIndexDoesNotPretendThereWereOneHundredMatchesOrFullCoverage() = runTest {
        val repo = SearchCatalogMemory().apply { catalogBlock = { _, _ -> scoped(1) } }
        repo.categoryBlock = { _, _ -> SearchMatches(limited = true) }
        var state = SearchVodState()
        val controller = SearchVodController(VodKind.MOVIE, backgroundScope, repo, StandardTestDispatcher(testScheduler)) { state = it }
        controller.bind(searchAccount("A")); controller.updateQuery("shared"); controller.search(); runCurrent()
        controller.scanMore(); runCurrent()
        assertTrue(state.indexLimited); assertFalse(state.more); assertFalse(state.complete)
    }
}