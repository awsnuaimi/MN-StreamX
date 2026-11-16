package mnstudio.apps.mnstreamx.data.diagnostics

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import mnstudio.apps.mnstreamx.domain.performance.LivePlaybackHistoryEntry
import mnstudio.apps.mnstreamx.domain.performance.LivePlaybackHistorySink

internal class DeviceLivePlaybackHistory(
    file: () -> File,
    scope: CoroutineScope
) : LivePlaybackHistorySink {
    private val atomic by lazy { AtomicFile(file()) }
    private val lock = Any()
    private val entries = LinkedHashMap<Any, LivePlaybackHistoryEntry>()
    private var ignoreSavedEntries = false
    private val changes = Channel<Unit>(Channel.CONFLATED)

    private val restore = scope.async(start = CoroutineStart.LAZY) {
        val saved = readFile()
        synchronized(lock) {
            if (ignoreSavedEntries || saved.isEmpty()) return@synchronized
            val pending = entries.toMap()
            entries.clear()
            saved.asReversed().forEach { add(Any(), it) }
            pending.forEach(::add)
        }
    }
    private val writer = scope.launch(start = CoroutineStart.LAZY) {
        restore.await()
        for (signal in changes) writeFile(currentEntries())
    }

    override fun record(key: Any, entry: LivePlaybackHistoryEntry, isStart: Boolean) {
        synchronized(lock) {
            // A late state change must not restore an evicted or cleared interruption.
            if (!isStart && key !in entries) return
            add(key, entry)
        }
        requestWrite()
    }

    override fun clear() {
        synchronized(lock) {
            ignoreSavedEntries = true
            entries.clear()
        }
        requestWrite()
    }

    suspend fun snapshot(): List<LivePlaybackHistoryEntry> {
        restore.await()
        return currentEntries()
    }

    private fun add(key: Any, entry: LivePlaybackHistoryEntry) {
        entries[key] = entry
        if (entries.size > LivePlaybackHistoryEntry.LIMIT) entries.remove(entries.keys.first())
    }

    private fun requestWrite() {
        changes.trySend(Unit)
        writer.start()
    }

    private fun currentEntries(): List<LivePlaybackHistoryEntry> =
        synchronized(lock) { entries.values.toList().asReversed() }

    private fun readFile(): List<LivePlaybackHistoryEntry> = try {
        atomic.openRead().use { input ->
            require(input.channel.size() <= LivePlaybackHistoryCodec.MAX_BYTES)
            LivePlaybackHistoryCodec.read(input)
        }
    } catch (_: IOException) {
        emptyList()
    } catch (_: IllegalArgumentException) {
        emptyList()
    } catch (_: SecurityException) {
        emptyList()
    }

    private fun writeFile(entries: List<LivePlaybackHistoryEntry>) {
        try {
            if (entries.isEmpty()) {
                atomic.delete()
                return
            }
            val output = atomic.startWrite()
            var committed = false
            try {
                LivePlaybackHistoryCodec.write(output, entries)
                atomic.finishWrite(output)
                committed = true
            } finally {
                if (!committed) atomic.failWrite(output)
            }
        } catch (_: IOException) {
            // Keep the current observations available in memory if private storage is unavailable.
        } catch (_: SecurityException) {
            // The journal is best effort. A storage failure must not interrupt playback.
        }
    }

    companion object {
        private var instance: DeviceLivePlaybackHistory? = null

        @Synchronized fun forApp(context: Context): DeviceLivePlaybackHistory {
            val app = context.applicationContext
            return instance ?: DeviceLivePlaybackHistory(
                { app.noBackupFilesDir.resolve("live_playback_history.bin") },
                CoroutineScope(SupervisorJob() + Dispatchers.IO)
            ).also { instance = it }
        }
    }
}