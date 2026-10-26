package com.yourapp.iptv.domain.diagnostics

import com.yourapp.iptv.domain.model.HomeLanguage
import com.yourapp.iptv.domain.update.UpdateIssue
import com.yourapp.iptv.testing.diagnosticSnapshot
import org.junit.Assert.*
import org.junit.Test

class DiagnosticReportTest {
    @Test fun reportsWhitelistDataInsteadOfAttemptingToMaskArbitraryExceptionOrProviderText() {
        val secrets = listOf("private-user-42","secret-password-99","private-host.invalid","token-X9Y8Z7","AA:BB:CC:DD:EE:FF")
        val malicious = diagnosticSnapshot().copy(version = "https://${secrets[2]}/${secrets[0]}/${secrets[1]}",abis = secrets + "arm64-v8a")
        val event = DiagnosticEvent(1,1000,DiagnosticArea.PLAYBACK,DiagnosticIssue.PLAYBACK,secrets.joinToString(),2001)
        for (language in HomeLanguage.entries) {
            val report = DiagnosticReport.build(malicious,DiagnosticSource.XTREAM,"a".repeat(64),emptyList(),listOf(event),language)
            secrets.forEach { assertFalse(it,report.contains(it)) }
            assertTrue(report.contains("arm64-v8a"));assertTrue(report.contains("code=2001"))
        }
    }
    @Test fun sourceFingerprintsAreConvertedToScopesAndAreNeverExported() {
        val events = listOf(DiagnosticEvent(1,1000,DiagnosticArea.UPDATE,DiagnosticIssue.UPDATE,update = UpdateIssue.NO_RELEASE),
            DiagnosticEvent(2,1000,DiagnosticArea.PLAYBACK,DiagnosticIssue.HTTP,"a".repeat(64),2004,403),
            DiagnosticEvent(3,1000,DiagnosticArea.DETAILS,DiagnosticIssue.TIMEOUT,"b".repeat(64)))
        val text = DiagnosticReport.build(diagnosticSnapshot(),DiagnosticSource.XTREAM,"a".repeat(64),emptyList(),events,HomeLanguage.GERMAN)
        assertFalse(text.contains("a".repeat(64)));assertFalse(text.contains("b".repeat(64)))
        assertTrue(text.contains("Aktuelle Quelle"));assertTrue(text.contains("Andere Quelle"));assertTrue(text.contains("HTTP 403"));assertTrue(text.contains("NO_RELEASE"))
    }
    @Test fun maximumEventAndCheckCountsStayWithinThePortableReportBound() {
        val events = List(1000) { DiagnosticEvent(it.toLong(),Long.MAX_VALUE,DiagnosticArea.CONNECTION,DiagnosticIssue.TLS,"a".repeat(64),1_000_000,599) }
        val checks = List(100) { DiagnosticCheck(DiagnosticArea.SERIES_CATALOG,DiagnosticStatus.NOTICE,DiagnosticIssue.LIMIT,200,Long.MAX_VALUE,Int.MAX_VALUE) }
        for (language in HomeLanguage.entries) {
            val text = DiagnosticReport.build(diagnosticSnapshot(),DiagnosticSource.M3U,null,checks,events,language)
            assertTrue(text.toByteArray().size < DiagnosticReport.MAX_BYTES)
            assertEquals(64,Regex("code=1000000").findAll(text).count())
            assertFalse(text.contains(Int.MAX_VALUE.toString()))
        }
    }
    @Test fun categoryResultsAreNotPresentedAsProofThatAllVideosPlay() {
        val text = DiagnosticReport.build(diagnosticSnapshot(),DiagnosticSource.XTREAM,null,
            listOf(DiagnosticCheck(DiagnosticArea.MOVIE_CATALOG,DiagnosticStatus.NOTICE,DiagnosticIssue.CATEGORIES,200,10,0)),emptyList(),HomeLanguage.ENGLISH)
        assertTrue(text.contains("count=0"));assertTrue(text.contains("do not prove"));assertTrue(text.contains("current app process"))
    }
}