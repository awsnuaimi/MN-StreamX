package mnstudio.apps.mnstreamx.domain.diagnostics

import mnstudio.apps.mnstreamx.domain.caption.CaptionDiagnosticJournal
import mnstudio.apps.mnstreamx.domain.model.HomeLanguage
import mnstudio.apps.mnstreamx.domain.model.LiveRecoveryPhase
import mnstudio.apps.mnstreamx.domain.performance.LivePlaybackError
import mnstudio.apps.mnstreamx.domain.performance.LivePlaybackErrorOrigin
import mnstudio.apps.mnstreamx.domain.performance.LivePlaybackErrorStage
import mnstudio.apps.mnstreamx.domain.performance.LivePlaybackHealthSummary
import mnstudio.apps.mnstreamx.domain.performance.LivePlaybackHistoryEntry
import mnstudio.apps.mnstreamx.domain.performance.LivePlaybackInterruption
import mnstudio.apps.mnstreamx.domain.performance.LivePlaybackInterruptionError
import mnstudio.apps.mnstreamx.domain.performance.LivePlaybackInterruptionOutcome
import mnstudio.apps.mnstreamx.domain.performance.LivePlaybackInterruptionTrigger
import mnstudio.apps.mnstreamx.domain.performance.PerformanceOutcome
import mnstudio.apps.mnstreamx.domain.performance.PerformanceRecorder
import mnstudio.apps.mnstreamx.domain.performance.PerformanceScreen
import mnstudio.apps.mnstreamx.domain.performance.PerformanceStage
import mnstudio.apps.mnstreamx.domain.update.UpdateIssue
import mnstudio.apps.mnstreamx.testing.diagnosticSnapshot
import org.junit.Assert.*
import org.junit.Test

