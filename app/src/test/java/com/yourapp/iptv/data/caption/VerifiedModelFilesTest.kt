package com.yourapp.iptv.data.caption

import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.*
import okio.Buffer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class VerifiedModelFilesTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun spec(server: MockWebServer, bytes: ByteArray) = ModelBundle("unit", server.url("/").toString().trimEnd('/'),
        listOf(ModelAsset("model.bin", bytes.size.toLong(), MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 255) })))
    @Test fun verifiesCachesAndReplacesCorruptModelsAtomically() = runTest {
        MockWebServer().use { server ->
            val body = "synthetic model bytes".toByteArray(); val bundle = spec(server, body)
            val models = VerifiedModelFiles(temporary.root, OkHttpClient())
            server.enqueue(MockResponse().setBody(Buffer().write(body)))
            val folder = models.prepare(bundle) { }; val file = folder.resolve("model.bin")
            assertArrayEquals(body, file.readBytes())
            models.prepare(bundle) { }; assertEquals(1, server.requestCount)
            file.writeText("corrupt")
            server.enqueue(MockResponse().setBody(Buffer().write(body)))
            models.prepare(bundle) { }; assertArrayEquals(body, file.readBytes())
            assertEquals(2, server.requestCount)
            assertFalse(temporary.root.walk().any { it.name.endsWith(".part") })
        }
    }
    @Test fun wrongChecksumsAndOversizedDownloadsAreNeverPublished() = runTest {
        MockWebServer().use { server ->
            val bytes = "expected".toByteArray(); val bundle = spec(server, bytes)
            val models = VerifiedModelFiles(temporary.root, OkHttpClient())
            for (body in listOf("mismatch", "far too many bytes for this model")) {
                server.enqueue(MockResponse().setBody(body))
                try { models.prepare(bundle) { }; fail("Unverified model accepted") } catch (_: IOException) { }
                assertFalse(temporary.root.resolve("unit/model.bin").exists())
                assertFalse(temporary.root.walk().any { it.name.endsWith(".part") })
            }
        }
    }
    @Test fun cancellationClosesTheSocketAndRemovesPartialFiles() = runTest {
        MockWebServer().use { server ->
            val body = ByteArray(100000) { 3 }; val bundle = spec(server, body)
            server.enqueue(MockResponse().setBody(Buffer().write(body)).throttleBody(1024, 1, TimeUnit.SECONDS))
            val client = OkHttpClient.Builder().readTimeout(5, TimeUnit.SECONDS).build()
            val model = VerifiedModelFiles(temporary.root, client)
            val pending = launch(Dispatchers.IO) { model.prepare(bundle) { } }
            withContext(Dispatchers.IO) { assertNotNull(server.takeRequest(5, TimeUnit.SECONDS)) }
            pending.cancelAndJoin()
            assertFalse(temporary.root.walk().any { it.name.endsWith(".part") })
            assertFalse(temporary.root.resolve("unit/model.bin").exists())
        }
    }
}