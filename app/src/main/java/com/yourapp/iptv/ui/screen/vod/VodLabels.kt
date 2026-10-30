package com.yourapp.iptv.ui.screen.vod

import com.yourapp.iptv.domain.model.HomeLanguage
import com.yourapp.iptv.domain.model.VodKind

internal class VodLabels(val language: HomeLanguage) {
    fun text(ar: String, en: String, de: String): String = when (language) {
        HomeLanguage.ARABIC -> ar
        HomeLanguage.ENGLISH -> en
        HomeLanguage.GERMAN -> de
    }
    fun title(kind: VodKind) = if (kind == VodKind.MOVIE)
        text("الأفلام", "Movies", "Filme") else text("المسلسلات", "Series", "Serien")
    fun all() = text("جميع التصنيفات", "All categories", "Alle Kategorien")
    fun search() = text("ابحث عن عنوان", "Search titles", "Titel suchen")
    fun refresh() = text("تحديث القائمة", "Refresh catalog", "Katalog aktualisieren")
    fun categoryMode() = text("لم يوفر الخادم قائمة كاملة؛ يُحمّل التصنيف المختار فقط. البحث داخل هذا التصنيف.",
        "The server did not provide a complete list; only the selected category is loaded. Search is within that category.",
        "Der Server liefert keine vollständige Liste; nur die gewählte Kategorie wird geladen. Die Suche gilt für diese Kategorie.")
    fun categoriesUnavailable() = text("تعذر تحميل أسماء التصنيفات، لكن العناوين متاحة. يمكنك تحديث القائمة للمحاولة مجددًا.",
        "Category names could not be loaded, but titles are available. Refresh to retry.",
        "Kategorienamen fehlen, die Titel sind verfügbar. Zum Wiederholen den Katalog aktualisieren.")
    fun emptyCatalog() = text("لم يُرجع الخادم عناوين في هذه القائمة. حدّث القائمة أو جرّب تصنيفًا آخر؛ وقد لا يشمل الاشتراك هذا المحتوى.",
        "The server returned no titles in this list. Refresh or try another category; the subscription may not include this content.",
        "Der Server meldet keine Titel in dieser Liste. Aktualisieren oder eine andere Kategorie wählen; eventuell ist der Inhalt nicht im Abo enthalten.")
    fun tryAgain() = text("إعادة المحاولة", "Retry", "Erneut versuchen")
    fun back() = text("رجوع", "Back", "Zurück")
    fun play() = text("تشغيل", "Play", "Abspielen")
    fun resume() = text("متابعة المشاهدة", "Continue watching", "Weitersehen")
    fun position(ms: Long): String {
        val minutes = (ms / 60_000L).coerceAtLeast(0L)
        return "%02d:%02d".format(java.util.Locale.ROOT, minutes / 60, minutes % 60)
    }
    fun episodes() = text("الحلقات", "Episodes", "Folgen")
    fun empty() = text("لا توجد نتائج", "No results", "Keine Ergebnisse")
    fun failed() = text("تعذر تحميل المحتوى من السيرفر.", "Could not load the catalog.", "Katalog konnte nicht geladen werden.")
    fun requiresXtream() = text("تحتاج الأفلام والمسلسلات إلى حساب Xtream Codes أو رابط M3U من نوع get.php يحتوي بياناته. روابط M3U العامة غير مدعومة هنا.",
        "Movies and series require an Xtream Codes account or its get.php M3U URL. Generic M3U playlists are not supported here.",
        "Filme und Serien benötigen ein Xtream-Konto oder dessen get.php-M3U-Link. Allgemeine M3U-Listen werden hier nicht unterstützt.")
}