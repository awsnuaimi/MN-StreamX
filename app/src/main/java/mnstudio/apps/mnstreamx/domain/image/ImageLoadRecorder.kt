package mnstudio.apps.mnstreamx.domain.image

/** Constant-size recorder. Tokens belong to listeners; no global collection of requests is kept. */
internal class ImageLoadRecorder(private val nanoTime: () -> Long = System::nanoTime) {
    private data class Sample(val ms: Long, val success: Boolean, val capped: Boolean)
    private val counts = LongArray(ImageLoadResult.entries.size)
    private val recent = ArrayDeque<Sample>()
    private var started = 0L
    private var inFlight = 0L

    @Synchronized fun begin(): Request {
        started = increment(started)
        inFlight = increment(inFlight)
        return Request(nanoTime())
    }

    inner class Request internal constructor(private val start: Long) {
        private var finished = false
        fun finish(result: ImageLoadResult) = synchronized(this@ImageLoadRecorder) {
            if (!finished) {
                finished = true
                inFlight = (inFlight - 1).coerceAtLeast(0)
                counts[result.ordinal] = increment(counts[result.ordinal])
                val ms = (nanoTime() - start).coerceAtLeast(0) / 1_000_000
                if (recent.size == WINDOW) recent.removeFirst()
                recent.addLast(Sample(ms.coerceAtMost(MAX_MS), result != ImageLoadResult.FAILED &&
                    result != ImageLoadResult.CANCELLED, ms > MAX_MS))
            }
        }
    }

    @Synchronized fun snapshot(): ImageLoadSummary {
        val completed = recent.filter { it.success }.map { it.ms }.sorted()
        fun percentile(p: Int) = if (completed.isEmpty()) null else completed[(completed.size * p + 99) / 100 - 1]
        return ImageLoadSummary(started, inFlight,
            counts[ImageLoadResult.MEMORY.ordinal], counts[ImageLoadResult.DISK.ordinal],
            counts[ImageLoadResult.NETWORK.ordinal], counts[ImageLoadResult.LOCAL.ordinal],
            counts[ImageLoadResult.FAILED.ordinal], counts[ImageLoadResult.CANCELLED.ordinal],
            recent.size, recent.lastOrNull()?.ms, percentile(50), percentile(95), completed.lastOrNull(),
            recent.count { it.capped })
    }

    private fun increment(value: Long) = if (value == Long.MAX_VALUE) value else value + 1

    companion object {
        const val WINDOW = 32
        const val MAX_MS = 600_000L
    }
}