package com.yourapp.iptv.ui.screen.profile

import com.yourapp.iptv.domain.model.HomeLanguage
import com.yourapp.iptv.domain.profile.*

internal class ProfileLabels(private val language: HomeLanguage) {
    fun text(ar: String,en: String,de: String) = when (language) {
        HomeLanguage.ARABIC -> ar;HomeLanguage.ENGLISH -> en;HomeLanguage.GERMAN -> de
    }
    fun title() = text("الملفات الشخصية","Profiles","Profile")
    fun name(row: UserProfile) = row.name.ifBlank { text("الملف الأساسي","Original profile","Bisheriges Profil") }
    fun kind(kind: ProfileKind) = if (kind == ProfileKind.CHILD) text("أطفال","Child","Kind") else text("بالغ","Adult","Erwachsen")
    fun back() = text("رجوع","Back","Zurück")
    fun manage() = text("إدارة الملفات","Manage profiles","Profile verwalten")
    fun message(value: ProfileResult?) = when (value) {
        null,ProfileResult.OK -> ""
        ProfileResult.PIN_REQUIRED -> text("أدخل PIN للمتابعة.","Enter PIN to continue.","Zum Fortfahren PIN eingeben.")
        ProfileResult.PIN_SETUP_REQUIRED -> text("أنشئ PIN من الحماية الأبوية أولًا.","Set up a PIN in parental controls first.","Zuerst eine PIN im Jugendschutz einrichten.")
        ProfileResult.LIMIT -> text("الحد الأقصى 8 ملفات.","Maximum 8 profiles.","Höchstens 8 Profile.")
        ProfileResult.INVALID -> text("اختر اسمًا مختلفًا من 1 إلى 32 محرفًا.","Choose a unique name of 1–32 characters.","Einen eindeutigen Namen mit 1–32 Zeichen wählen.")
        ProfileResult.ACTIVE_PROFILE -> text("لا يمكن حذف الملف الأساسي أو النشط.","The original or active profile cannot be removed.","Das ursprüngliche oder aktive Profil kann nicht gelöscht werden.")
        ProfileResult.NOT_FOUND -> text("الملف غير موجود.","Profile not found.","Profil nicht gefunden.")
        ProfileResult.STORAGE -> text("تعذر حفظ التغيير. أعد المحاولة.","Could not save. Retry.","Speichern fehlgeschlagen. Erneut versuchen.")
        ProfileResult.DENIED -> text("افتح PIN أولًا.","Unlock PIN first.","Zuerst PIN entsperren.")
    }
}