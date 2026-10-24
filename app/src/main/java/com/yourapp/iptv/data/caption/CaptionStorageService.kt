package com.yourapp.iptv.data.caption

import android.content.Context
import android.os.StatFs
import com.yourapp.iptv.domain.caption.CaptionStorageCoordinator
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Storage measurement and bounded maintenance of downloaded offline-caption models. */
data class CaptionStorageSnapshot(val freeBytes: Long, val totalBytes: Long, val modelBytes: Long)

class CaptionStorageService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val coordinator: CaptionStorageCoordinator
) {
    private val modelFolder get() = CaptionModelStorage(context.noBackupFilesDir.resolve("caption-models"))

    suspend fun snapshot(): CaptionStorageSnapshot = withContext(Dispatchers.IO) {
        val space = StatFs(context.noBackupFilesDir.absolutePath)
        CaptionStorageSnapshot(space.availableBytes, space.totalBytes, modelFolder.bytes())
    }

    /** Returns null when maintenance is unsafe because caption work is active. */
    suspend fun deleteDownloadedModels(): Long? {
        if (!coordinator.beginMaintenance()) return null
        return try {
            withContext(Dispatchers.IO) { modelFolder.clear() }
        } finally {
            coordinator.endMaintenance()
        }
    }
}