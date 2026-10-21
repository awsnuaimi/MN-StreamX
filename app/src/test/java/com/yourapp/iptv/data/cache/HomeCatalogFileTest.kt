package com.yourapp.iptv.data.cache

import com.yourapp.iptv.domain.model.Channel
import com.yourapp.iptv.domain.organization.*
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.ByteBuffer

class HomeCatalogFileTest {
    @get:Rule val temporary = TemporaryFolder()
    private val config = OrganizationConfig(sourceKey = "account", enabled = true, folderSort = FolderSort.COUNT)
    private val rows = listOf(Channel("a", "TVP 1", streamUrl = "https://unit.test/a", group = "PL General"),
        Channel("b", "ZDF", streamUrl = "https://unit.test/b", group = "DE News"))

    @Test fun reopeningRestoresOrderFoldersAssignmentsAndCountsWithoutClassification() {
        val file = temporary.root.resolve("catalog.bin")
        val original = ChannelOrganizer.project(rows, config)
        HomeCatalogFile(file).write("fingerprint", config, original, 12345) {}
        val restored = HomeCatalogFile(file).read("fingerprint", config, {})!!
        assertEquals(original.channels, restored.catalog.channels)
        assertEquals(original.tree.folders, restored.catalog.tree.folders)
        assertEquals(original.tree.counts, restored.catalog.tree.counts)
        assertEquals(original.assignments, restored.catalog.assignments)
        assertEquals(original.groups, restored.catalog.groups)
        assertEquals(12345L, restored.refreshedAtMillis)
    }

    @Test fun accountAndRuleChangesRejectTheOldSnapshot() {
        val file = temporary.root.resolve("catalog.bin")
        HomeCatalogFile(file).write("first-account", config, ChannelOrganizer.project(rows, config), 1) {}
        assertThrows(IllegalArgumentException::class.java) { HomeCatalogFile(file).read("other", config) {} }
        val changed = config.copy(channelOverrides = mapOf("a" to "german"))
        assertThrows(IllegalArgumentException::class.java) { HomeCatalogFile(file).read("first-account", changed) {} }
        assertNotNull(HomeCatalogFile(file).read("first-account", config) {})
    }

    @Test fun interruptedWritePreservesThePreviousCompleteFile() {
        val file = temporary.root.resolve("catalog.bin")
        val original = ChannelOrganizer.project(rows, config)
        HomeCatalogFile(file).write("account", config, original, 1) {}
        val before = file.readBytes()
        var checkpoints = 0
        assertThrows(CancellationException::class.java) {
            HomeCatalogFile(file).write("account", config, original, 2) {
                if (++checkpoints > 20) throw CancellationException("cancelled")
            }
        }
        assertArrayEquals(before, file.readBytes())
        assertEquals(1L, HomeCatalogFile(file).read("account", config, {})!!.refreshedAtMillis)
    }

    @Test fun corruptionTruncationAndOversizedStringsAreRejected() {
        val file = temporary.root.resolve("catalog.bin")
        HomeCatalogFile(file).write("account", config, ChannelOrganizer.project(rows, config), 1) {}
        val before = file.readBytes()
        val corrupted = before.copyOf().also { it[it.size - 1] = (it.last().toInt() xor 1).toByte() }
        for (bad in listOf(corrupted, before.copyOf(before.size - 3), before.copyOf().also {
            ByteBuffer.wrap(it).putInt(8, 1024 * 1024 + 1)
        })) {
            file.writeBytes(bad)
            assertTrue(runCatching { HomeCatalogFile(file).read("account", config) {} }.isFailure)
        }
    }

    @Test fun disabledOrganizationAndFreshFavoriteStateAreNotConfused() {
        val file = temporary.root.resolve("catalog.bin")
        val disabled = config.copy(enabled = false)
        val catalog = ChannelOrganizer.project(rows.map { it.copy(isFavorite = true) }, disabled)
        HomeCatalogFile(file).write("account", disabled, catalog, 0) {}
        val restored = HomeCatalogFile(file).read("account", disabled, {})!!
        assertTrue(restored.catalog.assignments.isEmpty())
        assertFalse(restored.catalog.tree.enabled)
        assertTrue(restored.catalog.channels.none { it.isFavorite })
        assertEquals(rows, restored.catalog.channels)
    }

    @Test fun missingFileIsACacheMiss() {
        assertNull(HomeCatalogFile(temporary.root.resolve("missing.bin")).read("account", config) {})
    }

    @Test fun configurationKeyIsIndependentOfMapInsertionOrder() {
        val a = config.copy(channelOverrides = linkedMapOf("a" to "arabic", "b" to "polish"))
        val b = a.copy(channelOverrides = linkedMapOf("b" to "polish", "a" to "arabic"))
        assertEquals(HomeCatalogKey.of(a), HomeCatalogKey.of(b))
        assertNotEquals(HomeCatalogKey.of(a), HomeCatalogKey.of(a.copy(revision = 1)))
    }
}