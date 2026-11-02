package com.yourapp.iptv.data.source.xtream

import com.yourapp.iptv.data.network.HttpResponseFailure
import com.yourapp.iptv.data.network.ResponseLimitFailure
import com.yourapp.iptv.domain.model.PlaylistLoadException
import java.io.IOException
import java.io.Reader
import java.util.concurrent.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Keeps OkHttp callbacks free while large JSON responses are parsed on Dispatchers.IO. */
internal class XtreamHttpClient(private val client: OkHttpClient) {
    suspend fun <T> read(url: HttpUrl, limit: Int, parse: (Reader, () -> Boolean) -> T): T = coroutineScope {
        require(limit > 0) { "Xtream response limit must be positive" }
        suspendCancellableCoroutine { pending ->
            val request = Request.Builder().url(url).header("Accept", "application/json").build()
            val call = client.newCall(request)
            pending.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (pending.isActive) pending.resumeWithException(
                        PlaylistLoadException("تعذر الاتصال بسيرفر Xtream. تحقق من العنوان والشبكة ثم أعد المحاولة.", e)
                    )
                }

                override fun onResponse(call: Call, response: Response) {
                    if (!pending.isActive) {
                        response.close()
                        return
                    }
                    // The child belongs to the caller's scope, not a detached global scope.
                    val parser = launch(Dispatchers.IO) {
                        try {
                            val result = response.use {
                                if (it.code == 401 || it.code == 403) {
                                    throw PlaylistLoadException(
                                        "رفض السيرفر الدخول. تحقق من بيانات Xtream وصلاحية الحساب.",
                                        HttpResponseFailure(it.code)
                                    )
                                }
                                if (!it.isSuccessful) {
                                    throw PlaylistLoadException(
                                        "خطأ من سيرفر Xtream (HTTP ${it.code}).", HttpResponseFailure(it.code)
                                    )
                                }
                                val body = it.body
                                    ?: throw PlaylistLoadException("أعاد سيرفر Xtream استجابة فارغة.")
                                if (body.contentLength() > limit) throw tooLarge()
                                val active = { pending.isActive && isActive }
                                parse(byteLimitedBody(body, limit, active).charStream(), active)
                            }
                            if (pending.isActive) pending.resume(result)
                        } catch (error: Exception) {
                            if (pending.isActive) pending.resumeWithException(normalize(error))
                        }
                    }
                    // Also closes the response when the child is cancelled before it starts.
                    parser.invokeOnCompletion { response.close() }
                }
            })
        }
    }

    private fun byteLimitedBody(body: ResponseBody, limit: Int, active: () -> Boolean): ResponseBody {
        var bytesRead = 0L
        val bounded = object : ForwardingSource(body.source()) {
            override fun read(sink: Buffer, byteCount: Long): Long {
                if (byteCount == 0L) return 0L
                if (!active()) throw CancellationException("Xtream request cancelled")
                // Allow one extra byte to detect oversized unknown-length/chunked bodies.
                val allowed = minOf(byteCount, limit.toLong() - bytesRead + 1)
                if (allowed <= 0) throw tooLarge()
                val count = super.read(sink, allowed)
                if (count > 0) bytesRead += count
                if (bytesRead > limit) throw tooLarge()
                return count
            }
        }.buffer()
        // ResponseBody.charStream() retains OkHttp's charset and BOM handling.
        return object : ResponseBody() {
            override fun contentType() = body.contentType()
            override fun contentLength() = body.contentLength()
            override fun source(): BufferedSource = bounded
        }
    }

    private fun normalize(error: Exception): PlaylistLoadException =
        generateSequence<Throwable>(error) { it.cause }.take(8)
            .filterIsInstance<PlaylistLoadException>().firstOrNull()
            ?: PlaylistLoadException(
                "تعذر قراءة بيانات Xtream. تأكد أن العنوان لسيرفر Xtream Codes.", error
            )

    private fun tooLarge() = PlaylistLoadException(
        "حجم استجابة Xtream يتجاوز الحد المسموح.", ResponseLimitFailure()
    )
}