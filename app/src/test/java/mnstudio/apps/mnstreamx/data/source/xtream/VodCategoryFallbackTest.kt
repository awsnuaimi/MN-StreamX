package mnstudio.apps.mnstreamx.data.source.xtream

import mnstudio.apps.mnstreamx.domain.model.VodItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VodCategoryFallbackTest {
    @Test fun categoriesPreserveFirstOccurrenceAcrossMultipleTitles() {
        val items = listOf(
            VodItem("1", "First", "7", categoryIds = listOf("7", "8", "7")),
            VodItem("2", "Second", "8", categoryIds = listOf("8", "9")),
            VodItem("3", "Third", null, categoryIds = emptyList())
        )
        val actual = VodCategoryFallback.fromItems(items)
        assertEquals(listOf("7", "8", "9"), actual.map { it.id })
        assertEquals(actual.map { it.id }, actual.map { it.name })
    }

    @Test fun largeSyntheticCatalogNeedsOnlyUniqueCategoryEntries() {
        val items = (1..50_000).map { index ->
            val category = "${index % 25}"
            VodItem(index.toString(), "Synthetic $index", category, categoryIds = listOf(category, "shared"))
        }
        val derived = VodCategoryFallback.fromItems(items)
        assertEquals(26, derived.size)
        assertEquals("1", derived.first().id)
        assertEquals("shared", derived[1].id)
        assertEquals("0", derived.last().id)
    }

    @Test fun emptyItemsProduceNoInventedCategories() {
        assertTrue(VodCategoryFallback.fromItems(emptyList()).isEmpty())
    }
}