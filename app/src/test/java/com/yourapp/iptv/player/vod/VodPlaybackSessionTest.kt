package com.yourapp.iptv.player.vod

import android.app.Application
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.TrackNameProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34],application = Application::class)
@androidx.annotation.OptIn(UnstableApi::class)
class VodPlaybackSessionTest {
    private val fake = VodTestPlayer()
    private val captures = mutableListOf<Triple<Long,Long,Boolean>>()
    private var now = 0L
    private val resets = mutableListOf<Long>()
    private fun session() = VodPlaybackSession(fake.player,TrackNameProvider { it.label ?: "Track" },
        VodTrackPreferences(),{ a,b,c -> captures.add(Triple(a,b,c)) },{ now },{ resets.add(it) })
    @Test fun hiddenControlsAvoidPositionRecompositionButStillSavePlaybackPeriodically() {
        val session = session();val initial = session.snapshot
        fake.position = 80_000;now = 10_000;session.tick(false)
        assertEquals(initial,session.snapshot);assertEquals(listOf(Triple(80_000L,200_000L,false)),captures)
        session.tick(true);assertEquals(80_000L,session.snapshot.position);session.close()
    }
    @Test fun lifecyclePauseAndRepeatedDisposalSaveWithoutDuplicatingListenersOrReleasingTwice() {
        val session = session();session.pause();session.close();session.close()
        assertEquals(1,captures.size);assertEquals(1,fake.releases);assertTrue(fake.listeners.isEmpty())
        assertFalse(fake.playWhenReady)
    }
    @Test fun endOfMediaIsRecordedAsCompleteAndDisposalCannotTurnItBackIntoResumeProgress() {
        val session = session();fake.position = fake.duration;fake.state = Player.STATE_ENDED;fake.playing = false;fake.fire()
        session.close();assertTrue(captures.last().third);assertEquals(1,captures.size)
    }
    @Test fun seeksRespectTheMediaBoundsAndUnknownDurationsDisableSeeking() {
        val session = session();session.seekBy(Long.MAX_VALUE);assertEquals(200_000L,fake.seeks.last())
        session.seekBy(Long.MIN_VALUE);assertEquals(0L,fake.seeks.last())
        fake.duration = C.TIME_UNSET;fake.fire();session.seekTo(10_000)
        assertFalse(session.snapshot.seekable);assertEquals(2,fake.seeks.size);session.close()
        assertEquals(listOf(200_000L),resets)
    }
    @Test fun failingProgressStorageCannotCrashPlaybackOrLeakTheOwnedPlayer() {
        val session = VodPlaybackSession(fake.player,TrackNameProvider { "Track" },VodTrackPreferences(),
            capture = { _,_,_ -> error("Synthetic storage failure") },clearResume = { error("Synthetic reset failure") })
        session.restart();session.pause();session.close()
        assertEquals(1,fake.releases);assertTrue(fake.listeners.isEmpty())
    }
    @Test fun replayAndRetryUseTheExistingPlayerAndClearItsFailureState() {
        val session = session();fake.state = Player.STATE_ENDED;fake.playing = false;fake.fire()
        session.play();assertEquals(0L,fake.position);assertTrue(fake.playWhenReady)
        fake.error = PlaybackException("Synthetic",null,PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED);fake.fire()
        assertTrue(session.snapshot.failed);session.retry()
        assertFalse(session.snapshot.failed);assertEquals(1,fake.prepares);session.close()
    }
}