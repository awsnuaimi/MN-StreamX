package com.yourapp.iptv.data.diagnostics

import com.yourapp.iptv.domain.diagnostics.*
import com.yourapp.iptv.domain.model.*
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*

class DiagnosticSourceProbeTest {
    private val server = MockWebServer()
    private val transport = DiagnosticTransport(OkHttpClient.Builder().callTimeout(2,TimeUnit.SECONDS).build())
    private val probe = DiagnosticSourceProbe(transport)
    @Before fun setup() { server.start() }
    @After fun cleanup() { server.shutdown() }
    private fun source() = AppSettings(source = PlaylistSource.XTREAM,xtreamServer = server.url("/proxy/").toString(),
        xtreamUsername = "private-user",xtreamPassword = " private?&password# ")
    private fun response(value: String, status: Int = 200) { server.enqueue(MockResponse().setResponseCode(status).setBody(value)) }
    private fun run(source: AppSettings = source()) = runBlocking {
        val checks = mutableListOf<DiagnosticCheck>();probe.run(source,checks::add);checks.toList()
    }
    @Test fun configuredXtreamCredentialsUseTheExistingUrlBuilderAndOnlyRequestAuthenticationAndCategories() {
        response("""{"user_info":{"auth":1,"status":"Active","username":"private-user","password":"private"}}""")
        response("[{\"category_id\":\"1\"}]");response("[]");response("[{\"category_id\":\"2\"},{\"category_id\":\"3\"}]")
        val rows = run()
        assertEquals(4,rows.size);assertEquals(listOf(null,1,0,2),rows.map { it.count })
        assertEquals(DiagnosticStatus.PASS,rows.first().status);assertEquals(DiagnosticStatus.NOTICE,rows[2].status)
        val requests = List(4) { server.takeRequest(1,TimeUnit.SECONDS)!! }
        assertTrue(requests.all { it.requestUrl!!.encodedPath == "/proxy/player_api.php" })
        assertTrue(requests.all { it.requestUrl!!.queryParameter("password") == source().xtreamPassword })
        assertEquals(listOf(null,"get_live_categories","get_vod_categories","get_series_categories"),requests.map { it.requestUrl!!.queryParameter("action") })
    }
    @Test fun rejectedAndInactiveAccountsStopBeforeAnyCatalogRequests() {
        response("""{"user_info":{"auth":"0"}}""")
        assertEquals(DiagnosticIssue.AUTH_REJECTED,run().single().issue)
        response("""{"user_info":{"auth":true,"status":"Expired"}}""")
        assertEquals(DiagnosticIssue.ACCOUNT_INACTIVE,run().single().issue);assertEquals(2,server.requestCount)
    }
    @Test fun httpFailuresRetainOnlyTheirStatusAndDoNotTurnIntoAuthenticationGuesses() {
        response("private response body",503)
        val row = run().single();assertEquals(503,row.httpStatus);assertEquals(DiagnosticIssue.HTTP,row.issue)
        assertEquals(1,server.requestCount)
    }
    @Test fun genericM3uChecksOnlyThePrefixAndUsesRangeWithoutDownloadingTheFullPlaylist() {
        response("#EXTM3U\n" + "#EXTINF:-1,Private title\nhttps://private.invalid/user/password\n".repeat(2000))
        val row = run(AppSettings(m3uUrl = server.url("/list.m3u").toString())).single()
        assertEquals(DiagnosticIssue.PLAYLIST_HEADER,row.issue)
        assertEquals("bytes=0-65535",server.takeRequest(1,TimeUnit.SECONDS)!!.getHeader("Range"))
    }
    @Test fun getPhpSourcesRetainXtreamSupportRatherThanBeingMisreportedAsGenericM3u() {
        val source = AppSettings(m3uUrl = server.url("/get.php?username=u&password=p&type=m3u_plus").toString())
        assertEquals(DiagnosticSource.XTREAM,probe.kind(source))
        response("{\"user_info\":{\"auth\":0}}")
        assertEquals(DiagnosticIssue.AUTH_REJECTED,run(source).single().issue)
    }
    @Test fun redirectsAreNotFollowedAndCannotForwardProviderCredentialsToAnotherHost() {
        val target = MockWebServer();target.start()
        try {
            server.enqueue(MockResponse().setResponseCode(302).addHeader("Location",target.url("/private")))
            assertEquals(DiagnosticIssue.REDIRECT,run().single().issue);assertEquals(0,target.requestCount)
        } finally { target.shutdown() }
    }
    @Test fun boundedCategoryResponsesAreInconclusiveRatherThanFalseCatalogFailures() {
        response("{\"user_info\":{\"auth\":1}}")
        response("[" + "{\"category_id\":\"1\",\"name\":\"" + "x".repeat(270000) + "\"}]")
        response("[]");response("[]")
        val rows = run();assertEquals(DiagnosticIssue.LIMIT,rows[1].issue);assertEquals(DiagnosticStatus.NOTICE,rows[1].status)
    }
    @Test fun hostileJsonCannotOverflowTheParserAndDoesNotHideValidCategoryResponses() {
        response("[".repeat(1000) + "0" + "]".repeat(1000));repeat(3) { response("[]") }
        val rows = run();assertEquals(DiagnosticIssue.INVALID_RESPONSE,rows.first().issue);assertEquals(4,rows.size)
    }
    @Test fun categoriesCountOnlyUsableDistinctIdsRatherThanErrorObjectsOrDuplicateRows() {
        response("{\"user_info\":{\"auth\":1}}")
        response("[{},null,{\"error\":\"private\"},{\"category_id\":\"1\"},{\"category_id\":\"1\"}]")
        response("[]");response("[]")
        assertEquals(1,run()[1].count)
    }
    @Test fun anUnconfiguredAccountDoesNotContactAnyServer() {
        assertEquals(DiagnosticIssue.NO_SOURCE,run(AppSettings()).single().issue);assertEquals(0,server.requestCount)
    }
    @Test fun cancellingAnInFlightRequestClosesItWithoutPublishingAFailure() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val rows = mutableListOf<DiagnosticCheck>()
        val job = launch(Dispatchers.IO) { probe.run(source(),rows::add) }
        assertNotNull(server.takeRequest(2,TimeUnit.SECONDS));job.cancelAndJoin()
        assertTrue(rows.isEmpty())
    }
}