package com.yourapp.iptv.data.update

import com.yourapp.iptv.domain.update.AppUpdate
import com.yourapp.iptv.domain.update.UpdateFailure
import com.yourapp.iptv.domain.update.UpdateIssue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class UpdateDownloadTest {
    private lateinit var server: MockWebServer
    private lateinit var directory: File
    private lateinit var download: UpdateDownload
    private fun http() = UpdateHttp(OkHttpClient.Builder().followRedirects(false).build()) {
        it.host == "localhost" || it.host == "127.0.0.1"
    }
    @Before fun setup() {
        server = MockWebServer(); server.start()
        directory = Files.createTempDirectory("synthetic-update-").toFile()
        download = UpdateDownload(directory, http())
    }
    @After fun cleanup() { server.shutdown(); directory.deleteRecursively() }
    private fun update(bytes: ByteArray) = AppUpdate(104201, "1.1.42.1", "arm64-v8a",
        server.url("/update.apk").toString(), bytes.size.toLong(),
        UpdateDownload.hex(MessageDigest.getInstance("SHA-256").digest(bytes)))

    @Test fun aCompleteDownloadIsAtomicAndAValidCachedFileIsReused() = runBlocking {
        val bytes = ByteArray(32 * 1024) { (it % 251).toByte() }
        server.enqueue(MockResponse().setBody(Buffer().write(bytes)))
        val progress = mutableListOf<Int>()
        val update = update(bytes)
        val file = download.fetch(update) { progress += it }
        assertArrayEquals(bytes, file.readBytes())
        assertEquals(100, progress.last())
        assertEquals(file, download.fetch(update) {})
        assertEquals(1, server.requestCount)
        assertFalse(directory.listFiles().orEmpty().any { it.extension == "part" })
    }

    @Test fun aWrongDigestNeverProducesAnInstallableFile() = runBlocking {
        val bytes = "synthetic payload".toByteArray()
        server.enqueue(MockResponse().setBody(Buffer().write(bytes)))
        try { download.fetch(update(bytes).copy(sha256 = "0".repeat(64))) {}; fail("Accepted a wrong digest") }
        catch (error: UpdateFailure) { assertEquals(UpdateIssue.INTEGRITY, error.issue) }
        assertTrue(directory.listFiles().orEmpty().isEmpty())
    }

    @Test fun aWrongLengthIsRejectedBeforeOpeningATemporaryFile() = runBlocking {
        val bytes = "synthetic payload".toByteArray()
        server.enqueue(MockResponse().setBody("too short"))
        try { download.fetch(update(bytes)) {}; fail("Accepted a wrong length") }
        catch (error: UpdateFailure) { assertEquals(UpdateIssue.INTEGRITY, error.issue) }
        assertTrue(directory.listFiles().orEmpty().isEmpty())
    }

    @Test fun cancellationClosesAnActiveBodyAndRemovesItsPartialFile() = runBlocking {
        val bytes = ByteArray(64 * 1024) { 7 }
        server.enqueue(MockResponse().setBody(Buffer().write(bytes)).throttleBody(1024, 1, TimeUnit.SECONDS))
        val started = CompletableDeferred<Unit>()
        val job = launch { download.fetch(update(bytes)) { started.complete(Unit) } }
        withTimeout(5000) { started.await() }
        job.cancelAndJoin()
        withTimeout(5000) { while (directory.listFiles().orEmpty().isNotEmpty()) delay(20) }
        assertFalse(directory.listFiles().orEmpty().any { it.extension == "apk" })
    }

    @Test fun redirectsCannotSendTheDownloadOutsideTheAllowedHosts() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", "https://example.invalid/update.apk"))
        try { download.fetch(update(byteArrayOf(1))) {}; fail("Accepted a foreign redirect") }
        catch (error: UpdateFailure) { assertEquals(UpdateIssue.INVALID_RELEASE, error.issue) }
        assertEquals(1, server.requestCount)
    }

    @Test fun productionTransportRejectsCleartextCredentialsAndLookalikeHosts() {
        for (url in listOf("http://github.com/a", "https://github.com.example.invalid/a",
            "https://user:password@github.com/a", "https://github.com:444/a")) {
            assertFalse(url, UpdateHttp.trustedGitHubUrl(url.toHttpUrl()))
        }
        assertTrue(UpdateHttp.trustedGitHubUrl("https://release-assets.githubusercontent.com/file?signature=value".toHttpUrl()))
    }
}