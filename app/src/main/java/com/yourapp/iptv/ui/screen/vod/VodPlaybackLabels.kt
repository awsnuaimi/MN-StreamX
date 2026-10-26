package com.yourapp.iptv.ui.screen.vod

import com.yourapp.iptv.domain.model.HomeLanguage

internal class VodPlaybackLabels(private val language: HomeLanguage) {
    fun text(ar: String, en: String, de: String) = when (language) {
        HomeLanguage.ARABIC -> ar
        HomeLanguage.ENGLISH -> en
        HomeLanguage.GERMAN -> de
    }
    fun back() = text("رجوع", "Back", "Zurück")
    fun play() = text("تشغيل", "Play", "Abspielen")
    fun pause() = text("إيقاف مؤقت", "Pause", "Pause")
    fun rewind() = text("−١٠ ثوانٍ", "−10 seconds", "−10 Sekunden")
    fun forward() = text("+١٠ ثوانٍ", "+10 seconds", "+10 Sekunden")
    fun restart() = text("من البداية", "Restart", "Von vorne")
    fun timeline() = text("موضع المشاهدة؛ يمين ويسار للتنقل عشر ثوانٍ", "Playback position; left and right seek ten seconds", "Wiedergabeposition; links und rechts springen zehn Sekunden")
    fun audio() = text("الصوت", "Audio", "Tonspur")
    fun subtitles() = text("الترجمة", "Subtitles", "Untertitel")
    fun automatic() = text("تلقائي", "Automatic", "Automatisch")
    fun off() = text("إيقاف", "Off", "Aus")
    fun close() = text("إغلاق", "Close", "Schließen")
    fun none() = text("لا يوفر هذا الفيديو مسارات مدعومة هنا.", "No supported tracks are available in this video.", "Für dieses Video sind keine unterstützten Spuren verfügbar.")
    fun next() = text("الحلقة التالية", "Next episode", "Nächste Folge")
    fun autoNext(enabled: Boolean) = text("الحلقة التالية تلقائيًا: ${if (enabled) "نعم" else "لا"}",
        "Auto next: ${if (enabled) "On" else "Off"}", "Automatisch weiter: ${if (enabled) "An" else "Aus"}")
    fun nextIn(seconds: Int) = text("تبدأ الحلقة التالية خلال $seconds ثوانٍ", "Next episode starts in $seconds seconds", "Nächste Folge startet in $seconds Sekunden")
    fun cancelNext() = text("إلغاء العدّ التنازلي", "Cancel countdown", "Countdown abbrechen")
    fun failed() = text("تعذر تشغيل الفيديو. يمكنك إعادة المحاولة أو الرجوع.", "Playback failed. Retry or go back.", "Wiedergabe fehlgeschlagen. Erneut versuchen oder zurückgehen.")
    fun unavailable() = text("تعذر فتح الفيديو لهذا الاشتراك. ارجع إلى القائمة واختره مجددًا.",
        "This video could not be opened for this subscription. Return to the list and select it again.",
        "Dieses Video konnte für das Abo nicht geöffnet werden. Zur Liste zurückgehen und erneut auswählen.")
    fun retry() = text("إعادة المحاولة", "Retry", "Erneut versuchen")
    fun episodesLoading() = text("جارٍ جلب ترتيب الحلقات…", "Loading episode order…", "Folgenreihenfolge wird geladen…")
    fun episodesFailed() = text("تعذر جلب الحلقة التالية؛ الفيديو الحالي يبقى متاحًا.", "Next episode could not be loaded; the current video remains available.", "Nächste Folge konnte nicht geladen werden; das aktuelle Video bleibt verfügbar.")
    fun retryEpisodes() = text("إعادة جلب الحلقات", "Reload episodes", "Folgen neu laden")
    fun noParent() = text("لإظهار الحلقة التالية، افتح هذه الحلقة مرة واحدة من قائمة حلقات المسلسل.",
        "To enable next episode, open this episode once from the series episode list.",
        "Für die nächste Folge diese Episode einmal aus der Folgenliste der Serie öffnen.")
}