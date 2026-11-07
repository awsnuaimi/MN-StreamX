package mnstudio.apps.mnstreamx.data.caption

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.IOException
/** Verified compressed bytes, bounded streamed decompression, decoded SHA and atomic publication. */
internal class BergamotModelFiles(private val files: VerifiedModelFiles) {
    private val mutex = Mutex()
    private val native by lazy { NativeBergamot() }
    suspend fun prepare(pack: BergamotPack, progress: (Int) -> Unit): File = mutex.withLock {
        val folder = files.prepare(pack.bundle) { progress(it * 70 / 100) }
        withContext(Dispatchers.IO) {
            folder.listFiles()?.filter { it.name.endsWith(".unpack") }?.forEach { it.delete() }
            for ((index, asset) in pack.assets.withIndex()) {
                currentCoroutineContext().ensureActive()
                val decoded = ModelAsset(asset.name, asset.size, asset.sha256)
                val target = folder.resolve(asset.name)
                if (!CaptionModelIntegrity.valid(target, decoded)) {
                    CaptionModelIntegrity.requireSpace(folder, asset.size)
                    val temporary = File.createTempFile("bergamot-", ".unpack", folder)
                    try {
                        native.unpack(folder.resolve(asset.packed.path).absolutePath, temporary.absolutePath, asset.size)
                        currentCoroutineContext().ensureActive()
                        if (!CaptionModelIntegrity.valid(temporary, decoded)) throw IOException("Decoded checksum mismatch")
                        if (!temporary.renameTo(target)) throw IOException("Cannot publish decoded model")
                    } finally { temporary.delete() }
                }
                progress(70 + (index + 1) * 30 / pack.assets.size)
            }
            folder
        }
    }
}