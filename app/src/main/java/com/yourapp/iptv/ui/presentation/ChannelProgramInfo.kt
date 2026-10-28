package com.yourapp.iptv.ui.presentation

import com.yourapp.iptv.domain.model.Channel
import com.yourapp.iptv.domain.model.Program
import com.yourapp.iptv.ui.state.HomeProgramUiState

/** A presentation snapshot, never a second EPG loader. No URLs or titles in diagnostics. */
data class ChannelProgramInfo(val current: Program? = null, val next: Program? = null,
    val progress: Float? = null, val stale: Boolean = false) {
    override fun toString() = "ChannelProgramInfo(current=${current != null}, next=${next != null}, stale=$stale)"
    companion object {
        private fun span(end: Long, start: Long): Double {
            val difference = end - start
            return if (difference >= 0) difference.toDouble() else end.toDouble() - start.toDouble()
        }
        fun from(channel: Channel, state: HomeProgramUiState): ChannelProgramInfo {
            if (!state.matches(channel)) return ChannelProgramInfo()
            val now = state.nowMillis
            val current = state.schedule.current(now)?.takeIf { it.title.isNotBlank() }
            val next = state.schedule.upcoming(now, 8).firstOrNull { it.title.isNotBlank() }
            val progress = current?.let {
                (span(now, it.startTime) / span(it.endTime, it.startTime))
                    .toFloat().coerceIn(0f, 1f)
            }
            return ChannelProgramInfo(current, next, progress, state.schedule.isStale)
        }
    }
}