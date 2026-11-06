package mnstudio.apps.mnstreamx.domain.performance

import mnstudio.apps.mnstreamx.domain.model.HomeLanguage

object PerformanceReport {
    fun title(language: HomeLanguage) = when (language) {
        HomeLanguage.ARABIC -> "قياسات الأداء في الجلسة"
        HomeLanguage.ENGLISH -> "Session performance measurements"
        HomeLanguage.GERMAN -> "Leistungsmessungen der Sitzung"
    }
    fun note(language: HomeLanguage) = when (language) {
        HomeLanguage.ARABIC -> "آخر 32 عينة لكل مسار. p50 وp95 وmax للعمليات المكتملة فقط؛ last لآخر محاولة. ACTIVITY_HOME يبدأ عند إنشاء النشاط ويشمل انتظار الحماية. NAV_COMPOSE ينتهي بعد إشارتَي إطار؛ لا يثبت اكتمال المحتوى أو التشغيل. LIVE_PROJECT يشمل انتظار عامل تجهيز الصفوف؛ LIVE_FOCUS من طلب التنقل حتى وصول التركيز، ولا يقيس الرسم أو تشغيل الفيديو. PRESENTATION_WAIT لانتظار دور تجهيز القوائم وPRESENTATION_WORK للمعالجة بعد بدء الدور. قياسات VOD تفصل البحث في ذاكرة RAM عن قراءة الكاش من القرص (وتشمل المحاولات الفارغة)، ثم جلب الخادم وكتابة الكاش وأول نتيجة للكتالوج والتصنيف وتهيئة التصنيف المجاور وتحليل JSON وأول تخطيط لشبكة VOD، دون معرفات الاشتراكات. تخطيط الشبكة لا يضمن تحميل الصور أو اكتمال الرسم. القيم فوق 600000 ms تُحدّ ويظهر عددها في capped."
        HomeLanguage.ENGLISH -> "Last 32 samples per path. p50, p95 and max use completed operations only; last includes every outcome. ACTIVITY_HOME starts at Activity creation and includes protection waits. NAV_COMPOSE ends after two frame ticks, not content or playback readiness. LIVE_PROJECT includes row-worker dispatch waits; LIVE_FOCUS runs from a navigation request to confirmed row focus, not drawing or video readiness. PRESENTATION_WAIT measures list-worker queue waits and PRESENTATION_WORK the work after admission. VOD separates RAM lookups and disk cache reads (including misses), provider fetches, cache writes, first catalog/category results and adjacent-category warmup and JSON parsing plus first VOD grid layout without account IDs. Grid layout does not mean poster images are loaded. Values above 600000 ms are capped and counted."
        HomeLanguage.GERMAN -> "Letzte 32 Messungen je Pfad. p50, p95 und max nur für abgeschlossene Vorgänge; last umfasst jedes Ergebnis. ACTIVITY_HOME beginnt bei Activity-Erstellung und enthält Schutzwartezeiten. NAV_COMPOSE endet nach zwei Frames und bestätigt keine Inhalts- oder Wiedergabebereitschaft. LIVE_PROJECT enthält die Wartezeit auf den Zeilen-Worker; LIVE_FOCUS reicht vom Navigationswunsch bis zum bestätigten Zeilenfokus und misst weder Zeichnen noch Videobereitschaft. PRESENTATION_WAIT misst die Wartezeit der Listenverarbeitung und PRESENTATION_WORK die anschließende Verarbeitung. VOD trennt RAM-Nachschlagen von Cache-Lesevorgängen auf dem Datenträger (einschließlich Fehltreffern), Serverabrufen, Cache-Schreiben, ersten Katalog-/Kategorieergebnissen und dem Vorladen einer Nachbarkategorie und dem JSON-Parsen sowie dem ersten VOD-Rasterlayout, ohne Kontokennungen. Das Rasterlayout bestätigt keine geladenen Posterbilder. Werte über 600000 ms werden begrenzt und gezählt."
    }
    fun rows(values: List<PerformanceSummary>): List<String> = values.asSequence().filter { it.valid() }
        .distinctBy { it.stage to it.screen }.take(PerformanceStage.entries.size + PerformanceScreen.entries.size).map { row ->
            val name = row.stage.name + (row.screen?.let { "/${it.name}" } ?: "")
            "$name | n=${row.samples} ok=${row.complete} fail=${row.failed} cancel=${row.cancelled}" +
                " | last=${row.lastMs} p50=${row.p50Ms ?: "-"} p95=${row.p95Ms ?: "-"} max=${row.maxMs ?: "-"} ms | capped=${row.clamped}"
        }.toList()
}