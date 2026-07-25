package com.yourapp.iptv.analytics

object CrashReporter {
    private const val TAG = "CrashReporter"

    fun reportError(throwable: Throwable, message: String? = null) {
        // في هذه المرحلة، نقوم فقط بطباعة الخطأ
        // يمكن توسيع هذا لاحقاً بإرسال التقارير إلى Crashlytics أو أي خدمة تتبع أخطاء
        val errorMsg = message ?: "Unhandled exception"
        println("$TAG: $errorMsg - ${throwable.message}")
        throwable.printStackTrace()
    }

    fun reportWarning(message: String) {
        println("$TAG: Warning - $message")
    }
}