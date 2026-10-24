package com.yourapp.iptv.data.caption

/** Azure requires 16 kHz, signed 16-bit little-endian mono PCM without a WAV header. */
internal object CloudPcm16 {
    fun encode(samples: FloatArray): ByteArray {
        require(samples.size <= 16000 * 5) { "PCM window exceeds the five-second limit" }
        val output = ByteArray(samples.size * 2)
        for (i in samples.indices) {
            val sample = samples[i].let { if (it.isFinite()) it.coerceIn(-1f, 1f) else 0f }
            val pcm = (sample * 32767f).toInt().coerceIn(-32768, 32767)
            output[2 * i] = pcm.toByte()
            output[2 * i + 1] = (pcm shr 8).toByte()
        }
        return output
    }
}