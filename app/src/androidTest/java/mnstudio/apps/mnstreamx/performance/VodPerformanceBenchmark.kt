package mnstudio.apps.mnstreamx.performance

import android.content.Context
import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import mnstudio.apps.mnstreamx.data.cache.VodCatalogSnapshot
import mnstudio.apps.mnstreamx.domain.model.VodCatalog
import mnstudio.apps.mnstreamx.domain.model.VodCategory
import mnstudio.apps.mnstreamx.domain.model.VodItem
import mnstudio.apps.mnstreamx.ui.component.common.FocusBookmarks
import mnstudio.apps.mnstreamx.viewmodel.VodCatalogFilter
import java.io.File
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Device-side benchmarks for representative large-library operations.
 *
 * Run on a physical Android TV device with the benchmark build variant.
 * Inputs are synthetic; no provider credentials, URLs, or personal data are used.
 */
@RunWith(AndroidJUnit4::class)
class VodPerformanceBenchmark {
    @get:Rule
    val benchmarkRule = BenchmarkRule()

    private val library: List<VodItem> by lazy(LazyThreadSafetyMode.NONE) {
        (0 until LARGE_LIBRARY_SIZE).map { index ->
            val category = "cat-${index % CATEGORY_COUNT}"
            VodItem(
                id = "synthetic-$index",
                name = if (index % 3 == 0) "Drama Über $index" else "Comedy Film $index",
                categoryId = category,
                categoryIds = listOf(category, "shared")
            )
        }
    }

    private val cacheDirectory: File by lazy(LazyThreadSafetyMode.NONE) {
        File(ApplicationProvider.getApplicationContext<Context>().cacheDir, "vod-benchmark")
    }

    @After
    fun removeSyntheticCache() {
        cacheDirectory.deleteRecursively()
    }

    @Test
    fun fullCategoryAndSearchFilterOnSixtyThousandTitles() {
        benchmarkRule.measureRepeated {
            val matches = VodCatalogFilter.match(library, "cat-3", "Drama 1")
            check(matches.isNotEmpty())
        }
    }

    @Test
    fun incrementalSearchRefinesThePreviousResult() {
        val previous = VodCatalogFilter.match(library, "cat-3", "Drama")
        check(previous.isNotEmpty())
        benchmarkRule.measureRepeated {
            val candidates = VodCatalogFilter.refinedCandidates(
                items = library,
                category = "cat-3",
                query = "Drama 1",
                previousItems = library,
                previousCategory = "cat-3",
                previousQuery = "Drama",
                previousResult = previous
            )
            val matches = VodCatalogFilter.match(candidates, "cat-3", "Drama 1")
            check(matches.isNotEmpty())
        }
    }

    @Test
    fun restoreAnUnchangedFocusPositionInSixtyThousandKeys() {
        val keys = library.map { it.id }
        val saved = FocusBookmarks.Place(keys[43_210], 43_210)
        benchmarkRule.measureRepeated {
            check(FocusBookmarks.resolve(saved, keys) == 43_210)
        }
    }

    @Test
    fun restoreFocusAfterCatalogReordering() {
        val keys = library.map { it.id }
        val selected = keys[43_210]
        val reordered = keys.asReversed()
        val saved = FocusBookmarks.Place(selected, 43_210)
        val expected = reordered.size - 1 - 43_210
        check(reordered[expected] == selected)

        benchmarkRule.measureRepeated {
            check(FocusBookmarks.resolve(saved, reordered) == expected)
        }
    }

    @Test
    fun restoreFocusWhenSelectedTitleWasRemoved() {
        val keys = library.map { it.id }
        val selectedIndex = 43_210
        val saved = FocusBookmarks.Place(keys[selectedIndex], selectedIndex)
        val remaining = keys.filterIndexed { index, _ -> index != selectedIndex }
        check(saved.key !in remaining)

        benchmarkRule.measureRepeated {
            check(FocusBookmarks.resolve(saved, remaining) == selectedIndex)
        }
    }

    @Test
    fun categorySwitchInvalidatesIncrementalSearchCandidates() {
        val previous = VodCatalogFilter.match(library, "cat-3", "Drama")
        check(previous.isNotEmpty())
        benchmarkRule.measureRepeated {
            val candidates = VodCatalogFilter.refinedCandidates(
                items = library,
                category = "cat-4",
                query = "Drama 1",
                previousItems = library,
                previousCategory = "cat-3",
                previousQuery = "Drama",
                previousResult = previous
            )
            check(candidates === library)
            val matches = VodCatalogFilter.match(candidates, "cat-4", "Drama 1")
            check(matches.all { it.belongsTo("cat-4") })
        }
    }

    @Test
    fun readCompressedVodSnapshotFromDeviceCache() {
        val snapshot = VodCatalogSnapshot(cacheDirectory)
        val catalog = syntheticCacheCatalog()
        val key = CACHE_KEY
        snapshot.write(key, catalog, CACHE_TIMESTAMP)
        check(snapshot.read(key)?.catalog?.items?.size == CACHE_ITEM_COUNT)

        benchmarkRule.measureRepeated {
            val loaded = snapshot.read(key) ?: error("Synthetic VOD snapshot could not be read")
            check(loaded.catalog.items.size == CACHE_ITEM_COUNT)
        }
    }

    @Test
    fun writeCompressedVodSnapshotToDeviceCache() {
        val snapshot = VodCatalogSnapshot(cacheDirectory)
        val catalog = syntheticCacheCatalog()
        benchmarkRule.measureRepeated {
            snapshot.write(CACHE_KEY, catalog, CACHE_TIMESTAMP)
            check(File(cacheDirectory, "$CACHE_KEY.vod.gz").isFile)
        }
    }

    private fun syntheticCacheCatalog(): VodCatalog {
        val categories = (0 until CATEGORY_COUNT).map { index ->
            VodCategory("cat-$index", "Synthetic category $index")
        }
        val items = (0 until CACHE_ITEM_COUNT).map { index ->
            val category = "cat-${index % CATEGORY_COUNT}"
            VodItem(
                id = "cache-$index",
                name = "Synthetic title $index",
                categoryId = category,
                extension = "mp4",
                categoryIds = listOf(category, "shared")
            )
        }
        return VodCatalog(categories, items)
    }

    private companion object {
        const val LARGE_LIBRARY_SIZE = 60_000
        const val CATEGORY_COUNT = 7
        const val CACHE_ITEM_COUNT = 2_000
        const val CACHE_TIMESTAMP = 1_800_000_000_000L
        const val CACHE_KEY = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
    }
}