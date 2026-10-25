package com.yourapp.iptv.data.update

import com.yourapp.iptv.domain.update.UpdateFailure
import com.yourapp.iptv.domain.update.UpdateIssue
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class GitHubUpdateLookupTest {
    private lateinit var server: MockWebServer
    private lateinit var feed: GitHubUpdateFeed
    @Before fun setup() {
        server = MockWebServer(); server.start()
        val http = UpdateHttp(OkHttpClient.Builder().followRedirects(false).build()) { it.host == "localhost" }
        feed = GitHubUpdateFeed("synthetic-owner/packages", http, server.url("/"))
    }
    @After fun cleanup() { server.shutdown() }
    private suspend fun fails(expected: UpdateIssue) {
        val error = runCatching { feed.latest(104101, listOf("arm64-v8a")) }.exceptionOrNull()
        assertTrue(error is UpdateFailure)
        assertEquals(expected, (error as UpdateFailure).issue)
    }

    @Test fun anAccessibleRepositoryWithoutAReleaseIsNotAccusedOfBeingPrivate() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(MockResponse().setBody("""{"private":false}"""))
        fails(UpdateIssue.NO_RELEASE)
        assertEquals("/repos/synthetic-owner/packages/releases/latest", server.takeRequest().path)
        assertEquals("/repos/synthetic-owner/packages", server.takeRequest().path)
    }

    @Test fun anInaccessibleOrPrivateSourceIsReportedSeparately() = runBlocking {
        repeat(2) { server.enqueue(MockResponse().setResponseCode(404)) }
        fails(UpdateIssue.SOURCE_UNAVAILABLE)
        assertEquals(2, server.requestCount)
    }

    @Test fun rateLimitsDoNotTriggerExtraRepositoryRequests() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429))
        fails(UpdateIssue.RATE_LIMIT)
        assertEquals(1, server.requestCount)
    }

    @Test fun anAuthenticationErrorIsNotReportedAsNoRelease() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401))
        fails(UpdateIssue.ACCESS)
        assertEquals(1, server.requestCount)
    }

    @Test fun publishedAssetsAreSelectedFromTheDistributionRepository() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"draft":false,"prerelease":false,"tag_name":"v1.1.42.1","assets":[
            {"name":"MN-StreamX-1.1.42.1-arm64-v8a.apk","size":100,"state":"uploaded",
             "digest":"sha256:${"a".repeat(64)}",
             "browser_download_url":"https://github.com/synthetic-owner/packages/releases/download/v1.1.42.1/MN-StreamX-1.1.42.1-arm64-v8a.apk"}]}"""))
        val update = feed.latest(104101, listOf("arm64-v8a"))!!
        assertEquals(104201L, update.versionCode)
        assertTrue(update.url.startsWith("https://github.com/synthetic-owner/packages/"))
        assertEquals(1, server.requestCount)
        assertNull(server.takeRequest().getHeader("Authorization"))
    }
}