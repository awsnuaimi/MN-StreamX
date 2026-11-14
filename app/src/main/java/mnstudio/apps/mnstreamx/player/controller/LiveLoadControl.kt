package mnstudio.apps.mnstreamx.player.controller

import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.upstream.DefaultAllocator

/** One instance per live player. All state is owned by the playback thread. */
@androidx.annotation.OptIn(UnstableApi::class)
internal class LiveLoadControl(
    private val nowMs: () -> Long = SystemClock::elapsedRealtime
) : DefaultLoadControl(
    DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE),
    LivePlaybackTuning.MIN_BUFFER_MS,
    LivePlaybackTuning.MAX_BUFFER_MS,
    LivePlaybackTuning.START_BUFFER_MS,
    LivePlaybackTuning.REBUFFER_MS,
    LivePlaybackTuning.TARGET_BUFFER_BYTES,
    false,
    0,
    false
) {
    private val period = Timeline.Period()
    private val window = Timeline.Window()
    private var windowUid: Any? = null
    private var lastResumeMs: Long? = null
    private var lastResumeTargetMs = LivePlaybackTuning.REBUFFER_MS
    private var pendingTargetMs: Int? = null
    private var pendingSinceMs = 0L

    override fun onPrepared(playerId: PlayerId) {
        resetRebufferHistory()
        super.onPrepared(playerId)
    }

    override fun onStopped(playerId: PlayerId) {
        resetRebufferHistory()
        super.onStopped(playerId)
    }

    override fun onReleased(playerId: PlayerId) {
        resetRebufferHistory()
        super.onReleased(playerId)
    }

    override fun shouldContinueLoading(parameters: LoadControl.Parameters): Boolean {
        if (!parameters.rebuffering || !parameters.playWhenReady) pendingTargetMs = null
        return super.shouldContinueLoading(parameters)
    }

    override fun shouldStartPlayback(parameters: LoadControl.Parameters): Boolean {
        if (!parameters.rebuffering || !parameters.playWhenReady) {
            pendingTargetMs = null
            return super.shouldStartPlayback(parameters)
        }

        val timeline = parameters.timeline
        val periodUid = parameters.mediaPeriodId.periodUid
        val periodIndex = timeline.getIndexOfPeriod(periodUid)
        val currentWindow = if (periodIndex == C.INDEX_UNSET) {
            periodUid
        } else {
            timeline.getWindow(timeline.getPeriod(periodIndex, period).windowIndex, window).uid
        }
        if (windowUid != currentWindow) {
            resetRebufferHistory()
            windowUid = currentWindow
        }

        val now = nowMs()
        val targetMs = pendingTargetMs ?: rebufferTargetMs(now).also {
            pendingTargetMs = it
            pendingSinceMs = now
        }
        val targetUs = Util.msToUs(targetMs.toLong())
        // The source's target offset describes its preferred latency. Use the live window
        // itself to leave room for buffering without requiring media outside that window.
        val windowLimitUs = if (periodIndex != C.INDEX_UNSET && window.isLive && window.durationUs > 0) {
            val startupUs = Util.msToUs(LivePlaybackTuning.START_BUFFER_MS.toLong())
            minOf(window.durationUs, maxOf(window.durationUs / 2, startupUs))
        } else {
            Long.MAX_VALUE
        }
        val mediaTargetUs = minOf(
            Util.getMediaDurationForPlayoutDuration(targetUs, parameters.playbackSpeed),
            Util.msToUs(LivePlaybackTuning.MAX_BUFFER_MS.toLong()),
            windowLimitUs
        )
        if (parameters.bufferedDurationUs < mediaTargetUs &&
            allocator.totalBytesAllocated < LivePlaybackTuning.TARGET_BUFFER_BYTES
        ) {
            // If the live edge is too close to build the extra cushion, resume using
            // Media3's normal threshold after a bounded wait once rendering can proceed.
            val fallbackUs = minOf(mediaTargetUs, Util.msToUs(LivePlaybackTuning.START_BUFFER_MS.toLong()))
            if (now - pendingSinceMs < LivePlaybackTuning.MAX_ADAPTIVE_WAIT_MS ||
                parameters.bufferedDurationUs < fallbackUs || !super.shouldStartPlayback(parameters)
            ) return false
        }

        pendingTargetMs = null
        lastResumeMs = now
        lastResumeTargetMs = targetMs
        return true
    }

    private fun rebufferTargetMs(now: Long): Int {
        val lastResume = lastResumeMs ?: return LivePlaybackTuning.REBUFFER_MS
        if (now - lastResume >= LivePlaybackTuning.REBUFFER_REPEAT_WINDOW_MS) {
            return LivePlaybackTuning.REBUFFER_MS
        }
        return if (lastResumeTargetMs == LivePlaybackTuning.REBUFFER_MS) {
            LivePlaybackTuning.REPEATED_REBUFFER_MS
        } else {
            LivePlaybackTuning.FREQUENT_REBUFFER_MS
        }
    }

    private fun resetRebufferHistory() {
        windowUid = null
        lastResumeMs = null
        lastResumeTargetMs = LivePlaybackTuning.REBUFFER_MS
        pendingTargetMs = null
    }
}