package com.yourapp.iptv.ui.state

import com.yourapp.iptv.domain.model.AppSettings
import com.yourapp.iptv.domain.model.Subscription
import com.yourapp.iptv.domain.security.SecureStorageFailure

sealed interface SubscriptionAction {
    data object New : SubscriptionAction
    data class Edit(val id: String) : SubscriptionAction
    data class Activate(val id: String) : SubscriptionAction
    data class Remove(val id: String) : SubscriptionAction
}

enum class SettingsIssue(val fallbackMessage: String) {
    READ_FAILED("تعذر قراءة الإعدادات المحفوظة."),
    SAVE_FAILED("تعذر حفظ الإعدادات. أعد المحاولة."),
    M3U_REQUIRED("أدخل رابط قائمة M3U."),
    M3U_INVALID("رابط M3U غير صالح. استخدم رابط http:// أو https:// كاملًا."),
    XTREAM_REQUIRED("أكمل السيرفر واسم المستخدم وكلمة المرور."),
    SERVER_INVALID("عنوان السيرفر غير صالح."),
    EPG_INVALID("رابط EPG غير صالح."),
    SUBSCRIPTION_NAME("أدخل اسمًا للاشتراك."),
    SUBSCRIPTION_DUPLICATE("هذا الحساب محفوظ بالفعل. عدّل الاشتراك الموجود بدل إضافته مرة أخرى."),
    SUBSCRIPTION_LIMIT("بلغت الحد الأقصى للاشتراكات المحفوظة."),
    SUBSCRIPTION_MISSING("الاشتراك لم يعد متاحًا. أعد تحميل الإعدادات."),
    SUBSCRIPTION_ACTIVE_DELETE("بدّل إلى اشتراك آخر قبل حذف الاشتراك الحالي."),
    SUBSCRIPTION_INVALID("بيانات الاشتراك غير صالحة أو أطول من المسموح.")
}

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val loadFailed: Boolean = false,
    val issue: SettingsIssue? = null,
    val subscriptions: List<Subscription> = emptyList(),
    val activeSubscriptionId: String = "",
    val editingSubscriptionId: String? = null,
    val subscriptionName: String = "",
    val dirty: Boolean = false,
    val pending: SubscriptionAction? = null,
    val storageFailure: SecureStorageFailure? = null
) {
    val errorMessage: String? get() = issue?.fallbackMessage
    val enabled: Boolean get() = !isLoading && !isSaving && !loadFailed
}