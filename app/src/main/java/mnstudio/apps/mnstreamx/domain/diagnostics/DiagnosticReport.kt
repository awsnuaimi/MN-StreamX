package mnstudio.apps.mnstreamx.domain.diagnostics

import mnstudio.apps.mnstreamx.domain.model.HomeLanguage
import mnstudio.apps.mnstreamx.domain.caption.CaptionDiagnosticJournal
import java.time.Instant
import mnstudio.apps.mnstreamx.domain.performance.LivePlaybackHealthRecorder
import mnstudio.apps.mnstreamx.domain.performance.LivePlaybackHealthSession
import mnstudio.apps.mnstreamx.domain.performance.LivePlaybackHistoryEntry
import mnstudio.apps.mnstreamx.domain.performance.LivePlaybackInterruption
import mnstudio.apps.mnstreamx.domain.performance.LivePlaybackInterruptionOutcome
import mnstudio.apps.mnstreamx.domain.performance.PerformanceReport

object DiagnosticReport {
    const val MAX_BYTES = 64 * 1024
    fun build(snapshot: DiagnosticSnapshot, source: DiagnosticSource, active: String?, checks: List<DiagnosticCheck>,
        events: List<DiagnosticEvent>, language: HomeLanguage): String {
        val text = DiagnosticText(language)
        val result = buildString {
            appendLine("MN StreamX — ${text.title()}")
            appendLine(text.privacy());appendLine(text.sessionNote());appendLine()
            appendLine(text.device())
            DiagnosticSummary.rows(snapshot,text).forEach { (name,value) -> appendLine("$name: $value") }
            appendLine("${text.current()}: ${source.name}");appendLine()
            snapshot.images?.let { images ->
                appendLine(ImageDiagnosticReport.title(text));appendLine(ImageDiagnosticReport.note(text))
                ImageDiagnosticReport.rows(images,text).forEach { (name,value) -> appendLine("$name: $value") }
                appendLine()
            }
            if (snapshot.performance.isNotEmpty()) {
                appendLine(PerformanceReport.title(language));appendLine(PerformanceReport.note(language))
                PerformanceReport.rows(snapshot.performance).forEach { appendLine(it) };appendLine()
            }
            if (snapshot.liveHealth.isNotEmpty()) {
                appendLine(text.text("صحة تشغيل Live TV (آخر 8 جلسات)",
                    "Live TV playback health (last 8 selections)",
                    "Live-TV-Wiedergabezustand (letzte 8 Auswahlen)"))
                appendLine(text.text("قياسات جلسة التطبيق فقط؛ ليست دليلًا على سبب انقطاع البث.",
                    "Process-local measurements; not proof of a stream outage cause.",
                    "Messwerte dieser App-Sitzung; keine Aussage zur Ursache eines Streamausfalls."))
                appendLine(text.text(
                    "التوقّفات بعد بدء التشغيل، والأحدث أولاً. عند تداخل انقطاع الشبكة مع مهلة الخادم، يُحتسب الوقت ضمن انتظار الشبكة. ينتهي فتح الاتصال عند أول استجابة HTTP ناجحة.",
                    "Interruptions after playback begins, newest first. Network wait takes priority over overlapping server cooldown. Connecting ends at the first successful HTTP response.",
                    "Unterbrechungen nach Wiedergabebeginn, neueste zuerst. Bei gleichzeitiger Netz- und Server-Wartezeit zählt die Netzwartezeit. Der Verbindungsaufbau endet mit der ersten erfolgreichen HTTP-Antwort."
                ))
                appendLine(text.text(
                    "أول خطأ وآخر خطأين لكل توقّف. المحاولة 0 تسبق إعادة الاتصال. WATCHDOG مؤقّت داخل التطبيق، وNETWORK_TIMEOUT مهلة اتصال أو قراءة أبلغ عنها المشغّل.",
                    "First and last two errors per interruption. Attempt 0 is before reconnecting. WATCHDOG is the app timer; NETWORK_TIMEOUT is a connection or read timeout reported by the player.",
                    "Erster und letzte zwei Fehler je Unterbrechung. Versuch 0 liegt vor dem erneuten Verbindungsaufbau. WATCHDOG ist der App-Timer; NETWORK_TIMEOUT ist ein vom Player gemeldetes Verbindungs- oder Lesezeitlimit."
                ))
                snapshot.liveHealth.take(LivePlaybackHealthRecorder.LIMIT).forEachIndexed { index, item ->
                    val firstFrame = item.firstFrameMs?.coerceIn(0, 604_800_000)?.toString() ?: "-"
                    appendLine("#${index + 1} | duration=${item.durationMs.coerceIn(0, 604_800_000)} ms" +
                        " | firstFrame=$firstFrame ms | buffers=${item.bufferingEvents.coerceIn(0, 1_000_000)}" +
                        " | buffered=${item.bufferingMs.coerceIn(0, 604_800_000)} ms" +
                        " | maxBuffer=${item.longestBufferMs.coerceIn(0, 604_800_000)} ms" +
                        " | retries=${item.recoveryAttempts.coerceIn(0, 1_000_000)}" +
                        " | errors=${item.playerErrors.coerceIn(0, 1_000_000)} | finished=${item.finished}" +
                        " | interruptions=${item.interruptionCount.coerceIn(0, 1_000_000)}")
                    item.interruptions.take(LivePlaybackHealthSession.MAX_INTERRUPTION_DETAILS).forEachIndexed { number, incident ->
                        appendInterruption(incident, "  ${number + 1}. ")
                    }
                }
                appendLine()
            }
            val captionMeasurements = CaptionDiagnosticJournal.snapshot()
            if (captionMeasurements.isNotEmpty()) {
                appendLine("Caption diagnostics / Spracherkennungsdiagnose (current session)")
                captionMeasurements.forEach { appendLine(it) }
                appendLine()
            }
            appendLine(text.checks())
            checks.take(12).forEach { check ->
                append("${text.area(check.area)}: ${text.status(check.status)} — ${text.issue(check.issue)}")
                check.httpStatus?.takeIf { it in 100..599 }?.let { append(" | HTTP $it") }
                check.count?.takeIf { it in 0..20_000 }?.let { append(" | count=$it") }
                appendLine(" | ${check.elapsedMs.coerceIn(0,120_000)} ms")
            }
            if (checks.isEmpty()) appendLine(text.idle())
            appendLine(text.categoryNote());appendLine();appendLine(text.events())
            events.take(DiagnosticJournal.LIMIT).forEach { event ->
                val scope = when { event.sourceKey == null -> text.app();event.sourceKey == active -> text.current();else -> text.other() }
                val time = event.timeMs.reportTime()
                appendLine("$time | $scope | ${text.area(event.area)} | ${text.issue(event.issue)} | ${DiagnosticSummary.technical(event)}")
            }
            if (events.isEmpty()) appendLine(text.noEvents())
        }
        val availableBytes = MAX_BYTES - result.utf8Size()
        require(availableBytes >= 0)
        return result + savedInterruptions(snapshot.savedLiveInterruptions, text, availableBytes)
    }

