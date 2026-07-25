package com.yourapp.iptv.util.validator

object UrlValidator {
    fun isValidUrl(url: String): Boolean {
        return url.startsWith("http://") || url.startsWith("https://")
    }
}