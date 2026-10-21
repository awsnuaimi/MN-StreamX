package com.yourapp.iptv.player.caption

import com.yourapp.iptv.domain.caption.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer

/** Only decoded audio from this player is copied. No microphone or system-wide capture. */
class CaptionAudioTap : CaptionAudioInput {
    private class Receiver {
        val queue = Channel<AudioWindow>(1, BufferOverflow.DROP_OLDEST)
        var format: PcmFormat? = null
        var assembler: PcmWindowAssembler? = null
    }
    @Volatile private var active: Receiver? = null
    @Volatile override var positionUs: Long = Long.MIN_VALUE
        private set
    private val changes = MutableStateFlow(0L)
    val epoch = changes.asStateFlow()
    override fun open(): CaptionFeed {
        val receiver = Receiver()
        active?.queue?.close()
        active = receiver
        return CaptionFeed(receiver.queue) {
            if (active === receiver) active = null
            receiver.queue.close()
        }
    }
    fun position(value: Long) { positionUs = value }
    fun discontinuity() {
        positionUs = Long.MIN_VALUE
        active?.assembler?.reset()
        changes.value++
    }
    fun accept(data: ByteBuffer, ptsUs: Long, format: PcmFormat?) {
        val receiver = active ?: return
        if (format == null || !format.supported) return
        if (receiver.format != format) {
            receiver.format = format
            receiver.assembler = PcmWindowAssembler(format) { receiver.queue.trySend(it) }
        }
        receiver.assembler?.accept(data, ptsUs)
    }
}