    private fun savedInterruptions(
        entries: List<LivePlaybackHistoryEntry>,
        text: DiagnosticText,
        availableBytes: Int
    ): String {
        if (entries.isEmpty()) return ""
        val heading = buildString {
            appendLine()
            appendLine(text.text("انقطاعات Live TV المحفوظة",
                "Saved live TV interruptions", "Gespeicherte Live-TV-Unterbrechungen"))
            appendLine(text.text(
                "حتى ${LivePlaybackHistoryEntry.LIMIT} انقطاعًا عبر جلسات التطبيق بحسب المساحة المتاحة، والأحدث أولًا. يعرض ACTIVE آخر حالة مسجلة لانقطاع لم يكتمل استرجاعه.",
                "Up to ${LivePlaybackHistoryEntry.LIMIT} interruptions across app sessions as space permits, newest first. ACTIVE describes the last recorded state of an unfinished interruption.",
                "Bis zu ${LivePlaybackHistoryEntry.LIMIT} Unterbrechungen über mehrere App-Sitzungen, je nach Platz, neueste zuerst. ACTIVE zeigt den zuletzt erfassten Zustand einer noch offenen Unterbrechung."
            ))
        }
        var remaining = availableBytes - heading.utf8Size()
        val rows = buildString {
            for ((index, entry) in entries.take(LivePlaybackHistoryEntry.LIMIT).withIndex()) {
                val timeLabel = if (entry.interruption.outcome == LivePlaybackInterruptionOutcome.RECOVERED) {
                    "recoveredAt"
                } else {
                    "observedAt"
                }
                val row = buildString {
                    appendLine("#${index + 1} | startedAt=${entry.startedAtMs.reportTime()}" +
                        " | $timeLabel=${entry.observedAtMs.reportTime()} | trigger=${entry.trigger.name}" +
                        " | phase=${entry.phase.name} | httpOpened=${entry.httpOpened}")
                    appendInterruption(entry.interruption, "  ")
                }
                val bytes = row.utf8Size()
                if (bytes > remaining) break
                append(row)
                remaining -= bytes
            }
        }
        return if (rows.isEmpty()) "" else heading + rows
    }

    private fun StringBuilder.appendInterruption(incident: LivePlaybackInterruption, prefix: String) {
        appendLine("$prefix${incident.outcome.name}" +
            " | gap=${incident.durationMs.reportDuration()} ms" +
            " | networkWait=${incident.networkWaitMs.reportDuration()} ms" +
            " | retryAfterWait=${incident.retryAfterWaitMs.reportDuration()} ms" +
            " | retryDelay=${incident.retryDelayMs.reportDuration()} ms" +
            " | connecting=${incident.connectingMs.reportDuration()} ms" +
            " | loading=${incident.loadingMs.reportDuration()} ms" +
            " | attempts=${incident.attempts.coerceIn(0, 1_000_000)}" +
            " | errors=${incident.errorCount.coerceIn(0, 1_000_000)}")
        incident.errors.take(LivePlaybackHealthSession.MAX_ERROR_DETAILS).forEach { entry ->
            val error = entry.error
            val code = error.code.takeIf { it in -1_000_000..1_000_000 }?.toString() ?: "-"
            val http = error.httpStatus?.takeIf { it in 100..599 }?.toString() ?: "-"
            appendLine("    at=${entry.offsetMs.reportDuration()} ms | stage=${entry.stage.name}" +
                " | attempt=${entry.attempt.coerceIn(0, 1_000_000)} | origin=${error.origin.name}" +
                " | code=$code | HTTP=$http | retryable=${error.retryable}")
        }
    }

    private fun Long.reportTime(): Instant = Instant.ofEpochMilli(coerceIn(0, 4_102_444_800_000L))
    private fun Long.reportDuration(): Long = coerceIn(0L, LivePlaybackHealthSession.MAX_DURATION_MS)
    private fun String.utf8Size(): Int = toByteArray(Charsets.UTF_8).size
}