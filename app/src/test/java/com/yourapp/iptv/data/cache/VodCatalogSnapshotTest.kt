package com.yourapp.iptv.data.cache

import com.yourapp.iptv.domain.model.*
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class VodCatalogSnapshotTest {
    private fun key(account: String, kind: String = "MOVIE", category: String? = null) =
        VodCatalogCachePolicy.key(account, kind, category)

    @Test fun completedMovieAndSeriesCatalogsRestoreAfterNewStoreInstance() {
        val dir = Files.createTempDirectory("synthetic-vod-cache").toFile()
        try {
            val catalog = VodCatalog(listOf(VodCategory("7", "Documentaries")),
                listOf(VodItem("42", "Feature", "7", poster = "https://synthetic.invalid/poster.jpg",
                    plot = "A sample", rating = "7.4", year = "2020", categoryIds = listOf("7", "8"))))
            VodCatalogSnapshot(dir).write(key("account-A"), catalog, 1234)
            val restored = VodCatalogSnapshot(dir).read(key("account-A"))
            assertEquals(catalog, restored?.catalog)
            assertEquals(1234L, restored?.writtenAtMs)
            assertNull(VodCatalogSnapshot(dir).read(key("account-B")))
            assertNull(VodCatalogSnapshot(dir).read(key("account-A", "SERIES")))
        } finally { dir.deleteRecursively() }
    }

    @Test fun categoryScopedSnapshotAndPerCategorySnapshotsStayDistinct() {
        val dir = Files.createTempDirectory("synthetic-vod-cache").toFile()
        try {
            val store = VodCatalogSnapshot(dir)
            val categories = VodCatalog(listOf(VodCategory("a", "A")), emptyList(), categoryScoped = true)
            val listing = VodCatalog(emptyList(), listOf(VodItem("9", "Episode collection", "a")))
            store.write(key("user"), categories, 10)
            store.write(key("user", category = "a"), listing, 11)
            assertEquals(categories, store.read(key("user"))?.catalog)
            assertEquals(listing, store.read(key("user", category = "a"))?.catalog)
            assertNull(store.read(key("user", category = "b")))
        } finally { dir.deleteRecursively() }
    }

    @Test fun corruptOrTruncatedSnapshotIsIgnoredSafely() {
        val dir = Files.createTempDirectory("synthetic-vod-cache").toFile()
        try {
            val store = VodCatalogSnapshot(dir)
            val identifier = key("corrupt")
            store.write(identifier, VodCatalog(emptyList(), listOf(VodItem("1", "One", null))), 10)
            val file = dir.resolve("$identifier.vod.gz")
            val bytes = file.readBytes()
            file.writeBytes(bytes.copyOf(bytes.size / 2))
            assertNull(store.read(identifier))
            file.writeBytes(byteArrayOf(1, 2, 3))
            assertNull(store.read(identifier))
        } finally { dir.deleteRecursively() }
    }

    @Test fun cacheRefreshAndOfflineExpiryAreTimeBound() {
        val now = 1_000_000_000L
        val fresh = VodCatalogCachePolicy.FRESH_MS
        val offline = VodCatalogCachePolicy.OFFLINE_MS
        assertTrue(VodCatalogCachePolicy.fresh(now - fresh + 1, now))
        assertFalse(VodCatalogCachePolicy.fresh(now - fresh, now))
        assertTrue(VodCatalogCachePolicy.usableOffline(now - offline + 1, now))
        assertFalse(VodCatalogCachePolicy.usableOffline(now - offline, now))
        assertFalse(VodCatalogCachePolicy.fresh(now + 1, now))
    }

    @Test fun largeSyntheticCatalogRetainsOrderAndTitlesWithoutNetwork() {
        val dir = Files.createTempDirectory("synthetic-vod-cache").toFile()
        try {
            val items = (1..5000).map { VodItem(it.toString(), "Video $it", "x") }
            val cache = VodCatalogSnapshot(dir)
            cache.write(key("large"), VodCatalog(emptyList(), items), 11)
            val restored = cache.read(key("large"))?.catalog?.items
            assertEquals(5000, restored?.size)
            assertEquals("Video 5000", restored?.last()?.name)
        } finally { dir.deleteRecursively() }
    }
    @Test fun replacingASnapshotLeavesOneReadableCacheFileAndNoTemporaryFiles() {
        val dir = Files.createTempDirectory("synthetic-vod-cache").toFile()
        try {
            val store = VodCatalogSnapshot(dir)
            val id = key("replacement")
            val first = VodCatalog(emptyList(), listOf(VodItem("1", "Old", null)))
            val newer = VodCatalog(emptyList(), listOf(VodItem("2", "New", null)))
            store.write(id, first, 100)
            store.write(id, newer, 200)
            assertEquals(newer, store.read(id)?.catalog)
            assertEquals(200L, store.read(id)?.writtenAtMs)
            assertEquals(listOf("$id.vod.gz"), dir.list()?.sorted())
        } finally { dir.deleteRecursively() }
    }

}