class DiagnosticReportTest {
    @Test fun includesOnlyNumericLivePlaybackHealthWithoutStreamIdentifiers() {
        val live = LivePlaybackHealthSummary(
            durationMs = 900_000, firstFrameMs = 2_500, bufferingEvents = 3, bufferingMs = 17_000,
            longestBufferMs = 9_000, recoveryAttempts = 2, playerErrors = 1, finished = true,
            interruptionCount = 1, interruptions = listOf(
                LivePlaybackInterruption(8_400L, 2_000L, 3_000L, 1_500L, 1_000L, 2,
                    LivePlaybackInterruptionOutcome.RECOVERED, connectingMs = 900L, errorCount = 3,
                    errors = listOf(
                        LivePlaybackInterruptionError(LivePlaybackError(LivePlaybackErrorOrigin.WATCHDOG, 2002, null, true),
                            400L, LivePlaybackErrorStage.BUFFERING, 0),
                        LivePlaybackInterruptionError(LivePlaybackError(LivePlaybackErrorOrigin.NETWORK_TIMEOUT, 2002, null, true),
                            5_300L, LivePlaybackErrorStage.CONNECTING, 1),
                        LivePlaybackInterruptionError(LivePlaybackError(LivePlaybackErrorOrigin.PLAYER, 2004, 458, true),
                            7_200L, LivePlaybackErrorStage.LOADING, 2)
                    ))
            ))
        val report = DiagnosticReport.build(diagnosticSnapshot().copy(liveHealth = listOf(live)),
            DiagnosticSource.XTREAM, null, emptyList(), emptyList(), HomeLanguage.GERMAN)
        assertTrue(report.contains("Live-TV-Wiedergabezustand"))
        assertTrue(report.contains("firstFrame=2500 ms"))
        assertTrue(report.contains("maxBuffer=9000 ms"))
        assertTrue(report.contains("retries=2"))
        assertTrue(report.contains("Unterbrechungen nach Wiedergabebeginn"))
        assertTrue(report.contains("interruptions=1"))
        assertTrue(report.contains("RECOVERED | gap=8400 ms | networkWait=2000 ms | retryAfterWait=3000 ms" +
            " | retryDelay=1500 ms | connecting=900 ms | loading=1000 ms | attempts=2 | errors=3"))
        assertTrue(report.contains("at=400 ms | stage=BUFFERING | attempt=0 | origin=WATCHDOG | code=2002 | HTTP=- | retryable=true"))
        assertTrue(report.contains("at=5300 ms | stage=CONNECTING | attempt=1 | origin=NETWORK_TIMEOUT | code=2002 | HTTP=-"))
        assertTrue(report.contains("at=7200 ms | stage=LOADING | attempt=2 | origin=PLAYER | code=2004 | HTTP=458"))
    }
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
    @Test fun savedInterruptionsAppearEvenWhenTheCurrentProcessHasNoPlaybackHistory() {
        val active = LivePlaybackHistoryEntry(
            1_700_000_000_000L, 1_700_000_004_000L,
            LivePlaybackInterruptionTrigger.OUTPUT_STALLED, LiveRecoveryPhase.WAITING_NETWORK, false,
            LivePlaybackInterruption(4_000, 4_000, 0, 0, 0, 0, LivePlaybackInterruptionOutcome.ACTIVE)
        )
        val recovered = active.copy(
            startedAtMs = 1_699_999_990_000L,
            observedAtMs = 1_699_999_995_000L,
            phase = LiveRecoveryPhase.CONNECTING,
            httpOpened = true,
            interruption = active.interruption.copy(durationMs = 5_000, attempts = 1,
                outcome = LivePlaybackInterruptionOutcome.RECOVERED)
        )
        val report = DiagnosticReport.build(diagnosticSnapshot().copy(savedLiveInterruptions = listOf(active, recovered)),
            DiagnosticSource.NONE, null, emptyList(), emptyList(), HomeLanguage.GERMAN)
        assertTrue(report.contains("Gespeicherte Live-TV-Unterbrechungen"))
        assertTrue(report.contains("#1 | startedAt=2023-11-14T22:13:20Z | observedAt=2023-11-14T22:13:24Z"))
        assertTrue(report.contains("trigger=OUTPUT_STALLED | phase=WAITING_NETWORK | httpOpened=false"))
        assertTrue(report.contains("ACTIVE | gap=4000 ms"))
        assertTrue(report.contains("#2 | startedAt=2023-11-14T22:13:10Z | recoveredAt=2023-11-14T22:13:15Z"))
        assertTrue(report.contains("RECOVERED | gap=5000 ms"))
        assertFalse(report.contains("Live-TV-Wiedergabezustand"))
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
        val interruptionError = LivePlaybackInterruptionError(
            LivePlaybackError(LivePlaybackErrorOrigin.NETWORK_TIMEOUT, Int.MAX_VALUE, Int.MAX_VALUE, true),
            Long.MAX_VALUE, LivePlaybackErrorStage.WAITING_NETWORK, Int.MAX_VALUE
        )
        val interruption = LivePlaybackInterruption(Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE,
            Long.MAX_VALUE, Long.MAX_VALUE, Int.MAX_VALUE, LivePlaybackInterruptionOutcome.RECOVERED,
            errorCount = Int.MAX_VALUE, errors = List(100) { interruptionError })
        val live = LivePlaybackHealthSummary(Long.MAX_VALUE, Long.MAX_VALUE, Int.MAX_VALUE, Long.MAX_VALUE,
            Long.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE, false, Int.MAX_VALUE, List(100) { interruption })
        val recorder = PerformanceRecorder { 0L }
        PerformanceStage.entries.filter { it != PerformanceStage.NAV_COMPOSE }.forEach {
            recorder.recordMillis(it, 600_000, PerformanceOutcome.COMPLETE)
        }
        PerformanceScreen.entries.forEach { recorder.recordMillis(PerformanceStage.NAV_COMPOSE, 600_000, screen = it) }
        val archived = LivePlaybackHistoryEntry(Long.MAX_VALUE, Long.MAX_VALUE, LivePlaybackInterruptionTrigger.PLAYER_ERROR,
            LiveRecoveryPhase.WAITING_NETWORK, false, interruption)
        val snapshot = diagnosticSnapshot().copy(liveHealth = List(100) { live }, performance = recorder.snapshot(),
            savedLiveInterruptions = List(100) { archived })
        try {
            repeat(48) { CaptionDiagnosticJournal.record("C".repeat(32), 600_000, "a".repeat(64)) }
            for (language in HomeLanguage.entries) {
                val text = DiagnosticReport.build(snapshot,DiagnosticSource.M3U,null,checks,events,language)
                val savedCount = Regex("trigger=PLAYER_ERROR").findAll(text).count()
                assertTrue(text.toByteArray().size <= DiagnosticReport.MAX_BYTES)
                assertTrue(savedCount in 1..LivePlaybackHistoryEntry.LIMIT)
                assertEquals(64,Regex("code=1000000").findAll(text).count())
                assertEquals(48 + savedCount, Regex("RECOVERED [|] gap=604800000").findAll(text).count())
                assertEquals((48 + savedCount) * 3, Regex("origin=NETWORK_TIMEOUT").findAll(text).count())
                assertTrue(text.contains("code=- | HTTP=-"))
                assertFalse(text.contains(Int.MAX_VALUE.toString()))
            }
        } finally {
            CaptionDiagnosticJournal.clear()
        }
    }
    @Test fun categoryResultsAreNotPresentedAsProofThatAllVideosPlay() {
        val text = DiagnosticReport.build(diagnosticSnapshot(),DiagnosticSource.XTREAM,null,
            listOf(DiagnosticCheck(DiagnosticArea.MOVIE_CATALOG,DiagnosticStatus.NOTICE,DiagnosticIssue.CATEGORIES,200,10,0)),emptyList(),HomeLanguage.ENGLISH)
        assertTrue(text.contains("count=0"));assertTrue(text.contains("do not prove"));assertTrue(text.contains("current app process"))
    }
}