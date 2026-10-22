package com.yourapp.iptv.domain.caption

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

/** Renderer-thread only. Bounded mono 16 kHz windows; never writes to the playback buffer. */
class PcmWindowAssembler(private val format: PcmFormat, private val offer: (AudioWindow) -> Unit) {
    private val samples = FloatArray(80000)
    private var count = 0
    private var startUs = 0L
    private var lastEndUs = Long.MIN_VALUE
    private var frame = 0L
    private var next = 0.0
    private var previous = 0f
    private var energy = 0.0
    private var quiet = 0
    private val step = format.rate / 16000.0
    private var limitSamples = samples.size

    fun limitWindow(millis: Int) { limitSamples = millis.coerceIn(2000, 5000) * 16 }

    fun reset() {
        count = 0; frame = 0; next = 0.0; previous = 0f
        energy = 0.0; quiet = 0; lastEndUs = Long.MIN_VALUE
    }

    fun accept(input: ByteBuffer, ptsUs: Long) {
        if (!format.supported) return
        val data = input.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        val frameBytes = format.bytes * format.channels
        val frames = data.remaining() / frameBytes
        if (frames == 0) return
        if (lastEndUs != Long.MIN_VALUE && abs(ptsUs - lastEndUs) > 250000) reset()
        if (frame == 0L) startUs = ptsUs
        val origin = ptsUs - frame * 1000000L / format.rate
        repeat(frames) {
            var mono = 0f
            repeat(format.channels) {
                mono += when {
                    format.floating -> data.float.let { if (it.isFinite()) it.coerceIn(-1f, 1f) else 0f }
                    format.bytes == 2 -> data.short / 32768f
                    format.bytes == 3 -> {
                        val raw = (data.get().toInt() and 255) or ((data.get().toInt() and 255) shl 8) or
                            (data.get().toInt() shl 16)
                        raw / 8388608f
                    }
                    else -> data.int / 2147483648f
                }
            }
            mono /= format.channels
            while (next <= frame) {
                val fraction = (next - (frame - 1)).coerceIn(0.0, 1.0).toFloat()
                val value = if (frame == 0L) mono else previous + (mono - previous) * fraction
                if (count == 0) startUs = origin + (next * 1000000.0 / format.rate).toLong()
                samples[count++] = value
                energy += value * value
                quiet = if (abs(value) < 0.008f) quiet + 1 else 0
                next += step
                if (count >= limitSamples || (count >= 32000 && quiet >= 9600)) emit()
            }
            previous = mono; frame++
        }
        lastEndUs = ptsUs + frames * 1000000L / format.rate
    }

    private fun emit() {
        // An energy gate rejects silence; it is not a speech-versus-music classifier.
        if (count >= 16000 && energy / count >= 0.00004) offer(AudioWindow(samples.copyOf(count), startUs))
        else offer(AudioWindow(FloatArray(0), startUs))
        count = 0; energy = 0.0; quiet = 0
    }
}