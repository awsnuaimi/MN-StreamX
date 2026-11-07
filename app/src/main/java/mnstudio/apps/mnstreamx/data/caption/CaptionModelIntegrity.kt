package mnstudio.apps.mnstreamx.data.caption

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import mnstudio.apps.mnstreamx.domain.caption.CaptionResourcePolicy
import mnstudio.apps.mnstreamx.domain.caption.CaptionStorageException
import java.io.File
import java.security.MessageDigest
internal object CaptionModelIntegrity {
    suspend fun valid(file: File, asset: ModelAsset): Boolean {
        if (!file.isFile || file.length() != asset.size) return false
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(65536)
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = input.read(buffer); if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) } == asset.sha256
    }
    fun requireSpace(directory: File, bytes: Long) {
        val required = bytes + CaptionResourcePolicy.STORAGE_RESERVE
        if (directory.usableSpace < required) throw CaptionStorageException(directory.usableSpace, required)
    }
}