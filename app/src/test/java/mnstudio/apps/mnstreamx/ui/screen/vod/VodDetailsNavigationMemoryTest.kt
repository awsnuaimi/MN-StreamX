package mnstudio.apps.mnstreamx.ui.screen.vod

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VodDetailsNavigationMemoryTest {
    @Test fun remembersSeasonAndEpisodeIndependentlyForEachSeries() {
        val memory = VodDetailsNavigationMemory()
        memory.selectSeason("series-a", 3)
        memory.selectEpisode("series-b", 5, "episode-2")
        assertEquals(VodDetailsPosition(3), memory.get("series-a"))
        assertEquals(VodDetailsPosition(5, "episode-2"), memory.get("series-b"))
        memory.selectEpisode("series-a", 3, "episode-10")
        assertEquals(VodDetailsPosition(3, "episode-10"), memory.get("series-a"))
        memory.selectSeason("series-a", 1)
        assertEquals(VodDetailsPosition(1), memory.get("series-a"))
    }

    @Test fun savedStateRoundTripRetainsOnlyValidBoundedRecords() {
        val memory = VodDetailsNavigationMemory()
        for (n in 1..40) memory.selectEpisode("series-$n", n, "episode-$n")
        assertEquals(32, memory.entries().size)
        assertNull(memory.get("series-1"))
        val fields: List<Any> = memory.entries().flatMap { (id, pos) ->
            listOf(id, pos.season, pos.episodeId.orEmpty(), pos.episodeId != null)
        }
        val restored = VodDetailsNavigationMemory.restore(fields)
        assertEquals(memory.entries(), restored.entries())
        assertEquals(VodDetailsPosition(40, "episode-40"), restored.get("series-40"))
        assertTrue(restored.entries().size <= VodDetailsNavigationMemory.LIMIT)
    }

    @Test fun separateProfileScopesMustNeverShareAnInstance() {
        // Each Compose owner creates its own rememberSaveable(sourceKey) instance.
        val a = VodDetailsNavigationMemory()
        val b = VodDetailsNavigationMemory()
        a.selectEpisode("same-series", 2, "e1")
        assertNull(b.get("same-series"))
    }
}