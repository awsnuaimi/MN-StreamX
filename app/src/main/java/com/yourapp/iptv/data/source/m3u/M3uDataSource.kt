package com.yourapp.iptv.data.source.m3u

import com.yourapp.iptv.data.model.ChannelDto
import com.yourapp.iptv.data.network.HttpClient
import com.yourapp.iptv.domain.model.PlaylistLoadException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class M3uDataSource(private val client: OkHttpClient = HttpClient.create()) {
    suspend fun fetchChannels(url: String): List<ChannelDto> = suspendCancellableCoroutine { pending ->
        val request = try {
            Request.Builder().url(url).build()
        } catch (_: IllegalArgumentException) {
            pending.resumeWithException(PlaylistLoadException("رابط قائمة القنوات غير صالح."))
            return@suspendCancellableCoroutine
        }
        val call = client.newCall(request)
        pending.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (pending.isActive) {
                    pending.resumeWithException(
                        PlaylistLoadException("تعذر الاتصال بمصدر القنوات. تحقق من الشبكة والرابط.")
                    )
                }
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val channels = response.use {
                        if (!it.isSuccessful) {
                            throw PlaylistLoadException("تعذر تحميل قائمة القنوات (HTTP ${it.code}).")
                        }
                        val body = it.body ?: throw PlaylistLoadException("مصدر القنوات أعاد استجابة فارغة.")
                        if (body.contentLength() > 20 * 1024 * 1024) {
                            throw PlaylistLoadException("حجم قائمة القنوات يتجاوز الحد المسموح (20 ميغابايت).")
                        }
                        M3uParser.parse(body.charStream(), it.request.url.toString()) { pending.isActive }
                    }
                    if (pending.isActive) pending.resume(channels)
                } catch (error: Exception) {
                    if (pending.isActive) {
                        pending.resumeWithException(
                            error as? PlaylistLoadException
                                ?: PlaylistLoadException("تعذر قراءة قائمة القنوات. تحقق من صيغة الملف.")
                        )
                    }
                }
            }
        })
    }
}