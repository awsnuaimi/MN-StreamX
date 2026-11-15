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

/** The selection key is shared only by media items created for the same channel selection. */
internal class LiveBufferAttempt(val selection: Any, val afterInterruption: Boolean)

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
    private var bufferAttempt: LiveBufferAttempt? = null
    private var recoveryStartBuffered = false
    private var lastResumeMs: Long? = null
    private var lastResumeTargetMs = LivePlaybackTuning.REBUFFER_MS
    private var pendingTargetMs: Int? = null
    private var pendingSinceMs = 0L

    override fun onPrepared(playerId: PlayerId) {
        resetAttempt()
        super.onPrepared(playerId)
    }

    override fun onStopped(playerId: PlayerId) {
        resetAttempt()
        super.onStopped(playerId)
    }

    override fun onReleased(playerId: PlayerId) {
        bufferAttempt = null
        resetAttempt()
        super.onReleased(playerId)
    }

    override fun shouldContinueLoading(parameters: LoadControl.Parameters): Boolean {
        if (!parameters.playWhenReady) pendingTargetMs = null
        return super.shouldContinueLoading(parameters)
    }

    override fun shouldStartPlayback(parameters: LoadControl.Parameters): Boolean {
        if (!parameters.playWhenReady) {
            pendingTargetMs = null
            return super.shouldStartPlayback(parameters)
        }

        val timeline = parameters.timeline
        val periodUid = parameters.mediaPeriodId.periodUid
        val periodIndex = timeline.getIndexOfPeriod(periodUid)
        val currentWindow = if (periodIndex != C.INDEX_UNSET) {
            timeline.getWindow(timeline.getPeriod(periodIndex, period).windowIndex, window)
        } else {
            null
        }
        val attempt = currentWindow?.mediaItem?.localConfiguration?.tag as? LiveBufferAttempt
        val currentWindowUid = currentWindow?.uid ?: periodUid
        if (attempt !== bufferAttempt || (attempt == null && windowUid != currentWindowUid)) {
            if (attempt == null || attempt.selection !== bufferAttempt?.selection) resetRebufferHistory()
            bufferAttempt = attempt
            windowUid = currentWindowUid
            pendingTargetMs = null
            recoveryStartBuffered = false
        }

        val recoveryStart = attempt?.afterInterruption == true && !recoveryStartBuffered
        if (!parameters.rebuffering && !recoveryStart) {
            pendingTargetMs = null
            return super.shouldStartPlayback(parameters)
        }

        val now = nowMs()
        val targetMs = pendingTargetMs ?: rebufferTargetMs(now).also {
            pendingTargetMs = it
            pendingSinceMs = now
        }
        // An isolated reconnect can start quickly. Its resume still counts toward a later interruption.
        val fastRecoveryStart = recoveryStart && !parameters.rebuffering && targetMs == LivePlaybackTuning.REBUFFER_MS
        if (fastRecoveryStart) {
            if (!super.shouldStartPlayback(parameters)) return false
        } else if (!hasEnoughToResume(parameters, currentWindow, targetMs, now)) {
            return false
        }

        pendingTargetMs = null
        if (recoveryStart) recoveryStartBuffered = true
        lastResumeMs = now
        lastResumeTargetMs = targetMs
        return true
    }

    private fun hasEnoughToResume(
        parameters: LoadControl.Parameters,
        currentWindow: Timeline.Window?,
        targetMs: Int,
        now: Long
    ): Boolean {
        val targetUs = Util.msToUs(targetMs.toLong())
        // The source's target offset describes its preferred latency. Use the live window
        // itself to leave room for buffering without requiring media outside that window.
        val windowLimitUs = if (currentWindow != null && currentWindow.isLive && currentWindow.durationUs > 0) {
            val startupUs = Util.msToUs(LivePlaybackTuning.START_BUFFER_MS.toLong())
            minOf(currentWindow.durationUs, maxOf(currentWindow.durationUs / 2, startupUs))
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
            return now - pendingSinceMs >= LivePlaybackTuning.MAX_ADAPTIVE_WAIT_MS &&
                parameters.bufferedDurationUs >= fallbackUs && super.shouldStartPlayback(parameters)
        }
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
        lastResumeMs = null
        lastResumeTargetMs = LivePlaybackTuning.REBUFFER_MS
    }

    private fun resetAttempt() {
        if (bufferAttempt == null) resetRebufferHistory()
        windowUid = null
        pendingTargetMs = null
        recoveryStartBuffered = false
    }
}