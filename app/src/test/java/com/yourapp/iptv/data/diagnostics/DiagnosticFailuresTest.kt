package com.yourapp.iptv.data.diagnostics

import com.yourapp.iptv.data.network.HttpResponseFailure
import com.yourapp.iptv.data.network.ResponseLimitFailure
import com.yourapp.iptv.domain.diagnostics.*
import com.yourapp.iptv.domain.model.PlaylistLoadException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test

class DiagnosticFailuresTest {
    @Test fun wrappedNetworkCausesSurviveTheExistingUserFacingPlaylistException() {
        assertEquals(DiagnosticIssue.DNS,DiagnosticFailures.classify(PlaylistLoadException("Safe message",UnknownHostException("secret-host.invalid"))).first)
        assertEquals(DiagnosticIssue.TLS,DiagnosticFailures.classify(IOException(SSLHandshakeException("secret URL"))).first)
        assertEquals(DiagnosticIssue.TIMEOUT,DiagnosticFailures.classify(SocketTimeoutException("private password")).first)
        assertEquals(DiagnosticIssue.CONNECTION,DiagnosticFailures.classify(PlaylistLoadException("Safe message",IOException("private response"))).first)
    }
    @Test fun typedHttpAndSizeErrorsDoNotDependOnParsingExceptionMessages() {
        assertEquals(DiagnosticIssue.HTTP to 403,DiagnosticFailures.classify(PlaylistLoadException("unchanged",HttpResponseFailure(403))))
        assertEquals(DiagnosticIssue.LIMIT,DiagnosticFailures.classify(PlaylistLoadException("unchanged",ResponseLimitFailure())).first)
        assertEquals(DiagnosticIssue.INVALID_RESPONSE,DiagnosticFailures.classify(IllegalArgumentException("private response")).first)
    }
    @Test fun cancellationIsNotRecordedAsAnApplicationFault() {
        val before = DiagnosticJournal.events.value.size
        try { DiagnosticFailures.record(DiagnosticArea.PLAYLIST,CancellationException());fail("Expected cancellation") }
        catch (_: CancellationException) { assertEquals(before,DiagnosticJournal.events.value.size) }
    }
    @Test fun theExistingJavaConstructorAndTheNewTypedCauseAreBothSupported() {
        val old = PlaylistLoadException::class.java.getConstructor(String::class.java).newInstance("Existing message")
        assertEquals("Existing message",old.message);assertNull(old.cause)
        val cause = IOException("Synthetic cause")
        assertSame(cause,PlaylistLoadException("Existing message",cause).cause)
    }
}