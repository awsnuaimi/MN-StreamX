package mnstudio.apps.mnstreamx.ui.presentation

import mnstudio.apps.mnstreamx.domain.model.Channel
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class LiveChannelMetadataTest {
    private fun channel(id: String, name: String = "PL| Alpha HD") = Channel(id,name,streamUrl = "https://synthetic.invalid/$id",group = "G")
    @Test fun catalogNumberingAndCategoryVisitsReuseTheSameParsedMetadata() {
        var prepared = 0
        val cache = LiveChannelMetadataCache { prepared++;LiveChannelMetadata.prepare(it) }
        val source = (0 until 5000).map { channel("$it","PL| Synthetic $it HD") }
        val numbers = LiveChannelNumbers().forCatalog(source,cache)
        repeat(5) {
            val rows = LiveChannelRows.project(source.take(30),LiveChannelSort.NAME,Locale.GERMAN,cache)
            assertEquals(30,rows.size);assertEquals(1,numbers.getValue(rows.first().preferenceKey))
        }
        assertEquals(5000,prepared)
    }
    @Test fun favoriteChangesReuseMetadataButPublishTheNewChannelState() {
        var prepared = 0
        val cache = LiveChannelMetadataCache { prepared++;LiveChannelMetadata.prepare(it) }
        val source = channel("1")
        LiveChannelRows.project(listOf(source),LiveChannelSort.PROVIDER,Locale.ROOT,cache)
        val changed = source.copy(isFavorite = true,folderIds = listOf("new"))
        val row = LiveChannelRows.project(listOf(changed),LiveChannelSort.PROVIDER,Locale.ROOT,cache).single()
        assertEquals(1,prepared);assertTrue(row.favorite);assertSame(changed,row.variants.single())
    }
    @Test fun renamedChannelsAndChangedProviderGroupsNeverReuseOldFamilies() {
        val cache = LiveChannelMetadataCache();val source = channel("1")
        val original = cache.get(source)
        assertNotEquals(original.family,cache.get(source.copy(name = "PL| Beta HD")).family)
        assertNotEquals(original.family,cache.get(source.copy(group = "Other")).family)
        val plain = source.copy(name = "Plain")
        assertNotEquals(cache.get(plain).family,cache.get(plain.copy(streamUrl = "https://synthetic.invalid/other")).family)
    }
    @Test fun theMetadataBudgetEvictsLeastRecentlyUsedEntries() {
        var prepared = 0
        val cache = LiveChannelMetadataCache(2) { prepared++;LiveChannelMetadata.prepare(it) }
        val a = channel("a");val b = channel("b");val c = channel("c")
        cache.get(a);cache.get(b);cache.get(a);cache.get(c);cache.get(a)
        assertEquals(3,prepared);cache.get(b);assertEquals(4,prepared)
    }
    @Test fun interruptedProjectionDoesNotFinishScanningAWholeCatalog() {
        val source = (0 until 5000).map { channel("$it") }
        var checked = 0
        val result = runCatching {
            LiveChannelRows.project(source,LiveChannelSort.NAME,Locale.ROOT,checkpoint = {
                if (++checked == 40) throw java.util.concurrent.CancellationException("Synthetic cancellation")
            })
        }
        assertTrue(result.exceptionOrNull() is java.util.concurrent.CancellationException)
        assertEquals(40,checked)
    }
}