package com.yourapp.iptv.domain.caption

/** On sustained translator overload, keep transcribed captions and retry only occasionally. */
class CaptionTranslationContinuity {
    private var failures = 0
    private var remaining = 0
    val consecutiveDeadlines get() = failures
    val deferredWindows get() = remaining

    fun shouldTranslate(): Boolean {
        if (remaining == 0) return true
        remaining--
        return false
    }

    fun deadline() {
        failures = (failures + 1).coerceAtMost(5)
        remaining = 1 shl failures // 2, 4, 8, 16, 32 windows; bounded retries.
    }

    fun success(elapsedMillis: Long = 0, windowMillis: Long = 5000) {
        failures = 0
        val cadence = windowMillis.coerceAtLeast(1000)
        // Successful inference may still be slower than audio arrival. Skip enough
        // future windows to avoid building a delay backlog on a slow CPU.
        remaining = if (elapsedMillis <= cadence) 0
            else ((elapsedMillis + cadence - 1) / cadence - 1).coerceIn(1, 16).toInt()
    }

    fun untranslated(text: String, sourceLanguage: String): String {
        val language = sourceLanguage.takeIf { it.matches(Regex("[a-zA-Z]{2,3}")) }
            ?.uppercase() ?: "AUTO"
        return "[$language · untranslated] ${text.trim()}"
    }
}