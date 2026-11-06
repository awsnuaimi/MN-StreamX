package mnstudio.apps.mnstreamx.player.vod

import android.app.Application
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.TrackNameProvider
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.source.MediaSource.MediaPeriodId
import mnstudio.apps.mnstreamx.domain.performance.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34],application = Application::class)
@androidx.annotation.OptIn(UnstableApi::class)
class VodPlaybackLatencyTest {
    private fun stats() = PerformanceJournal.snapshot().firstOrNull { it.stage == PerformanceStage.VOD_FIRST_FRAME }
    @Test fun startupUsesHalfASecondOfMediaAndRebufferingKeepsALargerSafetyMargin() {
        val control = VodPlaybackTuning.createLoadControl()
        control.onPrepared(PlayerId.UNSET)
        fun starts(buffered: Long, rebuffering: Boolean) = control.shouldStartPlayback(
            LoadControl.Parameters(PlayerId.UNSET,Timeline.EMPTY,MediaPeriodId("synthetic"),0,
                buffered,1f,true,rebuffering,C.TIME_UNSET))
        assertFalse(starts(400_000,false))
        assertTrue(starts(500_000,false))
        assertFalse(starts(1_000_000,true))
        assertTrue(starts(2_000_000,true))
        control.onReleased(PlayerId.UNSET)
    }
    @Test fun theFirstVideoFrameIsRecordedOnceAndDisposedCallbacksAreIgnored() {
        val before = stats()?.complete ?: 0
        val fake = VodTestPlayer()
        val session = VodPlaybackSession(fake.player,TrackNameProvider { "Track" },VodTrackPreferences(),{ _,_,_ -> })
        val listener = fake.listeners.single()
        listener.onRenderedFirstFrame();listener.onRenderedFirstFrame()
        assertEquals(before + 1,stats()?.complete)
        session.close();listener.onRenderedFirstFrame()
        assertEquals(before + 1,stats()?.complete)
    }
    @Test fun leavingBeforeVideoOrFailingBeforeVideoProducesNoSuccessfulFirstFrame() {
        val before = stats()
        val first = VodTestPlayer()
        VodPlaybackSession(first.player,TrackNameProvider { "Track" },VodTrackPreferences(),{ _,_,_ -> }).close()
        assertEquals((before?.cancelled ?: 0) + 1,stats()?.cancelled)
        val failed = VodTestPlayer()
        val session = VodPlaybackSession(failed.player,TrackNameProvider { "Track" },VodTrackPreferences(),{ _,_,_ -> })
        failed.error = PlaybackException("Synthetic",null,PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED)
        failed.fire();session.close()
        assertEquals((before?.failed ?: 0) + 1,stats()?.failed)
        assertEquals(before?.complete ?: 0,stats()?.complete)
    }
}