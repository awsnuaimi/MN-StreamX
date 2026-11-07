package mnstudio.apps.mnstreamx.domain.caption
/** Our player uses normalized floats; Vosk's short API requires signed PCM16. */
object CaptionPcm16 {
    fun shorts(audio: FloatArray, offset: Int, count: Int): ShortArray {
        require(offset >= 0 && count >= 0 && offset <= audio.size - count)
        return ShortArray(count) { index ->
            val value = audio[offset + index]
            if (!value.isFinite()) 0 else (value.coerceIn(-1f, 1f) * 32768f)
                .toInt().coerceIn(-32768, 32767).toShort()
        }
    }
}