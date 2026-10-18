package com.yourapp.iptv.data.source.m3u

import com.yourapp.iptv.data.model.ChannelDto
import com.yourapp.iptv.domain.model.PlaylistLoadException
import java.io.Reader
import java.net.URI
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.CancellationException

object M3uParser {
    private val attributes = Regex("""([\w-]+)\s*=\s*("[^"]*"|'[^']*'|[^\s,]+)""")
    private const val MAX_CHARACTERS = 20 * 1024 * 1024
    private const val MAX_CHANNELS = 30_000

    fun parse(content: String): List<ChannelDto> = parse(content.reader())

    fun parse(
        reader: Reader,
        playlistUrl: String? = null,
        isActive: () -> Boolean = { true }
    ): List<ChannelDto> {
        val channels = LinkedHashMap<String, ChannelDto>()
        var metadata: Metadata? = null
        boundedReader(reader, isActive).buffered().lineSequence().forEach { raw ->
            if (!isActive()) throw CancellationException("Playlist parsing cancelled")
            val line = raw.trim().removePrefix("\uFEFF")
            when {
                line.isEmpty() -> Unit
                line.startsWith("#EXT-X-", ignoreCase = true) -> {
                    throw PlaylistLoadException("هذا رابط بث HLS؛ أدخل رابط قائمة قنوات M3U.")
                }
                line.startsWith("#EXTINF:", ignoreCase = true) -> metadata = readMetadata(line)
                line.startsWith("#EXTGRP:", ignoreCase = true) -> {
                    metadata = metadata?.copy(group = line.substringAfter(':').trim())
                }
                line.startsWith('#') -> Unit
                else -> {
                    val current = metadata
                    metadata = null
                    val url = resolve(line, playlistUrl)
                    if (current != null && url != null) {
                        val id = stableId(url)
                        channels[id] = ChannelDto(
                            id = id,
                            name = current.name.ifBlank { "قناة ${channels.size + 1}" },
                            url = url,
                            logo = current.logo?.let { resolve(it, playlistUrl) },
                            group = current.group?.takeIf { it.isNotBlank() }
                        )
                        if (channels.size > MAX_CHANNELS) {
                            throw PlaylistLoadException("القائمة تتجاوز الحد المسموح (30000 قناة).")
                        }
                    }
                }
            }
        }
        if (channels.isEmpty()) {
            throw PlaylistLoadException("لم يُعثر على قنوات صالحة. تأكد من رابط قائمة M3U.")
        }
        return channels.values.toList()
    }

    private fun boundedReader(source: Reader, isActive: () -> Boolean): Reader = object : Reader() {
        private var count = 0L
        override fun read(buffer: CharArray, offset: Int, length: Int): Int {
            if (length == 0) return 0
            if (!isActive()) throw CancellationException("Playlist parsing cancelled")
            val allowed = minOf(length.toLong(), MAX_CHARACTERS - count + 1).toInt()
            if (allowed <= 0) throw oversized()
            val read = source.read(buffer, offset, allowed)
            if (read > 0) count += read
            if (count > MAX_CHARACTERS) throw oversized()
            return read
        }
        override fun close() = source.close()
    }

    private fun oversized() = PlaylistLoadException("حجم قائمة القنوات يتجاوز الحد المسموح (20 ميغابايت).")

    private fun readMetadata(line: String): Metadata {
        var quote: Char? = null
        val separator = line.indexOfFirst { char ->
            if (char == '\'' || char == '"') {
                quote = if (quote == char) null else quote ?: char
                false
            } else {
                char == ',' && quote == null
            }
        }
        val header = if (separator >= 0) line.substring(0, separator) else line
        val values = attributes.findAll(header).associate {
            it.groupValues[1].lowercase(Locale.ROOT) to
                it.groupValues[2].removeSurrounding("\"").removeSurrounding("'")
        }
        return Metadata(
            name = if (separator >= 0) line.substring(separator + 1).trim()
                .ifBlank { values["tvg-name"].orEmpty() } else values["tvg-name"].orEmpty(),
            logo = values["tvg-logo"],
            group = values["group-title"]
        )
    }

    private fun resolve(value: String, base: String?): String? = try {
        val uri = URI(value.replace(" ", "%20"))
        val resolved = if (uri.isAbsolute) uri else base?.let { URI(it).resolve(uri) }
        resolved?.takeIf {
            it.scheme?.lowercase(Locale.ROOT) in setOf("http", "https") && !it.host.isNullOrBlank()
        }?.toASCIIString()
    } catch (_: Exception) {
        null
    }

    private fun stableId(url: String): String {
        val digits = "0123456789abcdef"
        val digest = MessageDigest.getInstance("SHA-256").digest(url.toByteArray(Charsets.UTF_8))
        return buildString(68) {
            append("m3u_")
            digest.forEach { byte ->
                val value = byte.toInt() and 0xff
                append(digits[value ushr 4])
                append(digits[value and 15])
            }
        }
    }

    private data class Metadata(val name: String, val logo: String?, val group: String?)
}