package com.yourapp.iptv.util.security

object UrlSecurity {
    fun ensureHttps(url: String): String {
        return if (url.startsWith("http://")) {
            url.replace("http://", "https://")
        } else {
            url
        }
    }

    fun isSecure(url: String): Boolean {
        return url.startsWith("https://")
    }
}