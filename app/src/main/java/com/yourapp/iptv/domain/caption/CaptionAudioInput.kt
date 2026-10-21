package com.yourapp.iptv.domain.caption

import kotlinx.coroutines.channels.ReceiveChannel

data class AudioWindow(val samples: FloatArray, val startUs: Long) {
    val durationMillis: Long get() = samples.size * 1000L / 16000
}
class CaptionFeed(val windows: ReceiveChannel<AudioWindow>, private val finish: () -> Unit) : AutoCloseable {
    override fun close() = finish()
}
interface CaptionAudioInput {
    val positionUs: Long
    fun open(): CaptionFeed
}
data class PcmFormat(val rate: Int, val channels: Int, val bytes: Int, val floating: Boolean = false) {
    val supported: Boolean get() = rate in 8000..192000 && channels in 1..8 &&
        bytes in 2..4 && (!floating || bytes == 4)
}