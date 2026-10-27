package com.yourapp.iptv.ui.component.common

import com.yourapp.iptv.domain.model.HomeLanguage
import com.yourapp.iptv.domain.security.SecureStorageFailure

internal fun secureStorageMessage(problem: SecureStorageFailure?, language: HomeLanguage): String {
    fun text(ar: String, en: String, de: String) = when (language) {
        HomeLanguage.ARABIC -> ar
        HomeLanguage.ENGLISH -> en
        HomeLanguage.GERMAN -> de
    }
    return when (problem) {
        SecureStorageFailure.TEMPORARILY_UNAVAILABLE -> text(
            "التخزين الآمن غير متاح حاليًا. أعد المحاولة؛ وإذا استمرت المشكلة أعد تشغيل الجهاز.",
            "Secure storage is currently unavailable. Retry; if the problem persists, restart the device.",
            "Der sichere Speicher ist derzeit nicht verfügbar. Erneut versuchen; bei anhaltenden Problemen das Gerät neu starten.")
        SecureStorageFailure.KEY_MISSING -> text(
            "مفتاح التشفير المحفوظ غير موجود. لا يمكن فتح البيانات المشفّرة.",
            "The saved encryption key is missing. Encrypted data cannot be opened.",
            "Der gespeicherte Verschlüsselungsschlüssel fehlt. Verschlüsselte Daten können nicht geöffnet werden.")
        SecureStorageFailure.KEY_INVALIDATED -> text(
            "مفتاح التشفير لم يعد صالحًا للاستخدام. لا يمكن فتح البيانات المشفّرة.",
            "The encryption key has been invalidated. Encrypted data cannot be opened.",
            "Der Verschlüsselungsschlüssel ist ungültig geworden. Verschlüsselte Daten können nicht geöffnet werden.")
        SecureStorageFailure.INVALID_RECORD -> text(
            "صيغة البيانات المشفّرة المحفوظة غير صالحة أو غير مدعومة.",
            "The saved encrypted record has an invalid or unsupported format.",
            "Die gespeicherten verschlüsselten Daten haben ein ungültiges oder nicht unterstütztes Format.")
        SecureStorageFailure.AUTHENTICATION_FAILED -> text(
            "تعذّر التحقق من سلامة البيانات المشفّرة. لم تُمسح البيانات.",
            "The encrypted data could not be authenticated. No data was erased.",
            "Die Echtheit der verschlüsselten Daten konnte nicht bestätigt werden. Es wurden keine Daten gelöscht.")
        null -> text("تعذّر قراءة البيانات المحفوظة.", "Saved data could not be read.",
            "Gespeicherte Daten konnten nicht gelesen werden.")
    }
}