package com.yourapp.iptv.ui.screen.search

import com.yourapp.iptv.domain.model.HomeLanguage
import com.yourapp.iptv.domain.model.VodKind

internal class SearchLabels(private val language: HomeLanguage) {
    fun text(ar: String, en: String, de: String) = when (language) {
        HomeLanguage.ARABIC -> ar
        HomeLanguage.ENGLISH -> en
        HomeLanguage.GERMAN -> de
    }
    fun title() = text("البحث الموحّد", "Search everything", "Übergreifende Suche")
    fun back() = text("رجوع", "Back", "Zurück")
    fun search() = text("بحث", "Search", "Suchen")
    fun field() = text("قناة، فيلم أو مسلسل", "Channel, movie or series", "Sender, Film oder Serie")
    fun scope() = text("النتائج من الاشتراك النشط فقط.", "Results from the active subscription only.",
        "Ergebnisse nur aus dem aktiven Abo.")
    fun start() = text("اكتب عنوانًا للبحث في القنوات والأفلام والمسلسلات.",
        "Enter a title to search channels, movies and series.", "Titel eingeben, um Sender, Filme und Serien zu suchen.")
    fun channels() = text("القنوات", "Channels", "Sender")
    fun kind(kind: VodKind) = if (kind == VodKind.MOVIE) text("الأفلام", "Movies", "Filme")
        else text("المسلسلات", "Series", "Serien")
    fun recent() = text("آخر عمليات البحث", "Recent searches", "Letzte Suchanfragen")
    fun clearHistory() = text("مسح سجل البحث", "Clear search history", "Suchverlauf löschen")
    fun clearQuery() = text("مسح النص", "Clear query", "Suchtext löschen")
    fun historyError() = text("تعذر قراءة أو حفظ سجل البحث. يمكنك مسحه لإعادة إنشائه.",
        "Search history could not be read or saved. Clear it to recreate it.",
        "Suchverlauf konnte nicht gelesen oder gespeichert werden. Zum Neuerstellen löschen.")
    fun loading() = text("جارٍ التحميل…", "Loading…", "Wird geladen…")
    fun failed() = text("تعذر تحميل هذا القسم؛ تبقى نتائج الأقسام الأخرى متاحة.",
        "This section could not be loaded; other sections remain available.",
        "Dieser Bereich konnte nicht geladen werden; andere Ergebnisse bleiben verfügbar.")
    fun sourceError() = text("تعذر قراءة الاشتراك النشط.", "Could not read the active subscription.",
        "Aktives Abo konnte nicht gelesen werden.")
    fun needsSource() = text("أضف اشتراكًا من الإعدادات أولًا.", "Add a subscription in settings first.",
        "Zuerst ein Abo in den Einstellungen hinzufügen.")
    fun settings() = text("الإعدادات", "Settings", "Einstellungen")
    fun retry() = text("إعادة المحاولة", "Retry", "Erneut versuchen")
    fun missingChannels() = text("قنوات هذا الاشتراك لم تُحمّل بعد. افتح البث الحي لتحديثها ثم عُد إلى البحث.",
        "Channels for this subscription are not loaded. Open Live TV to refresh them, then return to search.",
        "Sender dieses Abos sind noch nicht geladen. Unter Live-TV aktualisieren, dann zur Suche zurückkehren.")
    fun live() = text("فتح البث الحي", "Open Live TV", "Live-TV öffnen")
    fun unsupported() = text("الأفلام والمسلسلات غير متاحة لهذا النوع من المصدر؛ يلزم Xtream أو رابط get.php ببيانات الحساب.",
        "Movies and series need Xtream or a get.php URL with account credentials.",
        "Filme und Serien benötigen Xtream oder einen get.php-Link mit Zugangsdaten.")
    fun full() = text("تم البحث في القائمة التي أتاحها المزود.", "Searched the catalog supplied by the provider.",
        "Der vom Anbieter gelieferte Katalog wurde durchsucht.")
    fun categoryScope(done: Int, total: Int) = text("تم البحث في $done من $total تصنيفًا.",
        "Searched $done of $total categories.", "$done von $total Kategorien durchsucht.")
    fun failedCategories(count: Int) = text("تعذر بحث $count تصنيفًا. يمكنك إعادة المحاولة.",
        "$count categories could not be searched. You can retry.", "$count Kategorien konnten nicht durchsucht werden. Erneut versuchen.")
    fun scan() = text("بحث التصنيفات التالية (حتى 10)", "Search next categories (up to 10)", "Nächste Kategorien durchsuchen (bis zu 10)")
    fun stop() = text("إيقاف البحث الموسّع", "Stop category search", "Kategoriensuche stoppen")
    fun refine() = text("النتائج محدودة إلى أول 100 تطابق؛ حدّد عنوانًا أدق.",
        "Results are limited to 100 matches; refine the title.", "Ergebnisse auf 100 Treffer begrenzt; Suchtext eingrenzen.")
    fun indexLimited() = text("لم تُفهرس القائمة كاملة بسبب حجمها؛ نطاق النتائج محدود.",
        "The full list could not be indexed because of its size; search coverage is limited.",
        "Die Liste ist zu groß für einen vollständigen Index; Suchumfang begrenzt.")
    fun noResults() = text("لا توجد نتائج مطابقة في النطاق الذي تم بحثه.",
        "No matches in the searched scope.", "Keine Treffer im durchsuchten Bereich.")
    fun notSearched() = text("لم تُبحث التصنيفات بعد. استخدم الزر أدناه لبدء البحث.",
        "Categories have not been searched yet. Start with the button below.",
        "Kategorien noch nicht durchsucht. Mit der Schaltfläche unten beginnen.")
}