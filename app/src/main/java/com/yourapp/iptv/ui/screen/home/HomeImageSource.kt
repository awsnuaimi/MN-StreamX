package com.yourapp.iptv.ui.screen.home

/** Network artwork is optional: the palette gradient remains visible on missing/invalid logos. */
internal object HomeImageSource {
    fun remote(url: String?): String? = url?.trim()?.takeIf { candidate ->
        (candidate.startsWith("https://", ignoreCase = true) ||
            candidate.startsWith("http://", ignoreCase = true)) &&
            candidate.length > 8 && !candidate.any { it.isWhitespace() }
    }
}