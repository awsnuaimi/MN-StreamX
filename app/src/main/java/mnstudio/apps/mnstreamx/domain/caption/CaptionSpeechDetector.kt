package mnstudio.apps.mnstreamx.domain.caption

/** Sample positions remain relative to the original 16 kHz audio window. */
data class CaptionSpeechSpan(val start: Int, val end: Int) {
    fun decodeRange(size: Int): CaptionSpeechSpan {
        require(start in 0 until end && end <= size && size >= 16000)
        var left = (start - 3200).coerceAtLeast(0)
        var right = (end + 3200).coerceAtMost(size)
        if (right - left < 16000) {
            right = (left + 16000).coerceAtMost(size)
            left = (right - 16000).coerceAtLeast(0)
        }
        return CaptionSpeechSpan(left, right)
    }
}

interface CaptionSpeechDetector : AutoCloseable {
    suspend fun speech(samples: FloatArray): CaptionSpeechSpan?
    fun cancel()
    override fun close()
}