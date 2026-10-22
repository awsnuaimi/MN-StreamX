package com.yourapp.iptv.domain.caption

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PcmWindowAssemblerTest {
    @Test fun shorteningAPartialWindowPreservesPcmAndContinuousTimestamps() {
        val windows = mutableListOf<AudioWindow>()
        val assembler = PcmWindowAssembler(PcmFormat(16000, 1, 2), windows::add)
        fun audio(seconds: Int) = ByteBuffer.allocate(seconds * 32000).order(ByteOrder.LITTLE_ENDIAN).apply {
            repeat(seconds * 16000) { putShort(16384) }; flip()
        }
        assembler.accept(audio(2), 0)
        assembler.limitWindow(3500)
        val rest = audio(5); assembler.accept(rest, 2000000)
        assertEquals(0, rest.position())
        assertEquals(listOf(56000, 56000), windows.map { it.samples.size })
        assertEquals(listOf(0L, 3500000L), windows.map { it.startUs })
        assertTrue(windows.flatMap { it.samples.asList() }.all { kotlin.math.abs(it - 0.5f) < 0.001f })
    }
    @Test fun requestedWindowSizesStayWithinTheFixedSampleBuffer() {
        for ((requested, expected) in listOf(0 to 2000L, Int.MAX_VALUE to 5000L)) {
            val windows = mutableListOf<AudioWindow>()
            val assembler = PcmWindowAssembler(PcmFormat(16000, 1, 2), windows::add)
            assembler.limitWindow(requested)
            val audio = ByteBuffer.allocate(16000 * 10 * 2).order(ByteOrder.LITTLE_ENDIAN)
            repeat(16000 * 10) { audio.putShort(16384) }; audio.flip()
            assembler.accept(audio, 0)
            assertTrue(windows.isNotEmpty()); assertTrue(windows.all { it.durationMillis == expected })
        }
    }
    @Test fun stereo48kIsDownmixedResampledAndDoesNotMutateThePlaybackBuffer() {
        val windows = mutableListOf<AudioWindow>()
        val input = ByteBuffer.allocate(48000 * 5 * 4).order(ByteOrder.LITTLE_ENDIAN)
        repeat(48000 * 5) { input.putShort(16384); input.putShort(0) }; input.flip()
        val assembler = PcmWindowAssembler(PcmFormat(48000, 2, 2), windows::add)
        val before = input.position(); assembler.accept(input, 7000000)
        assertEquals(before, input.position()); assertEquals(1, windows.size)
        assertEquals(80000, windows.single().samples.size)
        assertEquals(7000000, windows.single().startUs)
        assertTrue(windows.single().samples.all { kotlin.math.abs(it - 0.25f) < 0.001f })
    }
    @Test fun fragmented44100PcmHasStableTimingAndBoundedWindowSizes() {
        val windows = mutableListOf<AudioWindow>()
        val assembler = PcmWindowAssembler(PcmFormat(44100, 1, 2), windows::add)
        repeat(600) { index ->
            val packet = ByteBuffer.allocate(441 * 2).order(ByteOrder.LITTLE_ENDIAN)
            repeat(441) { packet.putShort(12000) }; packet.flip()
            assembler.accept(packet, index * 10000L)
        }
        assertEquals(1, windows.size); assertEquals(80000, windows[0].samples.size)
        assertEquals(0, windows[0].startUs)
    }
    @Test fun silenceProducesOnlyEmptyHeartbeatsNeverNativeInferenceSamples() {
        val windows = mutableListOf<AudioWindow>()
        PcmWindowAssembler(PcmFormat(16000, 1, 2), windows::add).accept(ByteBuffer.allocate(16000 * 10 * 2), 0)
        assertTrue(windows.isNotEmpty()); assertTrue(windows.all { it.samples.isEmpty() })
    }
    @Test fun seeksDiscardThePreviousPartialWindow() {
        val windows = mutableListOf<AudioWindow>()
        val a = PcmWindowAssembler(PcmFormat(16000, 1, 2), windows::add)
        fun audio(seconds: Int) = ByteBuffer.allocate(16000 * seconds * 2).order(ByteOrder.LITTLE_ENDIAN).apply {
            repeat(16000 * seconds) { putShort(12000) }; flip()
        }
        a.accept(audio(3), 0); a.accept(audio(5), 20000000)
        assertEquals(1, windows.size); assertEquals(20000000, windows.single().startUs)
    }
    @Test fun floatingPointNaNIsSilenceAndInvalidFormatsNeverCrash() {
        val rows = mutableListOf<AudioWindow>()
        val data = ByteBuffer.allocate(16000 * 5 * 4).order(ByteOrder.LITTLE_ENDIAN)
        repeat(16000 * 5) { data.putFloat(Float.NaN) }; data.flip()
        PcmWindowAssembler(PcmFormat(16000, 1, 4, true), rows::add).accept(data, 0)
        assertTrue(rows.all { it.samples.isEmpty() })
        PcmWindowAssembler(PcmFormat(0, 0, 0), rows::add).accept(data, 0)
    }
    @Test fun integer24And32BitInputsKeepTheCorrectSignAndScale() {
        for (bytes in listOf(3, 4)) {
            val rows = mutableListOf<AudioWindow>()
            val data = ByteBuffer.allocate(16000 * 5 * bytes).order(ByteOrder.LITTLE_ENDIAN)
            repeat(16000 * 5) { if (bytes == 3) { data.put(0); data.put(0); data.put(0xC0.toByte()) } else data.putInt(-1073741824) }
            data.flip(); PcmWindowAssembler(PcmFormat(16000, 1, bytes), rows::add).accept(data, 0)
            assertTrue(rows.single().samples.all { kotlin.math.abs(it + 0.5f) < 0.001f })
        }
    }
}