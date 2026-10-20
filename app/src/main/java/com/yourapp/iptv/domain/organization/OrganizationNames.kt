package com.yourapp.iptv.domain.organization

import com.yourapp.iptv.domain.model.AppSettings
import com.yourapp.iptv.domain.model.PlaylistSource
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale

object OrganizationNames {
    private val marks = Regex("\\p{M}+")
    private val words = Regex("[\\p{L}\\p{N}]+")
    private val qualities = setOf("uhd", "fhd", "hd", "sd", "4k", "8k", "vip", "1080p", "720p", "2160p")
    fun normalize(value: String): String = words.findAll(marks.replace(
        Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFKD), "")
        .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا').replace('ى', 'ي').replace('ة', 'ه'))
        .joinToString(" ") { it.value }
    fun canonicalGroup(value: String?): String = value?.let(::normalize)?.split(' ')
        ?.filterIndexed { index, word -> index == 0 || word !in qualities }?.joinToString(" ")?.takeIf { it.isNotBlank() } ?: "~ungrouped"
    fun groupKey(value: String?) = digest(canonicalGroup(value)).take(32)
    fun digest(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
    fun sourceKey(value: AppSettings): String = digest(when (value.source) {
        PlaylistSource.M3U -> "m3u\u0000${value.m3uUrl.trim()}"
        PlaylistSource.XTREAM -> "xtream\u0000${value.xtreamServer.trim().trimEnd('/')}\u0000${value.xtreamUsername.trim()}"
    })
}