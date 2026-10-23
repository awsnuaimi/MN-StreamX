package com.yourapp.iptv.data.source.xtream

import com.yourapp.iptv.domain.model.PlaylistLoadException
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.security.MessageDigest

internal class XtreamUrls(
    server: String,
    private val username: String,
    private val password: String
) {
    private val base: HttpUrl = normalize(server)

    init {
        if (username.isBlank() || password.isBlank()) {
            throw PlaylistLoadException("أكمل اسم المستخدم وكلمة مرور Xtream.")
        }
    }

    fun api(action: String? = null): HttpUrl = base.newBuilder()
        .addPathSegment("player_api.php")
        .addQueryParameter("username", username)
        .addQueryParameter("password", password)
        .apply { if (action != null) addQueryParameter("action", action) }
        .build()

    fun vodApi(action: String, id: String): HttpUrl {
        require(id.length in 1..18 && id.all { it in '0'..'9' })
        val key = when (action) {
            "get_vod_info" -> "vod_id"
            "get_series_info" -> "series_id"
            else -> throw IllegalArgumentException("Unsupported media detail operation")
        }
        return api(action).newBuilder().addQueryParameter(key, id).build()
    }

    fun vodStream(kind: com.yourapp.iptv.domain.model.VodKind, id: String, extension: String): String {
        require(id.length in 1..18 && id.all { it in '0'..'9' })
        val safe = XtreamVodFormat.safeExtension(extension)
        return base.newBuilder()
            .addPathSegment(if (kind == com.yourapp.iptv.domain.model.VodKind.MOVIE) "movie" else "series")
            .addPathSegment(username)
            .addPathSegment(password)
            .addPathSegment("$id.$safe")
            .build().toString()
    }

    fun stream(id: String, extension: String): String = base.newBuilder()
        .addPathSegment("live")
        .addPathSegment(username)
        .addPathSegment(password)
        .addPathSegment("$id.$extension")
        .build().toString()

    fun channelId(id: String): String {
        val identity = "$base\u0000$username\u0000$id"
        val hash = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(Charsets.UTF_8))
        val digits = "0123456789abcdef"
        return buildString(71) {
            append("xtream_")
            hash.forEach {
                val value = it.toInt() and 0xff
                append(digits[value ushr 4])
                append(digits[value and 15])
            }
        }
    }

    fun icon(value: String?): String? = value?.trim()?.takeIf { it.isNotEmpty() }
        ?.let { base.resolve(it) }?.takeIf { it.username.isEmpty() && it.password.isEmpty() }?.toString()

    companion object {
        private val endpoints = setOf("player_api.php", "panel_api.php", "get.php", "xmltv.php")

        fun isValid(server: String): Boolean = runCatching { normalize(server) }.isSuccess

        private fun normalize(server: String): HttpUrl {
            val url = server.trim().toHttpUrlOrNull()
                ?: throw PlaylistLoadException("عنوان سيرفر Xtream غير صالح. أدخل http:// أو https:// مع المنفذ إن وجد.")
            if (url.username.isNotEmpty() || url.password.isNotEmpty()) {
                throw PlaylistLoadException("أدخل السيرفر وحده، واسم المستخدم وكلمة المرور في الحقول المخصصة.")
            }
            var path = url.encodedPath.trimEnd('/')
            if (url.pathSegments.lastOrNull { it.isNotEmpty() } in endpoints) {
                path = path.substringBeforeLast('/', "")
            }
            return url.newBuilder().encodedPath("$path/").query(null).fragment(null).build()
        }
    }
}