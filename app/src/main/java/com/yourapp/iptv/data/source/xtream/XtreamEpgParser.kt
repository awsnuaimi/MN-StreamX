package com.yourapp.iptv.data.source.xtream

import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.yourapp.iptv.domain.model.PlaylistLoadException
import com.yourapp.iptv.domain.model.Program
import java.io.Reader
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.time.OffsetDateTime
import java.util.Base64
import java.util.concurrent.CancellationException

internal object XtreamEpgParser {
    private val fields = setOf("title", "description", "start_timestamp", "stop_timestamp", "start", "end", "stop")

    fun parse(reader: Reader, active: () -> Boolean = { true }): List<Program> = JsonReader(reader).use { json ->
        val programs = ArrayList<Program>()
        var found = false
        when (json.peek()) {
            JsonToken.BEGIN_OBJECT -> {
                json.beginObject()
                while (json.hasNext()) {
                    checkActive(active)
                    if (json.nextName() == "epg_listings") {
                        if (found) throw invalid()
                        found = true
                        if (json.peek() == JsonToken.NULL) json.nextNull()
                        else readListings(json, programs, active)
                    } else json.skipValue()
                }
                json.endObject()
            }
            JsonToken.BEGIN_ARRAY -> {
                json.beginArray()
                if (json.hasNext()) throw invalid()
                json.endArray()
                found = true
            }
            else -> throw invalid()
        }
        if (!found || json.peek() != JsonToken.END_DOCUMENT) throw invalid()
        programs.sortedBy { it.startTime }.distinctBy { it.startTime to it.endTime }
    }

    private fun readListings(json: JsonReader, programs: MutableList<Program>, active: () -> Boolean) {
        if (json.peek() != JsonToken.BEGIN_ARRAY) throw invalid()
        json.beginArray()
        var count = 0
        while (json.hasNext()) {
            checkActive(active)
            if (++count > 32) throw invalid()
            if (json.peek() != JsonToken.BEGIN_OBJECT) { json.skipValue(); continue }
            val values = HashMap<String, String>()
            json.beginObject()
            while (json.hasNext()) {
                checkActive(active)
                val name = json.nextName()
                if (name in fields && json.peek() in setOf(JsonToken.STRING, JsonToken.NUMBER)) {
                    values[name] = json.nextString()
                } else json.skipValue()
            }
            json.endObject()
            val title = text(values["title"], 512)
            val start = timestamp(values["start_timestamp"]) ?: timestamp(values["start"]) ?: continue
            val end = timestamp(values["stop_timestamp"]) ?: timestamp(values["end"])
                ?: timestamp(values["stop"]) ?: continue
            if (title.isEmpty() || end <= start || end - start > 7 * 24 * 60 * 60 * 1000L) continue
            programs += Program(title, start, end, text(values["description"], 2048).ifBlank { null })
        }
        json.endArray()
    }

    private fun timestamp(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        raw.toLongOrNull()?.let {
            return when (it) {
                in 946684800L..4102444800L -> it * 1000L
                in 946684800000L..4102444800000L -> it
                else -> null
            }
        }
        // A timestamp with an explicit offset is safe; server-local dates are ambiguous.
        return runCatching { OffsetDateTime.parse(raw).toInstant().toEpochMilli() }.getOrNull()
            ?.takeIf { it in 946684800000L..4102444800000L }
    }

    private fun text(raw: String?, limit: Int): String {
        if (raw.isNullOrBlank()) return ""
        val original = raw.take(16_384).trim()
        val compact = original.filterNot(Char::isWhitespace)
        val decoded = if (compact.length >= 4 && compact.matches(Regex("[A-Za-z0-9+/]*={0,2}"))) {
            runCatching {
                val bytes = Base64.getDecoder().decode(compact)
                Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
                    .takeIf { value -> value.isNotBlank() && value.none { it.isISOControl() && !it.isWhitespace() } }
            }.getOrNull()
        } else null
        return (decoded ?: original).replace(Regex("[\\p{Cc}&&[^\\n\\t]]"), " ").trim().take(limit)
    }

    private fun checkActive(active: () -> Boolean) {
        if (!active()) throw CancellationException("EPG parsing cancelled")
    }

    private fun invalid() = PlaylistLoadException("تعذر قراءة جدول برامج Xtream.")
}