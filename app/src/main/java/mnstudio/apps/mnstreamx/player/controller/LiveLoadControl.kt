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
    private var pendingTargetMs: Int? = null

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

        val currentWindow = playbackWindowUid(parameters)
        if (windowUid != currentWindow) {
            resetRebufferHistory()
            windowUid = currentWindow
        }

        val targetMs = pendingTargetMs ?: rebufferTargetMs().also { pendingTargetMs = it }
        val targetUs = Util.msToUs(targetMs.toLong())
        val minimumUs = if (parameters.targetLiveOffsetUs == C.TIME_UNSET) {
            targetUs
        } else {
            minOf(targetUs, parameters.targetLiveOffsetUs / 2)
        }
        if (!super.shouldStartPlayback(parameters)) return false
        val playoutUs = Util.getPlayoutDurationForMediaDuration(
            parameters.bufferedDurationUs, parameters.playbackSpeed
        )
        if (playoutUs < minimumUs && allocator.totalBytesAllocated < LivePlaybackTuning.TARGET_BUFFER_BYTES) {
            return false
        }

        pendingTargetMs = null
        lastResumeMs = nowMs()
        return true
    }

    private fun rebufferTargetMs(): Int {
        val lastResume = lastResumeMs ?: return LivePlaybackTuning.REBUFFER_MS
        return if (nowMs() - lastResume < LivePlaybackTuning.REBUFFER_REPEAT_WINDOW_MS) {
            LivePlaybackTuning.REPEATED_REBUFFER_MS
        } else {
            LivePlaybackTuning.REBUFFER_MS
        }
    }

    private fun playbackWindowUid(parameters: LoadControl.Parameters): Any {
        val timeline = parameters.timeline
        val periodUid = parameters.mediaPeriodId.periodUid
        val index = timeline.getIndexOfPeriod(periodUid)
        if (index == C.INDEX_UNSET) return periodUid
        val windowIndex = timeline.getPeriod(index, period).windowIndex
        return timeline.getWindow(windowIndex, window).uid
    }

    private fun resetRebufferHistory() {
        windowUid = null
        lastResumeMs = null
        pendingTargetMs = null
    }
}