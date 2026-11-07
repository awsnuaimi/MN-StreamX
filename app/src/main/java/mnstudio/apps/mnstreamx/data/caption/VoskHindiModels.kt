package mnstudio.apps.mnstreamx.data.caption

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.IOException
import java.util.zip.ZipInputStream
internal class VoskHindiModels(private val files: VerifiedModelFiles) {
    private val mutex = Mutex()
    suspend fun prepare(progress: (Int) -> Unit): File = mutex.withLock {
        val root = files.prepare(VoskHindiCatalog.bundle) { progress(it * 70 / 100) }
        withContext(Dispatchers.IO) {
            root.listFiles()?.filter { it.name.endsWith(".unpack") }?.forEach { it.deleteRecursively() }
            val target = root.resolve(VoskHindiCatalog.FOLDER)
            if (VoskHindiCatalog.files.all { CaptionModelIntegrity.valid(target.resolve(it.path), it) }) return@withContext target
            CaptionModelIntegrity.requireSpace(root, VoskHindiCatalog.files.sumOf { it.size })
            val temporary = File.createTempFile("vosk-", ".unpack", root)
            if (!temporary.delete() || !temporary.mkdirs()) throw IOException("Cannot prepare speech package")
            try {
                val remaining = VoskHindiCatalog.files.associateBy { it.path }.toMutableMap()
                root.resolve(VoskHindiCatalog.bundle.assets.single().path).inputStream().use { raw ->
                    ZipInputStream(raw.buffered()).use { input ->
                        var entry = input.nextEntry
                        val prefix = VoskHindiCatalog.FOLDER + "/"
                        while (entry != null) {
                            currentCoroutineContext().ensureActive()
                            if (!entry.name.startsWith(prefix)) throw IOException("Unexpected archive root")
                            val path = entry.name.removePrefix(prefix)
                            if (!entry.isDirectory) {
                                val asset = remaining.remove(path) ?: throw IOException("Unexpected archive entry")
                                val file = temporary.resolve(path)
                                if (!file.canonicalPath.startsWith(temporary.canonicalPath + File.separator)) throw IOException("Invalid archive path")
                                file.parentFile?.mkdirs()
                                file.outputStream().use { output ->
                                    val buffer = ByteArray(65536); var total = 0L
                                    while (true) {
                                        currentCoroutineContext().ensureActive()
                                        val count = input.read(buffer); if (count < 0) break
                                        total += count
                                        if (total > asset.size) throw IOException("Archive exceeds pinned size")
                                        output.write(buffer, 0, count)
                                    }
                                    if (total != asset.size) throw IOException("Truncated archive")
                                }
                                if (!CaptionModelIntegrity.valid(file, asset)) throw IOException("Speech checksum mismatch")
                            }
                            input.closeEntry(); entry = input.nextEntry
                        }
                    }
                }
                if (remaining.isNotEmpty()) throw IOException("Incomplete archive")
                if (target.exists() && !target.deleteRecursively()) throw IOException("Cannot replace speech model")
                if (!temporary.renameTo(target)) throw IOException("Cannot publish speech model")
                progress(100); target
            } finally { temporary.deleteRecursively() }
        }
    }
}