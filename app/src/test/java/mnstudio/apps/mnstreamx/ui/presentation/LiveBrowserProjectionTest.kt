package mnstudio.apps.mnstreamx.ui.presentation

import mnstudio.apps.mnstreamx.domain.model.*
import mnstudio.apps.mnstreamx.ui.state.HomeUiState
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class LiveBrowserProjectionTest {
    private val channels = listOf(Channel("1","PL| Alpha HD",streamUrl = "one",group = "A"),
        Channel("2","PL| Beta HD",streamUrl = "two",group = "B"))
    private val state = HomeUiState(channels = channels,visibleChannels = channels,groups = listOf("A","B"),browseScope = "synthetic")
    private fun context(value: HomeUiState = state, preferences: LiveTvPreferences = LiveTvPreferences(), access: Long = 1) =
        LiveProjectionContext(value,preferences,access)
    @Test fun changingCategoriesReusesSourceProjectionAndKeepsCatalogNumbers() {
        val worker = LiveBrowserProjection()
        val a = worker.project(Any(),context(state.copy(selectedGroup = "A",visibleChannels = channels.take(1))),LiveChannelSort.PROVIDER,Locale.ROOT) {}
        val b = worker.project(Any(),context(state.copy(selectedGroup = "B",visibleChannels = channels.takeLast(1))),LiveChannelSort.PROVIDER,Locale.ROOT) {}
        assertSame(a.personal.channels,b.personal.channels)
        assertEquals(1,a.index.rows.single().number);assertEquals(2,b.index.rows.single().number)
    }
    @Test fun repeatedProjectionReusesUnchangedRowsAndFavoriteChangesRemainVisible() {
        val worker = LiveBrowserProjection()
        val first = worker.project(Any(),context(),LiveChannelSort.PROVIDER,Locale.ROOT) {}
        val second = worker.project(Any(),context(),LiveChannelSort.PROVIDER,Locale.ROOT) {}
        assertSame(first.index.rows.first(),second.index.rows.first())
        val updated = listOf(channels.first().copy(isFavorite = true),channels.last())
        val third = worker.project(Any(),context(state.copy(channels = updated,visibleChannels = updated)),LiveChannelSort.FAVORITES,Locale.ROOT) {}
        assertTrue(third.index.rows.first().favorite)
        assertSame(second.index.rows.last(),third.index.rows.last())
    }
    @Test fun hidingChannelsInvalidatesCachedMembershipImmediately() {
        val worker = LiveBrowserProjection()
        worker.project(Any(),context(),LiveChannelSort.PROVIDER,Locale.ROOT) {}
        val result = worker.project(Any(),context(preferences = LiveTvPreferences(hiddenChannels = setOf("1"))),LiveChannelSort.PROVIDER,Locale.ROOT) {}
        assertEquals(listOf("2"),result.index.rows.map { it.key })
        assertEquals(listOf("B"),result.personal.groups)
    }
    @Test fun retentionRequiresTheSameAccessSourceMembershipAndFilter() {
        val original = context()
        assertTrue(original.matches(context(preferences = LiveTvPreferences(sort = LiveTvSort.NAME))))
        assertFalse(original.matches(context(access = 2)))
        assertFalse(original.matches(context(state.copy(browseScope = "other"))))
        assertFalse(original.matches(context(state.copy(selectedGroup = "A"))))
        assertFalse(original.matches(context(state.copy(query = "Alpha"))))
        assertFalse(original.matches(context(state.copy(visibleChannels = channels.take(1)))))
        assertFalse(original.matches(context(state.copy(isFiltering = true))))
        assertFalse(original.matches(context(preferences = LiveTvPreferences(hiddenChannels = setOf("1")))))
    }
}