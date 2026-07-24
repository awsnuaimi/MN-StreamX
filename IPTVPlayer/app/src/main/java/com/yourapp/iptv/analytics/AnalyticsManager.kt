package com.yourapp.iptv.analytics

object AnalyticsManager {
    private const val TAG = "Analytics"

    fun logEvent(eventName: String, data: Map<String, Any> = emptyMap()) {
        // في هذه المرحلة، نقوم فقط بطباعة الحدث
        // يمكن توسيع هذا لاحقاً لإرسال البيانات إلى Firebase أو أي خدمة تحليلات
        println("$TAG: $eventName - $data")
    }

    fun logScreenView(screenName: String) {
        println("$TAG: Screen Viewed - $screenName")
    }

    fun logUserAction(action: String) {
        println("$TAG: User Action - $action")
    }
}