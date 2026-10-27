package com.yourapp.iptv.ui.screen.recovery

import com.yourapp.iptv.domain.model.HomeLanguage
import com.yourapp.iptv.domain.recovery.AppResetProblem

internal class AppResetLabels(private val language: HomeLanguage) {
    private fun text(ar: String,en: String,de: String) = when (language) {
        HomeLanguage.ARABIC -> ar;HomeLanguage.ENGLISH -> en;HomeLanguage.GERMAN -> de
    }
    fun open() = text("إعادة إعداد التطبيق بالكامل","Set up the app again","App vollständig neu einrichten")
    fun title() = text("مسح جميع بيانات التطبيق","Erase all app data","Alle App-Daten löschen")
    fun checking() = text("جارٍ التحقق من حالة التطبيق…","Checking app status…","App-Status wird geprüft…")
    fun preparing() = text("جارٍ تجهيز مسح البيانات…","Preparing to erase app data…","Löschen der App-Daten wird vorbereitet…")
    fun waiting() = text("Android يعالج طلب المسح. قد يُغلق التطبيق؛ افتحه مجددًا بعد ذلك.",
        "Android is processing the erase request. The app may close; open it again afterwards.",
        "Android verarbeitet die Löschanfrage. Die App wird möglicherweise geschlossen; danach erneut öffnen.")
    fun locked() = text("يبقى التصفح والتشغيل محجوبين حتى تكتمل إعادة الإعداد.",
        "Browsing and playback stay blocked until the reset is complete.",
        "Navigation und Wiedergabe bleiben bis zum Abschluss der Neueinrichtung gesperrt.")
    fun scope() = text(
        "سيُمسح كل ما حُفظ داخل التطبيق: بيانات الاشتراكات، جميع الملفات الشخصية بما فيها الافتراضي، PIN وقواعد الحماية، المفضلة والسجل والبحث وتقدم المشاهدة، القنوات ودليل البرامج، الإعدادات ومفاتيح الخدمات ونماذج الترجمة المحلية.",
        "This erases everything saved inside the app: subscription credentials, all profiles including the default profile, the PIN and protection rules, favorites, history, searches and viewing progress, channels and the program guide, settings, service keys and downloaded local caption models.",
        "Alles in der App Gespeicherte wird gelöscht: Zugangsdaten der Abos, alle Profile einschließlich des Standardprofils, PIN und Schutzregeln, Favoriten, Verlauf, Suchanfragen und Wiedergabefortschritt, Sender und Programmführer, Einstellungen, Dienstschlüssel und heruntergeladene lokale Untertitelmodelle.")
    fun outside() = text("يبقى التطبيق مثبتًا. الاشتراكات لدى المزوّد والنسخ التي صدّرتها خارج التطبيق لن تُحذف.",
        "The app stays installed. Provider subscriptions and backups exported outside the app are not deleted.",
        "Die App bleibt installiert. Abos beim Anbieter und außerhalb der App exportierte Sicherungen werden nicht gelöscht.")
    fun noRecovery() = text("هذا مسح كامل لبدء إعداد جديد؛ لا يستعيد المفتاح المفقود أو البيانات القديمة.",
        "This is a complete reset for a fresh setup; it does not recover a lost key or old data.",
        "Dies ist ein vollständiger Neustart der Einrichtung; verlorene Schlüssel oder alte Daten werden nicht wiederhergestellt.")
    fun acknowledge() = text("أفهم أنني سأحتاج إلى إدخال اشتراكاتي وإعداد الحماية من جديد.",
        "I understand that I must enter my subscriptions and set up protection again.",
        "Ich verstehe, dass ich meine Abos erneut eingeben und den Schutz neu einrichten muss.")
    fun finalTitle() = text("تأكيد المسح النهائي","Confirm permanent deletion","Endgültiges Löschen bestätigen")
    fun finalNote() = text("سيطلب التطبيق الآن من Android مسح جميع بياناته المحلية. لا يمكن التراجع عن المسح.",
        "The app will now ask Android to erase all its local data. This cannot be undone.",
        "Die App fordert Android jetzt auf, alle lokalen App-Daten zu löschen. Dies kann nicht rückgängig gemacht werden.")
    fun cancel() = text("إلغاء","Cancel","Abbrechen")
    fun review() = text("متابعة إلى التأكيد النهائي","Continue to final confirmation","Weiter zur letzten Bestätigung")
    fun erase() = text("مسح جميع البيانات نهائيًا","Permanently erase all data","Alle Daten endgültig löschen")
    fun retry() = text("إعادة المحاولة","Retry","Erneut versuchen")
    fun resume() = text("مراجعة واستكمال المسح","Review and finish reset","Löschen prüfen und fortsetzen")
    fun problem(value: AppResetProblem?) = when (value) {
        AppResetProblem.INTERRUPTED -> text("توجد إعادة إعداد غير مكتملة.","A reset was interrupted.","Eine Neueinrichtung wurde unterbrochen.")
        AppResetProblem.JOURNAL_READ -> text("تعذّر التحقق من حالة إعادة الإعداد. أعد المحاولة.",
            "The reset status could not be checked. Retry.","Der Status der Neueinrichtung konnte nicht geprüft werden. Erneut versuchen.")
        AppResetProblem.JOURNAL_WRITE -> text("تعذّر حفظ طلب المسح؛ لم يُطلب من Android مسح البيانات.",
            "The reset request could not be saved; Android was not asked to erase data.",
            "Die Löschabsicht konnte nicht gespeichert werden; Android wurde nicht zum Löschen aufgefordert.")
        AppResetProblem.SYSTEM_REJECTED -> text("لم يقبل Android طلب مسح البيانات.",
            "Android did not accept the request to erase app data.","Android hat die Anfrage zum Löschen der App-Daten nicht angenommen.")
        AppResetProblem.SYSTEM_UNAVAILABLE -> text("تعذّر إرسال طلب المسح إلى Android.",
            "The erase request could not be sent to Android.","Die Löschanfrage konnte nicht an Android gesendet werden.")
        AppResetProblem.NOT_FINISHED -> text("لم يُؤكّد انتهاء المسح. يمكنك مراجعة الطلب وإرساله مجددًا.",
            "Completion has not been confirmed. Review the request and try again.","Der Abschluss wurde nicht bestätigt. Anfrage prüfen und erneut versuchen.")
        null -> ""
    }
}