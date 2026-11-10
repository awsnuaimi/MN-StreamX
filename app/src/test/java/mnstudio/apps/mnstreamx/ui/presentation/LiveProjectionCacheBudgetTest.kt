package mnstudio.apps.mnstreamx.ui.presentation

import mnstudio.apps.mnstreamx.domain.model.Channel
import mnstudio.apps.mnstreamx.domain.model.LiveTvPreferences
import mnstudio.apps.mnstreamx.ui.state.HomeUiState
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class LiveProjectionCacheBudgetTest {
    @Test fun lowRamDevicesRetainFewerDisposableObjects() {
        for (heap in listOf(64, 128, 256, 512)) {
            val low = LiveProjectionCacheBudget.forDevice(heap, true)
            val normal = LiveProjectionCacheBudget.forDevice(heap, false)
            assertTrue(low.metadataEntries <= normal.metadataEntries)
            assertTrue(low.reusableRows <= normal.reusableRows)
            assertTrue(low.preparedRows <= normal.preparedRows)
            assertTrue(low.preparedViews <= normal.preparedViews)
        }
    }

    @Test fun unknownAndSmallHeapReportsUseCompactBudget() {
        assertEquals(LiveProjectionCacheBudget.forDevice(32, true),
            LiveProjectionCacheBudget.forDevice(-1, false))
        assertEquals(1, LiveProjectionCacheBudget.forDevice(96, false).preparedViews)
        assertEquals(4, LiveProjectionCacheBudget.forDevice(256, false).preparedViews)
    }

    @Test fun smallBudgetKeepsFullCurrentListAndDoesNotReuseStaleRows() {
        val budget = LiveProjectionCacheBudget(2, 2, 1, 2)
        val worker = LiveBrowserProjection(budget)
        val channels = (1..12).map { Channel(it.toString(), "Station $it", streamUrl = "url-$it") }
        fun state(source: List<Channel>) = HomeUiState(
            channels = source, visibleChannels = source, browseScope = "budget-test")
        fun project(source: List<Channel>) = worker.project(Any(),
            LiveProjectionContext(state(source), LiveTvPreferences(), 1),
            LiveChannelSort.PROVIDER, Locale.ROOT) {}
        val first = project(channels)
        assertEquals(12, first.index.rows.size)
        assertEquals((1..12).toList(), first.index.rows.map { it.number })
        val modified = channels.map { if (it.id == "1") it.copy(name = "Changed") else it }
        val updated = project(modified)
        assertEquals("Changed", updated.index.rows.first().title)
        assertEquals(12, updated.index.rows.size)
        assertEquals((1..12).toList(), updated.index.rows.map { it.number })
    }
}