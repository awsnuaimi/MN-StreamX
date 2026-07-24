package com.yourapp.iptv.util.security

object CertificateValidator {
    // في هذه المرحلة، سنقوم بتمرير التحقق فقط
    // يمكن توسيع هذا لاحقاً للتحقق من الشهادات الحقيقية
    fun validate(url: String): Boolean {
        return url.startsWith("https://")
    }
}