package com.yourapp.iptv.data.source.xtream

import com.yourapp.iptv.domain.model.PlaylistLoadException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.io.Reader
import java.util.concurrent.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class XtreamHttpClient(private val client: OkHttpClient) {
    suspend fun <T> read(url: HttpUrl, limit: Int, parse: (Reader, () -> Boolean) -> T): T =
        suspendCancellableCoroutine { pending ->
            val request = Request.Builder().url(url).header("Accept", "application/json").build()
            val call = client.newCall(request)
            pending.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (pending.isActive) pending.resumeWithException(
                        PlaylistLoadException("تعذر الاتصال بسيرفر Xtream. تحقق من العنوان والشبكة ثم أعد المحاولة.")
                    )
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        val result = response.use {
                            if (it.code == 401 || it.code == 403) {
                                throw PlaylistLoadException("رفض السيرفر الدخول. تحقق من بيانات Xtream وصلاحية الحساب.")
                            }
                            if (!it.isSuccessful) throw PlaylistLoadException("خطأ من سيرفر Xtream (HTTP ${it.code}).")
                            val body = it.body ?: throw PlaylistLoadException("أعاد سيرفر Xtream استجابة فارغة.")
                            if (body.contentLength() > limit) throw tooLarge()
                            val active = { pending.isActive }
                            parse(bounded(body.charStream(), limit, active), active)
                        }
                        if (pending.isActive) pending.resume(result)
                    } catch (error: Exception) {
                        if (pending.isActive) pending.resumeWithException(
                            generateSequence<Throwable>(error) { it.cause }.take(8)
                                .filterIsInstance<PlaylistLoadException>().firstOrNull()
                                ?: PlaylistLoadException("تعذر قراءة بيانات Xtream. تأكد أن العنوان لسيرفر Xtream Codes.")
                        )
                    }
                }
            })
        }

    private fun bounded(source: Reader, limit: Int, active: () -> Boolean) = object : Reader() {
        private var count = 0L
        override fun read(buffer: CharArray, offset: Int, length: Int): Int {
            if (length == 0) return 0
            if (!active()) throw CancellationException("Xtream request cancelled")
            val allowed = minOf(length.toLong(), limit - count + 1).toInt()
            if (allowed <= 0) throw tooLarge()
            val read = source.read(buffer, offset, allowed)
            if (read > 0) count += read
            if (count > limit) throw tooLarge()
            return read
        }
        override fun close() = source.close()
    }

    private fun tooLarge() = PlaylistLoadException("حجم استجابة Xtream يتجاوز الحد المسموح.")
}