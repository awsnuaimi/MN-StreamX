package mnstudio.apps.mnstreamx.viewmodel

import mnstudio.apps.mnstreamx.domain.model.VodCategory
import org.junit.Assert.*
import org.junit.Test

class VodCategoryWarmupTest {
    private val seasons = listOf(VodCategory("a", "A"), VodCategory("b", "B"),
        VodCategory("c", "C"), VodCategory("d", "D"))

    @Test fun prefersNextCategory() {
        assertEquals("b", VodCategoryWarmup.next(seasons, "a", emptySet()))
        assertEquals("c", VodCategoryWarmup.next(seasons, "b", emptySet()))
    }

    @Test fun lastCategoryFallsBackToPrevious() {
        assertEquals("c", VodCategoryWarmup.next(seasons, "d", emptySet()))
    }

    @Test fun skipsAttemptedAndDoesNotWarmWholeLibrary() {
        assertEquals("a", VodCategoryWarmup.next(seasons, "b", setOf("c")))
        assertNull(VodCategoryWarmup.next(seasons, "b", setOf("a", "c")))
        assertNull(VodCategoryWarmup.next(seasons, "b", setOf("a", "c", "d")))
    }

    @Test fun unknownCurrentAndSingleCategoryDoNotTriggerNetwork() {
        assertNull(VodCategoryWarmup.next(seasons, "unknown", emptySet()))
        assertNull(VodCategoryWarmup.next(seasons.take(1), "a", emptySet()))
        assertNull(VodCategoryWarmup.next(emptyList(), "a", emptySet()))
    }

    @Test fun blankIdentifiersNeverBecomePrefetchTargets() {
        val malformed = listOf(VodCategory("a", "A"), VodCategory("", "Empty"))
        assertNull(VodCategoryWarmup.next(malformed, "a", emptySet()))
    }
}