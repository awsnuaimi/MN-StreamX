package com.yourapp.iptv.domain.diagnostics

import com.yourapp.iptv.domain.model.HomeLanguage
import java.time.Instant

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