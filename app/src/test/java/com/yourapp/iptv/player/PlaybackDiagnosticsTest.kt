package com.yourapp.iptv.player

import android.app.Application
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.TrackNameProvider
import com.yourapp.iptv.domain.diagnostics.*
import com.yourapp.iptv.player.vod.*
import java.net.UnknownHostException
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34],application = Application::class)
@androidx.annotation.OptIn(UnstableApi::class)
class PlaybackDiagnosticsTest {
    @Before fun setup() { DiagnosticJournal.clear() }
    @After fun cleanup() { DiagnosticJournal.clear() }
    @Test fun media3FailuresAreClassifiedWithoutRetainingTheirMessageOrStreamUrl() {
        PlaybackDiagnostics.record(PlaybackException("https://private.invalid/user/password",UnknownHostException("private.invalid"),
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED),"a".repeat(64))
        val event = DiagnosticJournal.events.value.single()
        assertEquals(DiagnosticIssue.DNS,event.issue);assertEquals(2001,event.code)
        assertFalse(event.toString().contains("private.invalid"))
    }
    @Test fun decoderProblemsPreserveTheExactMedia3CodeAndHaveAReadableCategory() {
        PlaybackDiagnostics.record(PlaybackException("Private metadata",null,PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED))
        assertEquals(DiagnosticIssue.DECODER,DiagnosticJournal.events.value.single().issue)
        assertEquals(4005,DiagnosticJournal.events.value.single().code)
    }
    @Test fun theActualVodSessionRecordsAFaultOnceUntilRecoveryRatherThanOnEveryEvent() {
        val fake = VodTestPlayer()
        val session = VodPlaybackSession(fake.player,TrackNameProvider { "Track" },VodTrackPreferences(),
            capture = { _,_,_ -> },diagnosticSource = "a".repeat(64))
        try {
            val error = PlaybackException("Synthetic",null,PlaybackException.ERROR_CODE_DECODER_INIT_FAILED)
            fake.error = error;fake.fire();fake.fire();assertEquals(1,DiagnosticJournal.events.value.size)
            fake.error = null;fake.fire();fake.error = error;fake.fire()
            assertEquals(2,DiagnosticJournal.events.value.size)
            assertTrue(DiagnosticJournal.events.value.all { it.sourceKey == "a".repeat(64) })
        } finally { session.close() }
        assertEquals(1,fake.releases)
    }
}