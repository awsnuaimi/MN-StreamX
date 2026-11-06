package mnstudio.apps.mnstreamx.domain.diagnostics

import mnstudio.apps.mnstreamx.domain.image.ImageCacheUsage
import mnstudio.apps.mnstreamx.domain.image.ImageDiagnosticsSnapshot
import java.util.Locale

/** Shared labels and values keep the TV screen and portable report consistent. */
object ImageDiagnosticReport {
    fun title(text: DiagnosticText) = text.text("تشخيص الصور", "Image diagnostics", "Bilddiagnose")
    fun note(text: DiagnosticText) = text.text(
        "العدّادات تخص طلبات محمّل الصور المشترك في جلسة التطبيق، وليست عدد طلبات HTTP. DISK قد يشمل ملفات محلية؛ LOCAL مصادر محلية أخرى. الأزمنة من إنشاء مراقب الطلب، وتشمل انتظار دورة الحياة والحجم والطابور، حتى النتيجة؛ لا تقيس ظهور الصورة على الشاشة. آخر 32 محاولة؛ p50 وp95 وmax للناجحة فقط، وlast لأي نتيجة؛ سقف الزمن 600000 ms وعدد التجاوزات في capped. أحجام الكاش لقطة تقريبية، ولا تمثل إجمالي ذاكرة الصور أو GPU. مسح سجل الأحداث لا يصفر هذه العدّادات.",
        "Counters cover shared-loader executions in this app session, not HTTP request counts. DISK may include local files; LOCAL means other local sources. Timings run from listener creation, including lifecycle, size and queue waits, to outcome; they do not measure display readiness. Last 32 attempts; p50, p95 and max use successes only, last uses any outcome; durations cap at 600000 ms, counted in capped. Cache sizes are approximate snapshots, not total image or GPU memory. Clearing events does not reset these counters.",
        "Zähler gelten für Ausführungen des gemeinsamen Bildladers in dieser App-Sitzung, nicht für HTTP-Anfragen. DISK kann lokale Dateien enthalten; LOCAL bezeichnet andere lokale Quellen. Zeiten reichen von der Listener-Erstellung einschließlich Lebenszyklus-, Größen- und Warteschlangenwartezeit bis zum Ergebnis; keine Messung der Bildanzeige. Letzte 32 Versuche; p50, p95 und max nur für Erfolge, last für jedes Ergebnis; Begrenzung auf 600000 ms, gezählt in capped. Cache-Größen sind ungefähre Momentaufnahmen, nicht der gesamte Bild- oder GPU-Speicher. Das Leeren der Ereignisse setzt diese Zähler nicht zurück.")

    fun rows(value: ImageDiagnosticsSnapshot, text: DiagnosticText): List<Pair<String, String>> {
        val loads = value.loads
        fun bytes(value: Long) = String.format(Locale.ROOT, "%.2f MiB", value.coerceAtLeast(0) / 1048576.0)
        fun cache(value: ImageCacheUsage?) = value?.let { "${bytes(it.usedBytes)} / ${bytes(it.limitBytes)}" } ?: text.unknown()
        fun count(value: Long) = value.coerceAtLeast(0)
        return listOf(
            text.text("كاش الصور RAM / الحد", "Image RAM cache / limit", "Bild-RAM-Cache / Grenze") to cache(value.memory),
            text.text("كاش الصور على القرص / الحد", "Image disk cache / limit", "Bild-Datenträgercache / Grenze") to cache(value.disk),
            text.text("الطلبات منذ بدء الجلسة / الجارية", "Session requests / in flight", "Sitzungsanfragen / laufend") to
                "${count(loads.started)} / ${count(loads.inFlight)}",
            text.text("النتائج الناجحة حسب المصدر", "Successful outcomes by source", "Erfolgreiche Ergebnisse nach Quelle") to
                "RAM=${count(loads.memory)} · DISK=${count(loads.disk)} · NETWORK=${count(loads.network)} · LOCAL=${count(loads.local)}",
            text.text("الفشل / الإلغاء", "Failed / cancelled", "Fehlgeschlagen / abgebrochen") to
                "${count(loads.failed)} / ${count(loads.cancelled)}",
            text.text("أزمنة آخر المحاولات", "Recent attempt timings", "Zeiten der letzten Versuche") to
                "n=${loads.recent.coerceIn(0, 32)} | last=${loads.lastMs ?: "-"} p50=${loads.p50Ms ?: "-"}" +
                " p95=${loads.p95Ms ?: "-"} max=${loads.maxMs ?: "-"} ms | capped=${loads.capped.coerceIn(0, 32)}"
        )
    }
}