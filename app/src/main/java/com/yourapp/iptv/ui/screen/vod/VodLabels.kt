package com.yourapp.iptv.ui.screen.vod

import com.yourapp.iptv.domain.model.HomeLanguage
import com.yourapp.iptv.domain.model.VodKind

internal class VodLabels(private val language: HomeLanguage) {
    fun text(ar: String, en: String, de: String): String = when (language) {
        HomeLanguage.ARABIC -> ar
        HomeLanguage.ENGLISH -> en
        HomeLanguage.GERMAN -> de
    }
    fun title(kind: VodKind) = if (kind == VodKind.MOVIE)
        text("الأفلام", "Movies", "Filme") else text("المسلسلات", "Series", "Serien")
    fun all() = text("جميع التصنيفات", "All categories", "Alle Kategorien")
    fun search() = text("ابحث عن عنوان", "Search titles", "Titel suchen")
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
    fun requiresXtream() = text("تحتاج الأفلام والمسلسلات إلى حساب Xtream Codes في الإعدادات.",
        "Movies and series currently require an Xtream Codes account.",
        "Filme und Serien benötigen derzeit ein Xtream-Codes-Konto.")
}