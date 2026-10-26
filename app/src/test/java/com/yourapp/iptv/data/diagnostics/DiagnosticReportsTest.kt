package com.yourapp.iptv.data.diagnostics

import com.yourapp.iptv.domain.diagnostics.DiagnosticReport
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class DiagnosticReportsTest {
    @Test fun exportsAreBoundedAndRetainAtMostThreeOwnedReportsWithoutDeletingUnrelatedFiles() {
        val directory = Files.createTempDirectory("diagnostic-reports-test").toFile()
        try {
            val unrelated = directory.resolve("unrelated.txt").apply { writeText("preserve") }
            var last: java.io.File? = null
            repeat(8) { last = DiagnosticReports(directory).write("Synthetic report $it") }
            assertEquals(3,directory.listFiles()!!.count { it.name.startsWith("mnstreamx-diagnostics-") })
            assertTrue(last!!.isFile);assertEquals("Synthetic report 7",last!!.readText());assertEquals("preserve",unrelated.readText())
        } finally { directory.deleteRecursively() }
    }
    @Test fun anOversizedExportIsRejectedBeforeAFileIsCreated() {
        val directory = Files.createTempDirectory("diagnostic-report-bound").toFile()
        try {
            try { DiagnosticReports(directory).write("ع".repeat(DiagnosticReport.MAX_BYTES));fail("Expected size rejection") }
            catch (_: IllegalArgumentException) { assertTrue(directory.listFiles()!!.isEmpty()) }
        } finally { directory.deleteRecursively() }
    }
}