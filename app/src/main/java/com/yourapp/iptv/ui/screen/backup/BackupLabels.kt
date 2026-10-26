package com.yourapp.iptv.ui.screen.backup

import com.yourapp.iptv.domain.model.*
import java.text.DateFormat
import java.util.Date
import java.util.Locale

internal class BackupLabels(language: HomeLanguage) {
    private val language = language
    private fun text(ar: String, en: String, de: String) = when (language) {
        HomeLanguage.ARABIC -> ar; HomeLanguage.ENGLISH -> en; HomeLanguage.GERMAN -> de
    }
    fun title() = text("النسخ الاحتياطي والاستعادة", "Backup and restore", "Sichern und wiederherstellen")
    fun back() = text("رجوع", "Back", "Zurück")
    fun description() = text("احفظ الاشتراكات والمفضلة والمجلدات والإعدادات والسجل في ملف مشفّر لنقله إلى جهاز آخر.",
        "Save subscriptions, favorites, folders, settings and history in an encrypted file for another device.",
        "Abos, Favoriten, Ordner, Einstellungen und Verlauf in einer verschlüsselten Datei auf ein anderes Gerät übertragen.")
    fun excluded() = text("مفاتيح خدمات الترجمة السحابية وملفات النماذج لا تُنقل. أدخل مفاتيحك مجددًا على الجهاز الجديد.",
        "Cloud translation keys and model files are not transferred. Enter your keys again on the new device.",
        "Cloud-Übersetzungsschlüssel und Modelldateien werden nicht übertragen. Schlüssel auf dem neuen Gerät erneut eingeben.")
    fun export() = text("إنشاء نسخة احتياطية", "Create backup", "Sicherung erstellen")
    fun password() = text("كلمة مرور النسخة", "Backup password", "Passwort der Sicherung")
    fun confirmation() = text("تأكيد كلمة المرور", "Confirm password", "Passwort bestätigen")
    fun passwordHint() = text("من 12 إلى 128 محرفًا. احتفظ بها؛ لا يمكن استعادة النسخة دونها.",
        "12–128 characters. Keep it: the backup cannot be opened without it.", "12–128 Zeichen. Gut aufbewahren: Ohne Passwort lässt sich die Sicherung nicht öffnen.")
    fun import() = text("استعادة نسخة", "Restore a backup", "Sicherung wiederherstellen")
    fun choose() = text("اختيار ملف النسخة", "Choose backup file", "Sicherungsdatei auswählen")
    fun inspect() = text("فك التشفير ومعاينة المحتويات", "Unlock and preview", "Entschlüsseln und prüfen")
    fun preview() = text("معاينة قبل الاستعادة", "Preview before restoring", "Vorschau vor dem Wiederherstellen")
    fun accounts(added: Int, existing: Int) = text("اشتراكات جديدة: $added، موجودة بالفعل: $existing", "New subscriptions: $added; already saved: $existing",
        "Neue Abos: $added; bereits vorhanden: $existing")
    fun counts(favorites: Int, folders: Int, history: Int, progress: Int) = text(
        "المفضلة: $favorites • المجلدات: $folders • سجل القنوات: $history • مواضع متابعة المشاهدة: $progress",
        "Favorites: $favorites • Folders: $folders • Channel history: $history • Resume positions: $progress",
        "Favoriten: $favorites • Ordner: $folders • Senderverlauf: $history • Wiedergabepositionen: $progress")
    fun defaultName() = text("الاشتراك الأساسي", "Original subscription", "Bisheriges Abo")
    fun mergeNote() = text("تُضاف الاشتراكات الناقصة وتُدمج المفضلة والسجلات. تحتفظ الاشتراكات الموجودة ببيانات دخولها وأسمائها الحالية.",
        "Missing subscriptions are added; favorites and histories are merged. Existing subscriptions keep their current names and credentials.",
        "Fehlende Abos werden ergänzt, Favoriten und Verläufe zusammengeführt. Bestehende Abos behalten Namen und Zugangsdaten.")
    fun settings() = text("استعادة إعدادات المظهر والمشغّل والترجمة وبدء التشغيل", "Restore appearance, player, captions and startup settings",
        "Darstellung, Player-, Untertitel- und Starteinstellungen wiederherstellen")
    fun folders() = text("استعادة ترتيب المجلدات وتعييناتها المحفوظة", "Restore saved folder layout and assignments", "Gespeicherte Ordneranordnung und Zuordnungen wiederherstellen")
    fun activate() = text("تفعيل الاشتراك الذي كان نشطًا في النسخة", "Activate the subscription selected in the backup", "Das in der Sicherung aktive Abo verwenden")
    fun restore() = text("استعادة البيانات الآن", "Restore data now", "Daten jetzt wiederherstellen")
    fun cancel() = text("إلغاء المعاينة", "Cancel preview", "Vorschau abbrechen")
    fun busy() = text("جارٍ العمل…", "Working…", "Wird verarbeitet…")
    fun restoring() = text("جارٍ استعادة البيانات. يرجى الانتظار…", "Restoring data. Please wait…", "Daten werden wiederhergestellt. Bitte warten…")
    fun recovery() = text("جارٍ التحقق من اكتمال استعادة البيانات…", "Checking data recovery…", "Datenwiederherstellung wird geprüft…")
    fun retry() = text("إعادة المحاولة", "Retry", "Erneut versuchen")
    fun saved() = text("حُفظت النسخة المشفّرة بنجاح.", "Encrypted backup saved.", "Verschlüsselte Sicherung gespeichert.")
    fun restored() = text("استُعيدت البيانات بنجاح.", "Data restored successfully.", "Daten erfolgreich wiederhergestellt.")
    fun date(time: Long): String = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT,
        Locale.forLanguageTag(when (language) { HomeLanguage.ARABIC -> "ar"; HomeLanguage.ENGLISH -> "en"; HomeLanguage.GERMAN -> "de" })).format(Date(time))
    fun error(problem: BackupProblem) = when (problem) {
        BackupProblem.PASSWORD_RULES -> passwordHint()
        BackupProblem.PASSWORD_MISMATCH -> text("كلمتا المرور غير متطابقتين.", "Passwords do not match.", "Passwörter stimmen nicht überein.")
        BackupProblem.AUTHENTICATION -> text("كلمة المرور غير صحيحة أو الملف تالف أو معدّل.", "Wrong password, or the file is damaged or modified.", "Passwort falsch oder Datei beschädigt bzw. verändert.")
        BackupProblem.INVALID -> text("ملف النسخة أو المعاينة غير صالح. افتح الملف وعاينه مجددًا.", "Invalid backup or expired preview. Unlock the file again.", "Sicherung ungültig oder Vorschau abgelaufen. Datei erneut entschlüsseln.")
        BackupProblem.UNSUPPORTED -> text("إصدار هذه النسخة غير مدعوم. حدّث التطبيق.", "Unsupported backup version. Update the app.", "Sicherungsversion nicht unterstützt. App aktualisieren.")
        BackupProblem.TOO_LARGE -> text("النسخة أكبر من الحد الآمن البالغ 16 MiB.", "The backup exceeds the safe 16 MiB limit.", "Sicherung überschreitet die sichere Grenze von 16 MiB.")
        BackupProblem.STORAGE -> text("تعذر إكمال العملية. تحقق من الملف والمساحة المتاحة ثم أعد المحاولة.", "Could not complete the operation. Check the file and free space, then retry.", "Vorgang fehlgeschlagen. Datei und freien Speicher prüfen, dann erneut versuchen.")
        BackupProblem.ACCOUNT_LIMIT -> text("سيزيد عدد الاشتراكات عن 16. أزل اشتراكًا غير مستخدم ثم أعد المعاينة.", "This would exceed 16 subscriptions. Remove an unused subscription and preview again.", "Mehr als 16 Abos. Ein ungenutztes Abo entfernen und Vorschau wiederholen.")
        BackupProblem.RECOVERY -> text("تعذر إكمال استرجاع البيانات السابقة. أعد المحاولة قبل متابعة الاستخدام.", "Could not finish recovering previous data. Retry before continuing.", "Vorherige Daten konnten nicht vollständig wiederhergestellt werden. Vor dem Fortfahren erneut versuchen.")
        BackupProblem.PICKER -> text("لا يوجد منتقي ملفات متوافق. يلزم تطبيق ملفات يدعم اختيار مستندات Android.", "No compatible file picker is available. A file app supporting Android document selection is required.", "Keine passende Dateiauswahl verfügbar. Eine Datei-App mit Android-Dokumentauswahl wird benötigt.")
    }
}