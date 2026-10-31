package com.yourapp.iptv.viewmodel

import com.yourapp.iptv.domain.model.VodItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VodCatalogFilterTest {
    private val items = listOf(
        VodItem("1", "First Movie", "a", categoryIds = listOf("a", "b")),
        VodItem("2", "Second Movie", "b"),
        VodItem("3", "Third Series", "c")
    )

    @Test fun matchesCategoryAliasesAndTrimmedCaseInsensitiveSearch() {
        assertEquals(listOf("1"), VodCatalogFilter.match(items, "a", " FIRST ").map { it.id })
        assertEquals(listOf("1", "2"), VodCatalogFilter.match(items, "b", "movie").map { it.id })
        assertEquals(listOf("3"), VodCatalogFilter.match(items, null, "SERIES").map { it.id })
    }

    @Test fun asynchronousFilteringUsesTheSameOrderingAndCriteria() = runBlocking {
        val large = (0 until 1000).map { index ->
            VodItem("$index", "Film $index", if (index % 2 == 0) "a" else "b")
        }
        assertTrue(large.size >= VodCatalogFilter.BACKGROUND_THRESHOLD)
        assertEquals(VodCatalogFilter.match(large, "a", "film 2"),
            VodCatalogFilter.matchCancellable(large, "a", "film 2"))
    }

    @Test fun cancelledScanDoesNotReturnAResult() = runBlocking {
        val cancelled = Job().apply { cancel() }
        var thrown = false
        try {
            withContext(cancelled) { VodCatalogFilter.matchCancellable(items, null, "") }
        } catch (_: CancellationException) {
            thrown = true
        }
        assertTrue(thrown)
    }
}