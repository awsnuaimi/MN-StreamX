package mnstudio.apps.mnstreamx.viewmodel

import mnstudio.apps.mnstreamx.domain.model.VodItem
import org.junit.Assert.*
import org.junit.Test

class VodCatalogFilterRefinementTest {
    private val items = (0 until 10_000).map {
        VodItem("$it", if (it % 3 == 0) "Drama $it" else "Comedy $it",
            if (it % 2 == 0) "a" else "b")
    }

    @Test fun longerSearchUsesOnlyCompletedPriorMatches() {
        val previous = VodCatalogFilter.match(items, "a", "dra")
        val candidates = VodCatalogFilter.refinedCandidates(
            items, "a", "drama", items, "a", "dra", previous)
        assertSame(previous, candidates)
        assertEquals(VodCatalogFilter.match(items, "a", "drama"),
            VodCatalogFilter.match(candidates, "a", "drama"))
    }

    @Test fun narrowingRemainsCorrectWithCaseAndWhitespace() {
        val previous = VodCatalogFilter.match(items, null, " DR ")
        val candidates = VodCatalogFilter.refinedCandidates(
            items, null, "dRaMa", items, null, " DR ", previous)
        assertSame(previous, candidates)
        assertEquals(VodCatalogFilter.match(items, null, "dRaMa"),
            VodCatalogFilter.match(candidates, null, "dRaMa"))
    }

    @Test fun deletingOrChangingQueryAlwaysUsesWholeCatalog() {
        val previous = VodCatalogFilter.match(items, null, "drama")
        listOf("dram", "comedy", "", "ramad").forEach { newQuery ->
            assertSame(items, VodCatalogFilter.refinedCandidates(
                items, null, newQuery, items, null, "drama", previous))
        }
    }

    @Test fun categoryOrCatalogChangeMustNotReuseOtherScope() {
        val previous = VodCatalogFilter.match(items, "a", "dr")
        val replacement = items.toList()
        assertNotSame(items, replacement)
        assertSame(replacement, VodCatalogFilter.refinedCandidates(
            replacement, "a", "dra", items, "a", "dr", previous))
        assertSame(items, VodCatalogFilter.refinedCandidates(
            items, "b", "dra", items, "a", "dr", previous))
        assertSame(items, VodCatalogFilter.refinedCandidates(
            items, "a", "dra", items, "a", "dr", null))
    }

    @Test fun incrementalRefinementIsEquivalentToFullFiltering() {
        val queries = listOf("d", "dr", "dra", "dram", "drama", "dra", "co", "com", "come")
        for (category in listOf(null, "a", "b")) {
            var previousQuery = ""
            var previousResult: List<VodItem>? = null
            queries.forEach { query ->
                val candidates = VodCatalogFilter.refinedCandidates(items, category, query,
                    items, category, previousQuery, previousResult)
                val result = VodCatalogFilter.match(candidates, category, query)
                assertEquals(VodCatalogFilter.match(items, category, query), result)
                previousQuery = query
                previousResult = result
            }
        }
    }
}