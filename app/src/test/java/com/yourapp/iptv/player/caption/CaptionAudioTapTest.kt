package com.yourapp.iptv.player.caption

import com.yourapp.iptv.domain.caption.PcmFormat
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class CaptionAudioTapTest {
    @Test fun disabledCaptureHasNoQueueAndTheEnabledQueueKeepsOnlyTheLatestWindow() = runTest {
        val tap = CaptionAudioTap()
        fun push(pts: Long) {
            val data = ByteBuffer.allocate(16000 * 5 * 2).order(ByteOrder.LITTLE_ENDIAN)
            repeat(16000 * 5) { data.putShort(16000) }; data.flip()
            tap.accept(data, pts, PcmFormat(16000, 1, 2))
            assertEquals(0, data.position())
        }
        push(0)
        val feed = tap.open(); push(1000000); push(6000000); push(11000000)
        assertEquals(11000000, feed.windows.receive().startUs)
        feed.close(); push(16000000)
        assertTrue(feed.windows.tryReceive().isClosed)
    }
    @Test fun closingAnOldFeedDoesNotDetachTheNewChannel() = runTest {
        val tap = CaptionAudioTap(); val old = tap.open(); val fresh = tap.open()
        old.close()
        tap.accept(ByteBuffer.allocate(16000 * 2 * 2), 0, PcmFormat(16000, 1, 2))
        assertTrue(fresh.windows.tryReceive().isSuccess)
        fresh.close()
    }
}