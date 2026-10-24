package com.yourapp.iptv.ui.component.player

import android.content.Context
import android.graphics.Bitmap
import com.yourapp.iptv.domain.model.Channel
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A small, private, evictable cache. Captures are neither backed up nor shared. */
internal object ChannelFrameStore {
    private const val DIRECTORY = "live-hero-frames"
    private const val MAX_FRAMES = 8
    private const val MAX_AGE_MS = 12L * 60L * 60L * 1000L

    private fun directory(context: Context) = File(context.cacheDir, DIRECTORY)

    fun fileFor(context: Context, channel: Channel): File? {
        val file = File(directory(context), ChannelFramePolicy.filename(channel))
        return file.takeIf { it.isFile && ChannelFramePolicy.isFresh(it.lastModified(),
            System.currentTimeMillis(), MAX_AGE_MS) }
    }

    suspend fun save(context: Context, channel: Channel, bitmap: Bitmap): Boolean =
        withContext(Dispatchers.IO) {
            val dir = directory(context)
            if (!dir.exists() && !dir.mkdirs()) return@withContext false
            val destination = File(dir, ChannelFramePolicy.filename(channel))
            val temp = runCatching { File.createTempFile("snapshot-", ".tmp", dir) }
                .getOrNull() ?: return@withContext false
            try {
                val written = temp.outputStream().buffered().use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 78, out)
                }
                if (!written || temp.length() == 0L) return@withContext false
                if (!temp.renameTo(destination)) return@withContext false
                destination.setLastModified(System.currentTimeMillis())
                prune(dir)
                true
            } catch (_: Exception) {
                false
            } finally {
                temp.delete()
            }
        }

    private fun prune(directory: File) {
        val now = System.currentTimeMillis()
        val files = directory.listFiles()?.filter { it.isFile } ?: return
        files.filter { it.name.endsWith(".tmp") ||
            !ChannelFramePolicy.isFresh(it.lastModified(), now, MAX_AGE_MS) }
            .forEach(File::delete)
        directory.listFiles()?.filter { it.isFile && it.name.endsWith(".jpg") }
            ?.sortedByDescending(File::lastModified)
            ?.drop(MAX_FRAMES)?.forEach(File::delete)
    }
}