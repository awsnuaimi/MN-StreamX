package com.yourapp.iptv.domain.performance

import com.yourapp.iptv.domain.model.HomeLanguage

object PerformanceReport {
    fun title(language: HomeLanguage) = when (language) {
        HomeLanguage.ARABIC -> "قياسات الأداء في الجلسة"
        HomeLanguage.ENGLISH -> "Session performance measurements"
        HomeLanguage.GERMAN -> "Leistungsmessungen der Sitzung"
    }
    fun note(language: HomeLanguage) = when (language) {
        HomeLanguage.ARABIC -> "آخر 32 عينة لكل مسار. p50 وp95 وmax للعمليات المكتملة فقط؛ last لآخر محاولة. ACTIVITY_HOME يبدأ عند إنشاء النشاط ويشمل انتظار الحماية. NAV_COMPOSE ينتهي بعد إشارتَي إطار؛ لا يثبت اكتمال المحتوى أو التشغيل. LIVE_PROJECT يشمل انتظار عامل تجهيز الصفوف؛ LIVE_FOCUS من طلب التنقل حتى وصول التركيز، ولا يقيس الرسم أو تشغيل الفيديو. القيم فوق 600000 ms تُحدّ ويظهر عددها في capped."
        HomeLanguage.ENGLISH -> "Last 32 samples per path. p50, p95 and max use completed operations only; last includes every outcome. ACTIVITY_HOME starts at Activity creation and includes protection waits. NAV_COMPOSE ends after two frame ticks, not content or playback readiness. LIVE_PROJECT includes row-worker dispatch waits; LIVE_FOCUS runs from a navigation request to confirmed row focus, not drawing or video readiness. Values above 600000 ms are capped and counted."
        HomeLanguage.GERMAN -> "Letzte 32 Messungen je Pfad. p50, p95 und max nur für abgeschlossene Vorgänge; last umfasst jedes Ergebnis. ACTIVITY_HOME beginnt bei Activity-Erstellung und enthält Schutzwartezeiten. NAV_COMPOSE endet nach zwei Frames und bestätigt keine Inhalts- oder Wiedergabebereitschaft. LIVE_PROJECT enthält die Wartezeit auf den Zeilen-Worker; LIVE_FOCUS reicht vom Navigationswunsch bis zum bestätigten Zeilenfokus und misst weder Zeichnen noch Videobereitschaft. Werte über 600000 ms werden begrenzt und gezählt."
    }
    fun rows(values: List<PerformanceSummary>): List<String> = values.asSequence().filter { it.valid() }
        .distinctBy { it.stage to it.screen }.take(PerformanceStage.entries.size + PerformanceScreen.entries.size).map { row ->
            val name = row.stage.name + (row.screen?.let { "/${it.name}" } ?: "")
            "$name | n=${row.samples} ok=${row.complete} fail=${row.failed} cancel=${row.cancelled}" +
                " | last=${row.lastMs} p50=${row.p50Ms ?: "-"} p95=${row.p95Ms ?: "-"} max=${row.maxMs ?: "-"} ms | capped=${row.clamped}"
        }.toList()
}