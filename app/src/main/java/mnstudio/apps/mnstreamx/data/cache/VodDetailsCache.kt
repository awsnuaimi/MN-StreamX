package mnstudio.apps.mnstreamx.data.cache

import mnstudio.apps.mnstreamx.domain.model.VodDetails
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Short-lived, memory-only metadata. No signed playback URL is written to disk. */
internal class VodDetailsCache(private val clock: () -> Long = { System.nanoTime() / 1_000_000 }) {
    private data class Entry(val value: VodDetails, val at: Long, val episodes: Int, val weight: Long)
    private data class Gate(val mutex: Mutex = Mutex(), var users: Int = 0)
    private val lock = Any()
    private val entries = LinkedHashMap<String, Entry>(16, .75f, true)
    private val gates = HashMap<String, Gate>()

    suspend fun get(key: String, ttlMs: Long, refresh: Boolean = false, fetch: suspend () -> VodDetails): VodDetails {
        if (!refresh) cached(key, ttlMs)?.let { return it }
        val gate = synchronized(lock) { gates.getOrPut(key) { Gate() }.also { it.users++ } }
        try {
            return gate.mutex.withLock {
                if (!refresh) cached(key, ttlMs)?.let { return@withLock it }
                // A failed explicit refresh must not resurrect the old signed target on the next attempt.
                if (refresh) synchronized(lock) { entries.remove(key) }
                val result = fetch()
                currentCoroutineContext().ensureActive()
                val size = result.seasons.sumOf { it.episodes.size }
                val weight = if (size <= MAX_EPISODES) weight(result) else MAX_WEIGHT + 1
                synchronized(lock) {
                    if (size <= MAX_EPISODES && weight <= MAX_WEIGHT) {
                        entries[key] = Entry(result, clock(), size, weight)
                        while (entries.size > 12 || entries.values.sumOf { it.episodes } > MAX_EPISODES ||
                            entries.values.sumOf { it.weight } > MAX_WEIGHT) {
                            entries.entries.iterator().apply { next(); remove() }
                        }
                    }
                }
                result
            }
        } finally {
            synchronized(lock) { if (--gate.users == 0 && gates[key] === gate) gates.remove(key) }
        }
    }

    private fun cached(key: String, ttl: Long): VodDetails? = synchronized(lock) {
        val value = entries[key] ?: return@synchronized null
        val age = clock() - value.at
        if (age in 0 until ttl) value.value else { entries.remove(key); null }
    }

    /** Approximate retained data, including strings; episode count alone cannot bound long plots. */
    private fun weight(details: VodDetails): Long {
        fun text(vararg values: String?) = values.sumOf { (it?.length ?: 0).toLong() * 2 }
        val item = details.item
        var total = 1024L + text(item.id,item.name,item.categoryId,item.poster,item.extension,item.plot,item.rating,item.year,
            details.playback?.url) + item.categoryIds.sumOf { it.length.toLong() * 2 }
        details.seasons.forEach { season ->
            total += 256 + text(season.name)
            season.episodes.forEach { episode ->
                total += 256 + text(episode.id,episode.name,episode.extension,episode.plot)
                if (total > MAX_WEIGHT) return total
            }
        }
        return total
    }
    companion object {
        private const val MAX_EPISODES = 5_000
        private const val MAX_WEIGHT = 4L * 1024 * 1024
    }
}