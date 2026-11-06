package mnstudio.apps.mnstreamx.ui.screen.vod

import mnstudio.apps.mnstreamx.domain.model.VodItem
import org.junit.Assert.*
import org.junit.Test

class VodCatalogOrderTest {
    @Test fun stableCaseInsensitiveSortPreservesEqualNameOrder() {
        val original = listOf(VodItem("0", "Zulu", null), VodItem("1", "alpha", null),
            VodItem("2", "ALPHA", null), VodItem("3", "Bravo", null))
        val sorted = VodCatalogOrder.byName(original)
        assertEquals(listOf("1", "2", "3", "0"), sorted.map { it.id })
        assertEquals(listOf("0", "1", "2", "3"), original.map { it.id })
    }

    @Test fun largeCatalogSortDoesNotChangeEntriesOrIDs() {
        val original = (50_000 downTo 0).map {
            VodItem("$it", "Title ${it.toString().padStart(6, '0')}", null)
        }
        val result = VodCatalogOrder.byName(original)
        assertEquals(original.size, result.size)
        assertEquals("0", result.first().id)
        assertEquals("50000", result.last().id)
        assertEquals("50000", original.first().id)
        assertEquals(original.map { it.id }.toSet(), result.map { it.id }.toSet())
    }
}