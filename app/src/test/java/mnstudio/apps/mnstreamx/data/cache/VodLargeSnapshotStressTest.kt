package mnstudio.apps.mnstreamx.data.cache

import mnstudio.apps.mnstreamx.domain.model.VodCatalog
import mnstudio.apps.mnstreamx.domain.model.VodCategory
import mnstudio.apps.mnstreamx.domain.model.VodItem
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

/** Disk scenarios use generated content only, never real provider files or credentials. */
class VodLargeSnapshotStressTest {
    private fun key(account: String, kind: String = "MOVIE") =
        VodCatalogCachePolicy.key(account, kind)

    @Test fun largeMultilingualCatalogRoundTripsWithoutCrossAccountExposure() {
        val directory = Files.createTempDirectory("synthetic-vod-load-").toFile()
        try {
            val categories = (0 until 12).map { VodCategory("cat-$it", "Kategorie $it") }
            val originals = (0 until 45_000).map { index ->
                val category = "cat-${index % categories.size}"
                VodItem("id-$index", "Film عربي Über $index", category,
                    extension = "mp4", plot = "Synthetic description $index",
                    categoryIds = listOf(category, "shared"))
            }
            val catalog = VodCatalog(categories, originals)
            val store = VodCatalogSnapshot(directory)
            store.write(key("synthetic-A"), catalog, 1_234L)
            val restored = VodCatalogSnapshot(directory).read(key("synthetic-A"))
            assertEquals(45_000, restored?.catalog?.items?.size)
            assertEquals(1_234L, restored?.writtenAtMs)
            assertEquals(originals.first(), restored?.catalog?.items?.first())
            assertEquals(originals[22_222], restored?.catalog?.items?.get(22_222))
            assertEquals(originals.last(), restored?.catalog?.items?.last())
            assertEquals(categories, restored?.catalog?.categories)
            assertNull(store.read(key("synthetic-B")))
            assertNull(store.read(key("synthetic-A", "SERIES")))
            assertEquals(1, directory.listFiles()?.count { it.name.endsWith(".vod.gz") })
            assertFalse(directory.listFiles().orEmpty().any { it.name.endsWith(".tmp") })
        } finally { directory.deleteRecursively() }
    }

    @Test fun overLimitCatalogCannotEvictOrCorruptLastValidSnapshot() {
        val directory = Files.createTempDirectory("synthetic-vod-limit-").toFile()
        try {
            val identifier = key("synthetic-limit")
            val store = VodCatalogSnapshot(directory)
            val original = VodCatalog(emptyList(), listOf(VodItem("stable", "Original", null)))
            store.write(identifier, original, 100L)
            val tooMany = VodCatalog(emptyList(), (0..60_000).map {
                VodItem("id-$it", "Item $it", null)
            })
            store.write(identifier, tooMany, 200L)
            val restored = store.read(identifier)
            assertEquals(100L, restored?.writtenAtMs)
            assertEquals(original, restored?.catalog)
            assertFalse(directory.listFiles().orEmpty().any { it.name.endsWith(".tmp") })
        } finally { directory.deleteRecursively() }
    }
}