package com.yourapp.iptv.player.caption

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.*
import com.yourapp.iptv.domain.caption.PcmFormat
import java.nio.ByteBuffer

@androidx.annotation.OptIn(UnstableApi::class)
internal class CaptionRenderers(context: Context, private val tap: CaptionAudioTap) : DefaultRenderersFactory(context) {
    override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink =
        CaptionAudioSink(DefaultAudioSink.Builder(context).setEnableFloatOutput(false)
            .setEnableAudioTrackPlaybackParams(false).build(), tap)
}

@androidx.annotation.OptIn(UnstableApi::class)
internal class CaptionAudioSink(sink: AudioSink, private val tap: CaptionAudioTap) : ForwardingAudioSink(sink) {
    private var format: PcmFormat? = null
    private var lastBuffer: ByteBuffer? = null
    private var lastPts = Long.MIN_VALUE
    private var basePosition = 0
    // Local generation requires decoded PCM; normal playback uses the original sink/factory.
    override fun supportsFormat(format: Format) = getFormatSupport(format) != AudioSink.SINK_FORMAT_UNSUPPORTED
    override fun getFormatSupport(format: Format): Int = if (format.sampleMimeType == MimeTypes.AUDIO_RAW)
        super.getFormatSupport(format) else AudioSink.SINK_FORMAT_UNSUPPORTED
    override fun getFormatOffloadSupport(format: Format): AudioOffloadSupport = AudioOffloadSupport.DEFAULT_UNSUPPORTED
    override fun configure(inputFormat: Format, specifiedBufferSize: Int, outputChannels: IntArray?) {
        super.configure(inputFormat, specifiedBufferSize, outputChannels)
        val encoding = inputFormat.pcmEncoding
        val bytes = when (encoding) {
            C.ENCODING_PCM_16BIT -> 2
            C.ENCODING_PCM_24BIT -> 3
            C.ENCODING_PCM_32BIT, C.ENCODING_PCM_FLOAT -> 4
            else -> 0
        }
        format = PcmFormat(inputFormat.sampleRate, inputFormat.channelCount, bytes, encoding == C.ENCODING_PCM_FLOAT)
    }
    override fun handleBuffer(buffer: ByteBuffer, presentationTimeUs: Long, encodedAccessUnitCount: Int): Boolean {
        val before = buffer.position()
        if (buffer !== lastBuffer || presentationTimeUs != lastPts) {
            lastBuffer = buffer; lastPts = presentationTimeUs; basePosition = before
        }
        val handled = super.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
        val after = buffer.position()
        val current = format
        if (after > before && current != null && current.supported) {
            val copy = buffer.duplicate().apply { position(before); limit(after) }
            val offset = (before - basePosition) * 1000000L / (current.bytes * current.channels * current.rate)
            tap.accept(copy, presentationTimeUs + offset, current)
        }
        return handled
    }
    override fun getCurrentPositionUs(sourceEnded: Boolean): Long = super.getCurrentPositionUs(sourceEnded).also(tap::position)
    override fun handleDiscontinuity() { super.handleDiscontinuity(); tap.discontinuity() }
    override fun flush() { super.flush(); lastBuffer = null; tap.discontinuity() }
    override fun reset() { super.reset(); lastBuffer = null; tap.discontinuity() }
}