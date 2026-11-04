package com.yourapp.iptv.domain.diagnostics

import com.yourapp.iptv.domain.image.*
import com.yourapp.iptv.domain.model.HomeLanguage
import com.yourapp.iptv.testing.diagnosticSnapshot
import org.junit.Assert.*
import org.junit.Test

class ImageDiagnosticReportTest {
    @Test fun theSameBoundedImageValuesAreAvailableToTheScreenAndEveryReportLanguage() {
        val recorder = ImageLoadRecorder()
        recorder.begin().finish(ImageLoadResult.NETWORK)
        recorder.begin().finish(ImageLoadResult.MEMORY)
        recorder.begin().finish(ImageLoadResult.CANCELLED)
        val images = ImageDiagnosticsSnapshot(ImageCacheUsage(1024 * 1024, 8 * 1024 * 1024),
            ImageCacheUsage(2 * 1024 * 1024, 32 * 1024 * 1024), recorder.snapshot())
        for (language in HomeLanguage.entries) {
            val text = DiagnosticText(language)
            val report = DiagnosticReport.build(diagnosticSnapshot().copy(images = images), DiagnosticSource.NONE,
                null, emptyList(), emptyList(), language)
            assertTrue(report.contains(ImageDiagnosticReport.title(text)))
            ImageDiagnosticReport.rows(images, text).forEach { (label, value) ->
                assertTrue(report.contains("$label: $value"))
            }
            assertTrue(report.contains("1.00 MiB / 8.00 MiB"))
            assertTrue(report.contains("RAM=1 · DISK=0 · NETWORK=1 · LOCAL=0"))
            assertTrue(report.contains("n=3")); assertTrue(report.contains("capped=0"))
            assertTrue(ImageDiagnosticReport.note(text).contains("HTTP"))
            assertTrue(report.toByteArray().size < DiagnosticReport.MAX_BYTES)
        }
    }

    @Test fun unavailableCachesAndEmptySessionsDoNotInventHitsOrSuccessfulTimings() {
        val snapshot = ImageDiagnosticsSnapshot(null, null, ImageLoadRecorder().snapshot())
        val text = DiagnosticText(HomeLanguage.GERMAN)
        val values = ImageDiagnosticReport.rows(snapshot, text).map { it.second }
        assertEquals(text.unknown(), values[0]); assertEquals(text.unknown(), values[1])
        assertTrue(values.last().contains("last=- p50=- p95=- max=-"))
        assertTrue(values.last().contains("n=0"))
    }
}