package com.yourapp.iptv.data.diagnostics

import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal data class DiagnosticResponse(val status: Int, val bytes: ByteArray, val truncated: Boolean)

internal class DiagnosticTransport(client: OkHttpClient) {
    private val client = client.newBuilder().followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build()
    suspend fun get(url: HttpUrl, limit: Int, prefix: Boolean = false): DiagnosticResponse = suspendCancellableCoroutine { waiting ->
        require(limit in 1024..256 * 1024)
        val request = Request.Builder().url(url).header("Accept",if (prefix) "*/*" else "application/json")
            .apply { if (prefix) header("Range","bytes=0-${limit - 1}") }.build()
        val call = client.newCall(request)
        waiting.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { if (waiting.isActive) waiting.resumeWithException(e) }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val result = response.use { r ->
                        // No redirects are followed and no response text is ever included in a result label.
                        if (!r.isSuccessful) DiagnosticResponse(r.code,byteArrayOf(),false)
                        else {
                            val bytes = ByteArrayOutputStream(minOf(limit,8192))
                            val buffer = ByteArray(8192)
                            var zeros = 0
                            r.body?.byteStream()?.use { input ->
                                while (bytes.size() <= limit && waiting.isActive) {
                                    val count = input.read(buffer,0,minOf(buffer.size,limit + 1 - bytes.size()))
                                    if (count < 0) break
                                    if (count == 0) { if (++zeros > 16) throw IOException("Non-progressing response");continue }
                                    zeros = 0;bytes.write(buffer,0,count)
                                }
                            }
                            val value = bytes.toByteArray()
                            DiagnosticResponse(r.code,value,value.size > limit)
                        }
                    }
                    if (waiting.isActive) waiting.resume(result) else result.bytes.fill(0)
                } catch (error: Exception) { if (waiting.isActive) waiting.resumeWithException(error) }
            }
        })
    }
}