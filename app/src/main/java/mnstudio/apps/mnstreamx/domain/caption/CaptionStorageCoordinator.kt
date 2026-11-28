package mnstudio.apps.mnstreamx.domain.caption

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import kotlinx.coroutines.delay

/** A process owns the model tree until its last session or maintenance operation finishes. */
class CaptionStorageCoordinator(private val guard: File? = null) {
    private var sessions = 0
    private var maintenance = false
    private var owner: FileLock? = null

    private fun acquire(): Boolean {
        val path = guard ?: return true
        val channel = try { RandomAccessFile(path, "rw").channel } catch (_: IOException) { return false }
        try {
            owner = channel.tryLock()
            return owner != null
        } catch (_: OverlappingFileLockException) {
            return false
        } catch (_: IOException) {
            return false
        } finally {
            if (owner == null) channel.close()
        }
    }

    private fun release() {
        val lock = owner ?: return
        owner = null
        lock.channel().close() // Closing the channel also drops the operating system's lock.
    }

    @Synchronized fun beginSession(): Boolean {
        if (maintenance || (sessions == 0 && !acquire())) return false
        sessions++
        return true
    }

    suspend fun awaitSession(): Boolean {
        if (beginSession()) return true
        if (guard == null) return false
        // A previous process may still be releasing its native decoder after a channel change.
        repeat(40) {
            delay(150)
            if (beginSession()) return true
        }
        return false
    }

    @Synchronized fun endSession() {
        check(sessions > 0)
        if (--sessions == 0) release()
    }

    @Synchronized fun beginMaintenance(): Boolean {
        if (maintenance || sessions != 0 || !acquire()) return false
        maintenance = true
        return true
    }

    @Synchronized fun endMaintenance() {
        check(maintenance)
        maintenance = false
        release()
    }
}