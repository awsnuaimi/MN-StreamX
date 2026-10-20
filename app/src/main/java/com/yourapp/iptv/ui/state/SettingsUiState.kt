package com.yourapp.iptv.ui.state

import com.yourapp.iptv.domain.model.AppSettings

enum class SettingsIssue(val fallbackMessage: String) {
    READ_FAILED("تعذر قراءة الإعدادات المحفوظة."),
    SAVE_FAILED("تعذر حفظ الإعدادات. أعد المحاولة."),
    M3U_REQUIRED("أدخل رابط قائمة M3U."),
    M3U_INVALID("رابط M3U غير صالح. استخدم رابط http:// أو https:// كاملًا."),
    XTREAM_REQUIRED("أكمل السيرفر واسم المستخدم وكلمة المرور."),
    SERVER_INVALID("عنوان السيرفر غير صالح."),
    EPG_INVALID("رابط EPG غير صالح.")
}

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val loadFailed: Boolean = false,
    val issue: SettingsIssue? = null
) {
    // Read compatibility for existing callers; visible text resolves the typed issue in UI resources.
    val errorMessage: String? get() = issue?.fallbackMessage
}