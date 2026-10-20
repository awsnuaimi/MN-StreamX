package com.yourapp.iptv.domain.model

import java.security.MessageDigest

/** Cache provenance only. Never use this key for organization rules or channel IDs. */
object PlaylistCacheKey {
    fun of(settings: AppSettings): String {
        val fields = when (settings.source) {
            PlaylistSource.M3U -> listOf("m3u", settings.m3uUrl.trim())
            PlaylistSource.XTREAM -> listOf("xtream", settings.xtreamServer.trim(),
                settings.xtreamUsername, settings.xtreamPassword)
        }
        // Length prefixes avoid collisions even when a credential contains a delimiter.
        val digest = MessageDigest.getInstance("SHA-256")
        fields.forEach { field ->
            val bytes = field.toByteArray(Charsets.UTF_8)
            digest.update("${bytes.size}:".toByteArray(Charsets.UTF_8))
            digest.update(bytes)
        }
        val hex = "0123456789abcdef"
        return buildString(64) {
            digest.digest().forEach { byte ->
                val value = byte.toInt() and 255
                append(hex[value ushr 4]); append(hex[value and 15])
            }
        }
    }
}