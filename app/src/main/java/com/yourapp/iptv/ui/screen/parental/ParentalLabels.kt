package com.yourapp.iptv.ui.screen.parental

import com.yourapp.iptv.domain.model.HomeLanguage
import com.yourapp.iptv.domain.parental.*

internal class ParentalLabels(val language: HomeLanguage) {
    fun text(ar: String, en: String, de: String) = when (language) {
        HomeLanguage.ARABIC -> ar; HomeLanguage.ENGLISH -> en; HomeLanguage.GERMAN -> de
    }
    fun title() = text("الرقابة الأبوية","Parental control","Jugendschutz")
    fun back() = text("رجوع","Back","Zurück")
    fun cancel() = text("إلغاء","Cancel","Abbrechen")
    fun confirm() = text("تأكيد","Confirm","Bestätigen")
    fun unlock() = text("فتح المحتوى المحمي","Unlock protected content","Geschützte Inhalte entsperren")
    fun lock() = text("قفل الآن","Lock now","Jetzt sperren")
    fun pin() = text("أدخل رمز PIN من 6 أرقام","Enter your 6-digit PIN","6-stellige PIN eingeben")
    fun newPin() = text("اختر رمز PIN من 6 أرقام","Choose a 6-digit PIN","6-stellige PIN festlegen")
    fun repeat() = text("أعد إدخال الرمز","Repeat the PIN","PIN wiederholen")
    fun mismatch() = text("الرمزان غير متطابقين","The PINs do not match","Die PINs stimmen nicht überein")
    fun delete() = text("حذف رقم","Delete digit","Ziffer löschen")
    fun clear() = text("مسح","Clear","Leeren")
    fun section(value: ParentalSection) = when (value) {
        ParentalSection.LIVE -> text("القنوات المباشرة","Live TV","Live-TV")
        ParentalSection.MOVIES -> text("الأفلام","Movies","Filme")
        ParentalSection.SERIES -> text("المسلسلات","Series","Serien")
        ParentalSection.SETTINGS -> text("الإعدادات","Settings","Einstellungen")
    }
    fun message(value: ParentalResult?, seconds: Long = 0): String = when {
        seconds > 0 -> text("حاول بعد $seconds ثانية","Try again in $seconds seconds","In $seconds Sekunden erneut versuchen")
        value == ParentalResult.WRONG_PIN -> text("رمز PIN غير صحيح","Incorrect PIN","Falsche PIN")
        value == ParentalResult.INVALID_PIN -> pin()
        value == ParentalResult.INVALID_RULES -> text("بلغت حد القواعد؛ قلّل المجموعات أو امسح قواعد الاشتراكات الأخرى.",
            "Rule limit reached. Reduce groups or remove other subscriptions' group rules.",
            "Regellimit erreicht. Gruppen reduzieren oder Gruppensperren anderer Abos entfernen.")
        value == ParentalResult.STORAGE_ERROR -> text("تعذر حفظ الحماية. أعد المحاولة.","Protection could not be saved. Retry.","Schutz konnte nicht gespeichert werden. Erneut versuchen.")
        value == ParentalResult.DENIED -> text("انتهت الجلسة. أعد المحاولة.","Session ended. Please retry.","Sitzung beendet. Bitte erneut versuchen.")
        else -> ""
    }
}