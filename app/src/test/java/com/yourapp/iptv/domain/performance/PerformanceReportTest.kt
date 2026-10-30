package com.yourapp.iptv.domain.performance

import com.yourapp.iptv.domain.diagnostics.*
import com.yourapp.iptv.domain.model.HomeLanguage
import com.yourapp.iptv.testing.diagnosticSnapshot
import org.junit.Assert.*
import org.junit.Test

class PerformanceReportTest {
    @Test fun reportsIncludeWhitelistedMetricsAndDescribeTheirActualBoundary() {
        val recorder = PerformanceRecorder();recorder.recordMillis(PerformanceStage.NAV_COMPOSE,14,screen = PerformanceScreen.SETTINGS)
        recorder.recordMillis(PerformanceStage.PROFILE_COMMIT,12)
        for (language in HomeLanguage.entries) {
            val report = DiagnosticReport.build(diagnosticSnapshot().copy(performance = recorder.snapshot()),DiagnosticSource.NONE,null,emptyList(),emptyList(),language)
            assertTrue(report.contains("NAV_COMPOSE/SETTINGS"));assertTrue(report.contains("PROFILE_COMMIT"));assertTrue(report.contains("p95=12"))
            assertTrue(report.toByteArray().size < DiagnosticReport.MAX_BYTES)
        }
    }
    @Test fun liveStagesExportOnlyNumericOutcomesAndExplainTheirActualBoundary() {
        val recorder = PerformanceRecorder()
        recorder.recordMillis(PerformanceStage.LIVE_PROJECT, 40)
        recorder.recordMillis(PerformanceStage.LIVE_FOCUS, 8)
        recorder.recordMillis(PerformanceStage.LIVE_FOCUS, 200, PerformanceOutcome.CANCELLED)
        for (language in HomeLanguage.entries) {
            val note = PerformanceReport.note(language)
            assertTrue(note.contains("LIVE_PROJECT")); assertTrue(note.contains("LIVE_FOCUS"))
            val report = DiagnosticReport.build(diagnosticSnapshot().copy(performance = recorder.snapshot()),
                DiagnosticSource.NONE, null, emptyList(), emptyList(), language)
            assertTrue(report.contains("LIVE_PROJECT | n=1"))
            assertTrue(report.contains("LIVE_FOCUS | n=2 ok=1 fail=0 cancel=1"))
            assertTrue(report.contains("p95=8")); assertTrue(report.toByteArray().size < DiagnosticReport.MAX_BYTES)
        }
    }
    @Test fun invalidAndDuplicateNumericSummariesAreDroppedAndExportsRemainBounded() {
        val valid = PerformanceSummary(PerformanceStage.ACTIVITY_HOME,null,1,1,0,0,5,5,5,5,0)
        val rows = List(1000) { valid.copy(lastMs = Long.MAX_VALUE) } + List(1000) { valid }
        assertEquals(1,PerformanceReport.rows(rows).size)
        val invalid = valid.copy(complete = 2);assertFalse(invalid.valid())
        assertFalse(valid.copy(screen = PerformanceScreen.HOME).valid())
    }
}