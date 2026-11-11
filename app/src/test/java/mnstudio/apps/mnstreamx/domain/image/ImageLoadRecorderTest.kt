package mnstudio.apps.mnstreamx.domain.image

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class ImageLoadRecorderTest {
    @Test fun terminalEventsCountOnceAndCancelledAndFailedAttemptsDoNotInflateSuccessfulTimings() {
        var now = 0L
        val recorder = ImageLoadRecorder { now }
        fun complete(ms: Long, result: ImageLoadResult) {
            val request = recorder.begin()
            now += ms * 1_000_000
            request.finish(result)
            request.finish(ImageLoadResult.FAILED)
        }
        complete(4, ImageLoadResult.MEMORY)
        complete(20, ImageLoadResult.DISK)
        complete(100, ImageLoadResult.NETWORK)
        complete(8, ImageLoadResult.LOCAL)
        complete(900, ImageLoadResult.FAILED)
        complete(700, ImageLoadResult.CANCELLED)
        val snapshot = recorder.snapshot()
        assertEquals(6L, snapshot.started); assertEquals(0L, snapshot.inFlight)
        assertEquals(listOf(1L, 1L, 1L, 1L, 1L, 1L), listOf(snapshot.memory, snapshot.disk,
            snapshot.network, snapshot.local, snapshot.failed, snapshot.cancelled))
        assertEquals(8L, snapshot.p50Ms); assertEquals(100L, snapshot.p95Ms)
        assertEquals(100L, snapshot.maxMs); assertEquals(700L, snapshot.lastMs)
    }

    @Test fun sampleStorageStaysBoundedWhileTotalsCoverTheWholeSession() {
        var now = 0L
        val recorder = ImageLoadRecorder { now }
        repeat(1000) {
            val request = recorder.begin(); now += 1_000_000
            request.finish(ImageLoadResult.NETWORK)
        }
        val old = recorder.snapshot()
        assertEquals(1000L, old.network); assertEquals(32, old.recent)
        val pending = recorder.begin()
        assertEquals(1L, recorder.snapshot().inFlight)
        assertEquals(0L, old.inFlight)
        pending.finish(ImageLoadResult.CANCELLED)
        assertEquals(1001L, recorder.snapshot().started)
    }

    @Test fun onlyFailuresHaveNoSuccessPercentilesAndLongWaitsAreExplicitlyCapped() {
        var now = 0L
        val recorder = ImageLoadRecorder { now }
        val request = recorder.begin(); now = 700_000L * 1_000_000
        request.finish(ImageLoadResult.FAILED)
        val snapshot = recorder.snapshot()
        assertEquals(600_000L, snapshot.lastMs); assertEquals(1, snapshot.capped)
        assertNull(snapshot.p50Ms); assertNull(snapshot.p95Ms); assertNull(snapshot.maxMs)
        repeat(32) { recorder.begin().finish(ImageLoadResult.MEMORY) }
        assertEquals(0, recorder.snapshot().capped)
    }

    @Test fun failureCategoriesAreBoundedAndDoNotAffectCancellation() {
        val recorder = ImageLoadRecorder()
        recorder.begin().finish(ImageLoadResult.FAILED, ImageFailureCategory.TIMEOUT)
        recorder.begin().finish(ImageLoadResult.FAILED, ImageFailureCategory.HTTP)
        recorder.begin().finish(ImageLoadResult.FAILED, ImageFailureCategory.DECODE)
        recorder.begin().finish(ImageLoadResult.FAILED)
        recorder.begin().finish(ImageLoadResult.CANCELLED, ImageFailureCategory.NETWORK)
        val snapshot = recorder.snapshot()
        assertEquals(4L, snapshot.failed)
        assertEquals(1L, snapshot.cancelled)
        assertEquals(1L, snapshot.failures.timeout)
        assertEquals(1L, snapshot.failures.http)
        assertEquals(1L, snapshot.failures.decode)
        assertEquals(1L, snapshot.failures.other)
        assertEquals(0L, snapshot.failures.network)
    }

    @Test fun concurrentImageCompletionCannotLoseCountsOrLeavePhantomPendingRequests() {
        val recorder = ImageLoadRecorder()
        val pool = Executors.newFixedThreadPool(8)
        try {
            val tasks = (0 until 8).map {
                pool.submit { repeat(1000) {
                    val request = recorder.begin()
                    request.finish(ImageLoadResult.MEMORY)
                    request.finish(ImageLoadResult.CANCELLED)
                } }
            }
            tasks.forEach { it.get(10, TimeUnit.SECONDS) }
            val snapshot = recorder.snapshot()
            assertEquals(8000L, snapshot.started); assertEquals(8000L, snapshot.memory)
            assertEquals(0L, snapshot.cancelled); assertEquals(0L, snapshot.inFlight)
            assertEquals(32, snapshot.recent)
        } finally { pool.shutdownNow() }
    }
}