package mnstudio.apps.mnstreamx.player.controller

import android.app.Application
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.source.MediaSource.MediaPeriodId
import androidx.media3.exoplayer.source.SinglePeriodTimeline
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@androidx.annotation.OptIn(UnstableApi::class)
class LiveLoadControlTest {
    private val player = PlayerId.UNSET
    private val firstPeriod = Any()
    private var clockMs = 0L
    private val loadControl = LiveLoadControl { clockMs }

    @Before fun prepare() = loadControl.onPrepared(player)

    @After fun release() = loadControl.onReleased(player)

    private fun params(
        bufferedMs: Long,
        rebuffering: Boolean = true,
        periodUid: Any = firstPeriod,
        speed: Float = 1f,
        targetOffsetMs: Long? = null,
        playWhenReady: Boolean = true,
        timeline: Timeline = Timeline.EMPTY
    ) = LoadControl.Parameters(
        player,
        timeline,
        MediaPeriodId(periodUid),
        0L,
        bufferedMs * 1_000,
        speed,
        playWhenReady,
        rebuffering,
        targetOffsetMs?.times(1_000) ?: C.TIME_UNSET
    )

    @Test fun openingAChannelStillUsesTheFastStartupThreshold() {
        assertFalse(loadControl.shouldStartPlayback(params(499, rebuffering = false)))
        assertTrue(loadControl.shouldStartPlayback(params(500, rebuffering = false)))
    }

    @Test fun aSecondStallInTheSameMinuteWaitsForMoreBufferedMedia() {
        assertFalse(loadControl.shouldStartPlayback(params(2_999)))
        assertTrue(loadControl.shouldStartPlayback(params(3_000)))
        clockMs = 10_000
        assertFalse(loadControl.shouldStartPlayback(params(4_999)))
        assertTrue(loadControl.shouldStartPlayback(params(5_000)))
    }

    @Test fun stablePlaybackRestoresTheNormalThresholdForTheNextStall() {
        assertTrue(loadControl.shouldStartPlayback(params(3_000)))
        clockMs = 60_000
        assertTrue(loadControl.shouldStartPlayback(params(3_000)))
    }

    @Test fun aStallKeepsItsTargetUntilPlaybackResumes() {
        assertTrue(loadControl.shouldStartPlayback(params(3_000)))
        clockMs = 59_000
        assertFalse(loadControl.shouldStartPlayback(params(3_000)))
        clockMs = 61_000
        assertFalse(loadControl.shouldStartPlayback(params(3_000)))
        assertTrue(loadControl.shouldStartPlayback(params(5_000)))
    }

    @Test fun replacingOrPreparingTheSourceClearsThePreviousChannelHistory() {
        assertTrue(loadControl.shouldStartPlayback(params(3_000)))
        assertFalse(loadControl.shouldStartPlayback(params(3_000)))
        val nextPeriod = Any()
        assertTrue(loadControl.shouldStartPlayback(params(3_000, periodUid = nextPeriod)))
        assertFalse(loadControl.shouldStartPlayback(params(3_000, periodUid = nextPeriod)))
        loadControl.onStopped(player)
        loadControl.onPrepared(player)
        assertTrue(loadControl.shouldStartPlayback(params(3_000, periodUid = nextPeriod)))
    }

    @Test fun refreshedTimelinesForTheSameWindowKeepTheRebufferHistory() {
        val firstTimeline = SinglePeriodTimeline(10_000_000L, true, true, false, null, MediaItem.EMPTY)
        val firstUid = firstTimeline.getUidOfPeriod(0)
        assertTrue(loadControl.shouldStartPlayback(params(3_000, periodUid = firstUid, timeline = firstTimeline)))

        val refreshedTimeline = SinglePeriodTimeline(20_000_000L, true, true, false, null, MediaItem.EMPTY)
        val refreshedUid = refreshedTimeline.getUidOfPeriod(0)
        assertFalse(loadControl.shouldStartPlayback(
            params(3_000, periodUid = refreshedUid, timeline = refreshedTimeline)
        ))
        assertTrue(loadControl.shouldStartPlayback(
            params(5_000, periodUid = refreshedUid, timeline = refreshedTimeline)
        ))
    }

    @Test fun aPausedBufferingCheckDoesNotCountAsAPlaybackResume() {
        assertTrue(loadControl.shouldStartPlayback(params(3_000, playWhenReady = false)))
        assertTrue(loadControl.shouldStartPlayback(params(3_000)))
    }

    @Test fun liveEdgeAndPlaybackSpeedStillLimitTheWait() {
        assertFalse(loadControl.shouldStartPlayback(params(2_999, speed = 1.5f, targetOffsetMs = 4_000)))
        assertTrue(loadControl.shouldStartPlayback(params(3_000, speed = 1.5f, targetOffsetMs = 4_000)))
        clockMs = 60_000
        assertFalse(loadControl.shouldStartPlayback(params(4_499, speed = 1.5f)))
        assertTrue(loadControl.shouldStartPlayback(params(4_500, speed = 1.5f)))
    }

    @Test fun highBitrateStreamsMayResumeAtTheExistingByteTarget() {
        val allocator = loadControl.allocator
        repeat(LivePlaybackTuning.TARGET_BUFFER_BYTES / C.DEFAULT_BUFFER_SEGMENT_SIZE - 1) {
            allocator.allocate()
        }
        assertFalse(loadControl.shouldStartPlayback(params(1_000)))
        allocator.allocate()
        assertTrue(loadControl.shouldStartPlayback(params(1_000)))
        assertFalse(loadControl.shouldContinueLoading(params(1_000)))
    }
}