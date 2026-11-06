package mnstudio.apps.mnstreamx.viewmodel

import mnstudio.apps.mnstreamx.domain.model.VodItem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Deterministic large-library regression cases; no timing thresholds or provider access. */
class VodLargeLibraryStressTest {
    private val items by lazy {
        (0 until 60_000).map { index ->
            val primary = "cat-${index % 7}"
            VodItem(index.toString(),
                if (index % 3 == 0) "Drama Über $index" else "Comedy فيلم $index",
                primary, categoryIds = listOf(primary, "shared"))
        }
    }

    @Test fun repeatedCategorySearchMatchesFullScanAndNeverChangesTheCatalog() = runBlocking {
        val originalFirst = items.first()
        val originalLast = items.last()
        val queries = listOf("d", "dr", "dra", "Drama", "Drama 1", "", "c", "co", "Comedy", "فيلم")
        for (category in listOf<String?>(null, "cat-0", "cat-3", "shared")) {
            var previousQuery = ""
            var previousResult: List<VodItem>? = null
            for (query in queries) {
                val candidates = VodCatalogFilter.refinedCandidates(items, category, query,
                    items, category, previousQuery, previousResult)
                val actual = VodCatalogFilter.matchCancellable(candidates, category, query)
                assertEquals("category=$category query=$query",
                    VodCatalogFilter.match(items, category, query), actual)
                previousResult = actual
                previousQuery = query
            }
        }
        assertSame(originalFirst, items.first())
        assertSame(originalLast, items.last())
        assertEquals(60_000, items.size)
    }

    @Test fun replacementCatalogOrCategoryNeverReusesOldAccountResults() {
        val previous = VodCatalogFilter.match(items, "cat-1", "Drama")
        val replacement = (0 until 60_000).map { VodItem("new-$it", "New $it", "cat-1") }
        assertSame(replacement, VodCatalogFilter.refinedCandidates(replacement, "cat-1", "Drama",
            items, "cat-1", "Dra", previous))
        assertSame(items, VodCatalogFilter.refinedCandidates(items, "cat-2", "Drama",
            items, "cat-1", "Dra", previous))
        assertTrue(VodCatalogFilter.match(replacement, "cat-1", "Drama").isEmpty())
    }

    @Test fun unfilteredFastPathDoesNotDuplicateSixtyThousandObjects() = runBlocking {
        assertSame(items, VodCatalogFilter.match(items, null, "  "))
        assertSame(items, VodCatalogFilter.matchCancellable(items, null, ""))
    }
}