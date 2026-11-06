package mnstudio.apps.mnstreamx.data.cache

import mnstudio.apps.mnstreamx.domain.model.VodCatalog
import mnstudio.apps.mnstreamx.domain.model.VodCategory
import mnstudio.apps.mnstreamx.domain.model.VodItem
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/** Optional snapshot in app-private cache storage; poster URLs may contain provider tokens. */
internal class VodCatalogSnapshot(private val directory: File) {
    data class Saved(val catalog: VodCatalog, val writtenAtMs: Long)

    companion object {
        private const val MAGIC = 0x564F4432
        private const val VERSION = 1
        private const val MAX_FILE = 32L * 1024 * 1024
        private const val MAX_CATEGORIES = 10_000
        private const val MAX_ITEMS = 60_000
        private const val MAX_STRING_BYTES = 64 * 1024
        private const val MAX_TOTAL_BYTES = 80L * 1024 * 1024
        private const val MAX_FILES = 32
        private const val MAX_DISK_BYTES = 96L * 1024 * 1024
        private const val MAX_CATEGORY_REFS = 200_000
    }

    /** The caller must provide a SHA-256 derived key, never a raw server, username or category. */
    fun read(key: String): Saved? {
        if (!validKey(key)) return null
        val file = File(directory, "$key.vod.gz")
        if (!file.isFile || file.length() !in 1..MAX_FILE) return null
        return try {
            FileInputStream(file).use { stream ->
                DataInputStream(GZIPInputStream(BufferedInputStream(stream, 64 * 1024))).use { input ->
                    val bounded = Budget(MAX_TOTAL_BYTES)
                    require(input.readInt() == MAGIC && input.readInt() == VERSION)
                    require(input.text(bounded) == key)
                    val timestamp = input.readLong().also { require(it > 0) }
                    val scoped = input.readBoolean()
                    val unavailable = input.readBoolean()
                    val categories = List(input.count(MAX_CATEGORIES)) {
                        VodCategory(input.text(bounded).orEmpty(), input.text(bounded).orEmpty())
                    }
                    var references = 0
                    val items = List(input.count(MAX_ITEMS)) {
                        val id = input.text(bounded).orEmpty()
                        val name = input.text(bounded).orEmpty()
                        val primary = input.text(bounded)
                        val poster = input.text(bounded)
                        val ext = input.text(bounded).orEmpty()
                        val plot = input.text(bounded)
                        val rating = input.text(bounded)
                        val year = input.text(bounded)
                        val referenceCount = input.count(64)
                        references += referenceCount
                        require(references <= MAX_CATEGORY_REFS)
                        val categoriesForItem = List(referenceCount) { input.text(bounded).orEmpty() }
                        VodItem(id, name, primary, poster, ext, plot, rating, year, categoriesForItem)
                    }
                    require(input.read() == -1)
                    require(categories.all { it.id.isNotBlank() } && items.all { it.id.isNotBlank() })
                    Saved(VodCatalog(categories, items, scoped, unavailable), timestamp)
                }
            }
        } catch (_: Exception) { null }
    }

    fun write(key: String, catalog: VodCatalog, timestamp: Long) {
        if (!validKey(key) || timestamp <= 0 || catalog.categories.size > MAX_CATEGORIES ||
            catalog.items.size > MAX_ITEMS) return
        if (!directory.isDirectory && !directory.mkdirs()) return
        val temporary = File.createTempFile("vod-", ".tmp", directory)
        try {
            FileOutputStream(temporary).use { stream ->
                val buffered = BufferedOutputStream(stream, 64 * 1024)
                val zipped = GZIPOutputStream(buffered)
                val output = DataOutputStream(zipped)
                try {
                    output.writeInt(MAGIC); output.writeInt(VERSION)
                    output.text(key); output.writeLong(timestamp)
                    output.writeBoolean(catalog.categoryScoped)
                    output.writeBoolean(catalog.categoriesUnavailable)
                    output.writeInt(catalog.categories.size)
                    catalog.categories.forEach { output.text(it.id); output.text(it.name) }
                    output.writeInt(catalog.items.size)
                    var references = 0
                    catalog.items.forEach { item ->
                        output.text(item.id); output.text(item.name); output.text(item.categoryId)
                        output.text(item.poster); output.text(item.extension)
                        output.text(item.plot); output.text(item.rating); output.text(item.year)
                        references += item.categoryIds.size
                        require(item.categoryIds.size <= 64 && references <= MAX_CATEGORY_REFS)
                        output.writeInt(item.categoryIds.size)
                        item.categoryIds.forEach { output.text(it) }
                    }
                    output.flush()
                    zipped.finish()
                    buffered.flush()
                    // Disposable cache: close and atomic rename are sufficient. Forcing an
                    // fsync here stalls large-library publication on slow TV storage.
                } finally { output.close() }
            }
            require(temporary.length() in 1..MAX_FILE)
            val destination = File(directory, "$key.vod.gz").toPath()
            try {
                Files.move(temporary.toPath(), destination,
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                // Some Android filesystems cannot guarantee an atomic move. The
                // temporary file has already been fully closed at this point.
                Files.move(temporary.toPath(), destination, StandardCopyOption.REPLACE_EXISTING)
            }
            trim()
        } finally { temporary.delete() }
    }

    private fun trim() {
        val files = directory.listFiles { f -> f.isFile && f.name.endsWith(".vod.gz") }
            ?.sortedByDescending { it.lastModified() }.orEmpty()
        var used = 0L
        files.forEachIndexed { index, file ->
            used += file.length()
            if (index >= MAX_FILES || used > MAX_DISK_BYTES) {
                used -= file.length()
                file.delete()
            }
        }
    }

    private fun validKey(value: String) = value.length == 64 && value.all { it in '0'..'9' || it in 'a'..'f' }
    private class Budget(var remaining: Long) {
        fun consume(size: Int) { remaining -= size; require(remaining >= 0) }
    }
    private fun DataInputStream.count(limit: Int) = readInt().also { require(it in 0..limit) }
    private fun DataOutputStream.text(value: String?) {
        if (value == null) { writeInt(-1); return }
        val bytes = value.toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_STRING_BYTES)
        writeInt(bytes.size); write(bytes)
    }
    private fun DataInputStream.text(budget: Budget): String? {
        val length = readInt()
        if (length == -1) return null
        require(length in 0..MAX_STRING_BYTES)
        budget.consume(length)
        val bytes = ByteArray(length)
        readFully(bytes)
        return String(bytes, Charsets.UTF_8)
    }
}