package mnstudio.apps.mnstreamx.domain.diagnostics

import mnstudio.apps.mnstreamx.domain.model.HomeLanguage
import mnstudio.apps.mnstreamx.domain.caption.CaptionDiagnosticJournal
import java.time.Instant
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
                appendLine(text.text("startupBuffer = التخزين المؤقت عند بدء القناة؛ rebuffer = التوقف بعد أول إطار. FIRST_FRAME العام يساوي أول إطار لكل اختيار، وليس لكل محاولة اتصال.",
                    "startupBuffer means initial loading; rebuffer means interruptions after first frame. FIRST_FRAME is recorded once per selection, not reconnect.",
                    "startupBuffer bedeutet anfängliches Laden; rebuffer sind Unterbrechungen nach dem ersten Bild. FIRST_FRAME wird einmal je Auswahl gemessen, nicht je Neuverbindung."))
                snapshot.liveHealth.take(8).forEachIndexed { index, item ->
                    val firstFrame = item.firstFrameMs?.coerceIn(0, 604_800_000)?.toString() ?: "-"
                    appendLine("#${index + 1} | duration=${item.durationMs.coerceIn(0, 604_800_000)} ms" +
                        " | firstFrame=$firstFrame ms | buffers=${item.bufferingEvents.coerceIn(0, 1_000_000)}" +
                        " | buffered=${item.bufferingMs.coerceIn(0, 604_800_000)} ms" +
                        " | maxBuffer=${item.longestBufferMs.coerceIn(0, 604_800_000)} ms" +
                        " | startupBuffers=${item.startupBufferingEvents.coerceIn(0, 1_000_000)}" +
                        " | startupBuffered=${item.startupBufferingMs.coerceIn(0, 604_800_000)} ms" +
                        " | rebuffers=${item.rebufferingEvents.coerceIn(0, 1_000_000)}" +
                        " | rebuffered=${item.rebufferingMs.coerceIn(0, 604_800_000)} ms" +
                        " | maxRebuffer=${item.longestRebufferMs.coerceIn(0, 604_800_000)} ms" +
                        " | retries=${item.recoveryAttempts.coerceIn(0, 1_000_000)}" +
                        " | errors=${item.playerErrors.coerceIn(0, 1_000_000)} | finished=${item.finished}")
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
                val time = Instant.ofEpochMilli(event.timeMs.coerceIn(0,4_102_444_800_000L))
                appendLine("$time | $scope | ${text.area(event.area)} | ${text.issue(event.issue)} | ${DiagnosticSummary.technical(event)}")
            }
            if (events.isEmpty()) appendLine(text.noEvents())
        }
        require(result.toByteArray(Charsets.UTF_8).size <= MAX_BYTES)
        return result
    }
}