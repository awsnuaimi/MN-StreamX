package com.yourapp.iptv.data.source.xtream

import com.yourapp.iptv.domain.model.AppSettings
import com.yourapp.iptv.domain.model.PlaylistSource
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** A provider's get.php playlist retains its Xtream credentials; generic M3U URLs do not. */
internal object XtreamVodAccount {
    fun resolve(settings: AppSettings): AppSettings? {
        if (settings.source == PlaylistSource.XTREAM) {
            return settings.takeIf { it.hasSelectedSource() && XtreamUrls.isValid(it.xtreamServer) }
        }
        val url = settings.m3uUrl.trim().toHttpUrlOrNull() ?: return null
        if (url.username.isNotEmpty() || url.password.isNotEmpty() ||
            url.pathSegments.lastOrNull() != "get.php") return null
        val users = url.queryParameterValues("username")
        val passwords = url.queryParameterValues("password")
        if (users.size != 1 || passwords.size != 1) return null
        val user = users.single()?.takeIf { it.isNotBlank() } ?: return null
        val password = passwords.single()?.takeIf { it.isNotBlank() } ?: return null
        val server = url.newBuilder().query(null).fragment(null)
            .removePathSegment(url.pathSegments.lastIndex).addPathSegment("").build()
        return settings.copy(source = PlaylistSource.XTREAM, xtreamServer = server.toString(),
            xtreamUsername = user, xtreamPassword = password)
    }
}