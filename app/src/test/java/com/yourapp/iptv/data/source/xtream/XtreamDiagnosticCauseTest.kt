package com.yourapp.iptv.data.source.xtream

import com.yourapp.iptv.data.diagnostics.DiagnosticFailures
import com.yourapp.iptv.domain.diagnostics.DiagnosticIssue
import com.yourapp.iptv.domain.model.PlaylistLoadException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*

class XtreamDiagnosticCauseTest {
    private val server = MockWebServer()
    @Before fun setup() { server.start() }
    @After fun cleanup() { server.shutdown() }
    private fun failure(client: OkHttpClient = OkHttpClient.Builder().callTimeout(2,TimeUnit.SECONDS).build(),limit: Int = 1024,
        url: okhttp3.HttpUrl = server.url("/player_api.php")): PlaylistLoadException = runBlocking {
        try { XtreamHttpClient(client).read(url,limit) { reader,_ -> reader.readText() };error("Expected failure") }
        catch (error: PlaylistLoadException) { error }
    }
    @Test fun forbiddenHttpRetainsItsExactCodeWithoutChangingTheExistingUserMessage() {
        server.enqueue(MockResponse().setResponseCode(403).setBody("private-password"))
        val error = failure()
        assertEquals(DiagnosticIssue.HTTP to 403,DiagnosticFailures.classify(error))
        assertEquals("رفض السيرفر الدخول. تحقق من بيانات Xtream وصلاحية الحساب.",error.message)
    }
    @Test fun dnsAndSizeCausesArePreservedForDiagnosisRatherThanCollapsedIntoAGenericMessage() {
        val client = OkHttpClient.Builder().proxy(java.net.Proxy.NO_PROXY).dns(object : okhttp3.Dns {
            override fun lookup(hostname: String): List<java.net.InetAddress> = throw UnknownHostException("private-host.invalid")
        }).build()
        assertEquals(DiagnosticIssue.DNS,DiagnosticFailures.classify(failure(client,url = "https://synthetic.invalid/player_api.php".toHttpUrl())).first)
        server.enqueue(MockResponse().setBody("x".repeat(2048)))
        assertEquals(DiagnosticIssue.LIMIT,DiagnosticFailures.classify(failure()).first)
    }
}