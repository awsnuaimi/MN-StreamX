package com.yourapp.iptv.util.validator

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object UrlValidator {
    fun isValidUrl(url: String): Boolean = url.trim().toHttpUrlOrNull() != null
}