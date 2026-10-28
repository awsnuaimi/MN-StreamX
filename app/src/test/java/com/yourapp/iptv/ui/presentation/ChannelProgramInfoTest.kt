package com.yourapp.iptv.ui.presentation

import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.ui.state.HomeProgramUiState
import org.junit.Assert.*
import org.junit.Test

class ChannelProgramInfoTest {
    private val channel = Channel("1", "Synthetic", streamUrl = "https://synthetic.invalid/private")
    private fun project(now: Long, vararg rows: Program, stale: Boolean = false) = ChannelProgramInfo.from(channel,
        HomeProgramUiState(channel.id, channel.streamUrl, EpgSchedule(rows.toList(), stale), now))
    @Test fun realCurrentAndNextHaveCorrectProgressRegardlessOfProviderOrder() {
        val current = Program("Current", 100, 300);val next = Program("Next", 300, 500)
        val value = project(200, next, Program("Expired", 0, 50), current)
        assertEquals(current, value.current);assertEquals(next, value.next);assertEquals(0.5f, value.progress!!, 0.001f)
    }
    @Test fun aProgramBoundaryAdvancesWithoutKeepingTheExpiredTitle() {
        val first = Program("First", 100, 300);val second = Program("Second", 300, 500)
        assertEquals("Second", project(300, first, second).current?.title)
        assertEquals(0f, project(300, first, second).progress!!, 0.001f)
        assertNull(project(500, first, second).current);assertNull(project(500, first, second).progress)
    }
    @Test fun gapsMalformedRowsAndBlankTitlesNeverInventAProgramOrProgress() {
        val value = project(200, Program("Expired", 0, 100), Program("Broken", 300, 250),
            Program(" ", 250, 300), Program("Next", 400, 500))
        assertNull(value.current);assertNull(value.progress);assertEquals("Next", value.next?.title)
    }
    @Test fun foreignChannelsAndChangedStreamsCannotReuseAnOldGuide() {
        val state = HomeProgramUiState(channel.id, channel.streamUrl, EpgSchedule(listOf(Program("Secret title", 0, 100))), 50)
        assertEquals(ChannelProgramInfo(), ChannelProgramInfo.from(channel.copy(id = "2"), state))
        assertEquals(ChannelProgramInfo(), ChannelProgramInfo.from(channel.copy(streamUrl = "https://other.invalid"), state))
        val value = ChannelProgramInfo.from(channel, state)
        assertFalse(value.toString().contains("Secret"));assertFalse(value.toString().contains("https://"))
    }
    @Test fun staleValidDataRemainsMarkedAndExtremeTimestampsDoNotOverflowProgress() {
        val value = project(0, Program("Extreme", Long.MIN_VALUE, Long.MAX_VALUE), stale = true)
        assertTrue(value.stale);assertEquals(0.5f, value.progress!!, 0.001f)
        val nearLimit = project(Long.MAX_VALUE - 1, Program("Narrow", Long.MAX_VALUE - 2, Long.MAX_VALUE))
        assertEquals(0.5f, nearLimit.progress!!, 0.001f)
    }
}