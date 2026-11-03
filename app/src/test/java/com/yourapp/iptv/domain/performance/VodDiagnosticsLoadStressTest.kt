package com.yourapp.iptv.domain.performance

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

/** Many synthetic measurements must remain bounded, numeric, and race-safe. */
class VodDiagnosticsLoadStressTest {
    @Test fun concurrentVodSamplesNeverExpandRecorderWindows() {
        val recorder = PerformanceRecorder()
        val pool = Executors.newFixedThreadPool(4)
        val stages = listOf(PerformanceStage.VOD_CACHE_READ, PerformanceStage.VOD_JSON_PARSE,
            PerformanceStage.VOD_PROVIDER_FETCH, PerformanceStage.VOD_GRID_LAYOUT)
        try {
            val tasks = stages.map { stage -> pool.submit {
                repeat(5_000) { index -> recorder.recordMillis(stage, index.toLong()) }
            } }
            tasks.forEach { it.get(15, TimeUnit.SECONDS) }
            val summaries = recorder.snapshot()
            assertEquals(stages.size, summaries.size)
            assertTrue(summaries.all { it.samples == 32 && it.valid() })
            assertTrue(summaries.all { it.maxMs == 4_999L })
        } finally { pool.shutdownNow() }
    }

    @Test fun negativeAndHugeDurationsAreClampedWithoutStoringSensitiveLabels() {
        val recorder = PerformanceRecorder()
        repeat(300) { index ->
            recorder.recordMillis(PerformanceStage.VOD_CACHE_DISK_LOAD,
                if (index % 2 == 0) -1 else Long.MAX_VALUE)
        }
        val summary = recorder.snapshot().single()
        assertEquals(32, summary.samples)
        assertEquals(32, summary.clamped)
        assertTrue(summary.valid())
        assertTrue(summary.p50Ms in listOf(0L, 600_000L))
        assertEquals(600_000L, summary.maxMs)
    }
}