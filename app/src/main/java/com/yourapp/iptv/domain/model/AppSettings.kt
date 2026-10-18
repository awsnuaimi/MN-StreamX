package com.yourapp.iptv.domain.model

data class AppSettings(
    val m3uUrl: String = "",
    val xtreamServer: String = "",
    val xtreamUsername: String = "",
    val xtreamPassword: String = "",
    val epgUrl: String = "",
    val source: PlaylistSource = PlaylistSource.M3U
) {
    fun hasSelectedSource(): Boolean = when (source) {
        PlaylistSource.M3U -> m3uUrl.isNotBlank()
        PlaylistSource.XTREAM -> xtreamServer.isNotBlank() &&
            xtreamUsername.isNotBlank() && xtreamPassword.isNotBlank()
    }

    override fun toString(): String = "AppSettings(source=$source, credentials=redacted)"
}