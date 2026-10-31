package com.yourapp.iptv.ui.screen.settings

import com.yourapp.iptv.domain.model.HomeLanguage
import java.text.Normalizer
import java.util.Locale

internal enum class SettingsCategory { ACCOUNTS, APPEARANCE, PLAYBACK, CAPTIONS, PARENTAL, DATA, UPDATES, HELP }
internal enum class SettingsAction { SUBSCRIPTIONS, PROFILES, PLAYER, BACKUP, DIAGNOSTICS, PARENTAL, CAPTIONS, BACK }
internal sealed interface SettingsIntent {
    data class Category(val value: SettingsCategory) : SettingsIntent
    data class Action(val value: SettingsAction) : SettingsIntent
}
internal class SettingsLabels(val language: HomeLanguage) {
    fun text(ar: String, en: String, de: String) = when (language) {
        HomeLanguage.ARABIC -> ar; HomeLanguage.ENGLISH -> en; HomeLanguage.GERMAN -> de
    }
    fun title(c: SettingsCategory) = when (c) {
        SettingsCategory.ACCOUNTS -> text("الاشتراكات والملفات الشخصية", "Subscriptions and profiles", "Abos und Profile")
        SettingsCategory.APPEARANCE -> text("المظهر واللغة", "Appearance and language", "Darstellung und Sprache")
        SettingsCategory.PLAYBACK -> text("المشاهدة والمشغّل", "Viewing and player", "Wiedergabe und Player")
        SettingsCategory.CAPTIONS -> text("الترجمة", "Subtitles and translation", "Untertitel und Übersetzung")
        SettingsCategory.PARENTAL -> text("الحماية الأبوية", "Parental controls", "Kindersicherung")
        SettingsCategory.DATA -> text("البيانات والتخزين", "Data and storage", "Daten und Speicher")
        SettingsCategory.UPDATES -> text("التحديث وحول التطبيق", "Updates and about", "Updates und App-Info")
        SettingsCategory.HELP -> text("المساعدة والتشخيص", "Help and diagnostics", "Hilfe und Diagnose")
    }
    fun description(c: SettingsCategory) = when (c) {
        SettingsCategory.ACCOUNTS -> text("إدارة اشتراكاتك والملفات الشخصية", "Manage subscriptions and profiles", "Abos und Benutzerprofile verwalten")
        SettingsCategory.APPEARANCE -> text("عدّل شكل التطبيق بما يناسبك", "Make the interface your own", "Die Oberfläche nach Wunsch anpassen")
        SettingsCategory.PLAYBACK -> text("مظهر المشغّل وسلوك بدء المشاهدة", "Player appearance and startup behaviour", "Player-Darstellung und Startverhalten")
        SettingsCategory.CAPTIONS -> text("لغة الترجمة وطريقتها وإعداداتها", "Subtitle language, engine and options", "Sprache, Verfahren und Optionen")
        SettingsCategory.PARENTAL -> text("رمز PIN وقواعد حماية المحتوى", "PIN and content protection rules", "PIN und Regeln zum Schutz von Inhalten")
        SettingsCategory.DATA -> text("النسخ الاحتياطي ومساحة التخزين", "Backups and storage space", "Sicherungen und Speicherplatz")
        SettingsCategory.UPDATES -> text("الإصدار الحالي وتحديث التطبيق", "Installed version and app updates", "Installierte Version und App-Updates")
        SettingsCategory.HELP -> text("فحص التطبيق وتقارير التشخيص", "App checks and diagnostic reports", "App-Prüfung und Diagnoseberichte")
    }
    fun scope(c: SettingsCategory) = when (c) {
        SettingsCategory.APPEARANCE, SettingsCategory.PLAYBACK -> text("لهذا الملف الشخصي", "For this profile", "Für dieses Profil")
        SettingsCategory.ACCOUNTS, SettingsCategory.PARENTAL -> text("الجهاز والملفات الشخصية", "Device and profiles", "Gerät und Profile")
        else -> text("لهذا الجهاز", "For this device", "Für dieses Gerät")
    }
    fun back() = text("رجوع", "Back", "Zurück")
    fun save() = text("حفظ التغييرات", "Save changes", "Änderungen speichern")
}
internal object SettingsCatalog {
    private val aliases = mapOf(
        SettingsCategory.ACCOUNTS to "اشتراك حساب ملف subscriptions accounts profiles abos konten profile",
        SettingsCategory.APPEARANCE to "مظهر لغة تيم لون أحمر ليلي شعار أعمدة شبكة قائمة شريط appearance language theme color red night logos columns grid list sidebar sprache design rot nacht raster",
        SettingsCategory.PLAYBACK to "مشغل نص حجم بدء آخر قناة تنقل تكيفي تعلم player text size startup last channel adaptive navigation learning schrift start sender adaptiv navigieren lernen",
        SettingsCategory.CAPTIONS to "ترجمة صوت نموذج سحابة captions subtitles translation audio models cloud untertitel übersetzung modelle",
        SettingsCategory.PARENTAL to "حماية طفل رمز قفل pin parental child lock kindersicherung sperren",
        SettingsCategory.DATA to "نسخ استرجاع بيانات تخزين مساحة backup restore data storage speicher sicherung wiederherstellen",
        SettingsCategory.UPDATES to "تحديث إصدار تنزيل تثبيت update version download install aktualisieren installieren",
        SettingsCategory.HELP to "مساعدة تشخيص تقرير أداء help diagnostics report performance hilfe diagnose bericht leistung")
    private fun normalize(value: String) = Normalizer.normalize(value, Normalizer.Form.NFKD)
        .replace(Regex("[\\p{M}ـ]"), "").lowercase(Locale.ROOT).trim()
    fun search(query: String, labels: SettingsLabels): List<SettingsCategory> {
        val words = normalize(query.take(80)).split(Regex("\\s+")).filter(String::isNotBlank)
        return SettingsCategory.entries.filter { category ->
            val index = normalize(labels.title(category) + " " + labels.description(category) + " " + aliases[category])
            words.all(index::contains)
        }
    }
}