package com.yourapp.iptv.data.source.xtream

import com.yourapp.iptv.domain.model.AppSettings
import com.yourapp.iptv.domain.model.Channel
import com.yourapp.iptv.domain.model.PlaylistSource
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.security.MessageDigest

internal class XtreamEpgRequest private constructor(val url: HttpUrl, val cacheKey: String) {
    override fun toString(): String = "XtreamEpgRequest(credentials=redacted)"

    companion object {
        fun create(channel: Channel, settings: AppSettings): XtreamEpgRequest? {
            if (settings.source != PlaylistSource.XTREAM || !settings.hasSelectedSource()) return null
            val stream = channel.streamUrl.toHttpUrlOrNull() ?: return null
            val filename = stream.pathSegments.lastOrNull().orEmpty()
            val extension = filename.substringAfterLast('.', "")
            val id = filename.substringBeforeLast('.', "").toLongOrNull()
                ?.takeIf { it > 0 }?.toString() ?: return null
            if (extension !in setOf("m3u8", "ts")) return null
            val urls = XtreamUrls(settings.xtreamServer, settings.xtreamUsername, settings.xtreamPassword)
            if (channel.id != urls.channelId(id) || stream.toString() != urls.stream(id, extension)) return null
            val url = urls.api("get_short_epg").newBuilder()
                .addQueryParameter("stream_id", id).addQueryParameter("limit", "6").build()
            val digest = MessageDigest.getInstance("SHA-256").digest(url.toString().toByteArray(Charsets.UTF_8))
            val digits = "0123456789abcdef"
            val key = buildString(64) {
                digest.forEach {
                    val value = it.toInt() and 0xff
                    append(digits[value ushr 4])
                    append(digits[value and 15])
                }
            }
            return XtreamEpgRequest(url, key)
        }
    }
}