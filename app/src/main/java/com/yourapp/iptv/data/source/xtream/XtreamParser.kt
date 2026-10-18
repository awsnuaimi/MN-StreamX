package com.yourapp.iptv.data.source.xtream

import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.yourapp.iptv.data.model.XtreamChannelDto
import com.yourapp.iptv.domain.model.PlaylistLoadException
import java.io.Reader
import java.util.concurrent.CancellationException

internal object XtreamParser {
    fun categories(reader: Reader, active: () -> Boolean): Map<String, String> =
        readObjects(reader, 10_000, active) { values ->
            val id = values["category_id"]?.takeIf { it.isNotBlank() }
            val name = values["category_name"]?.trim()?.takeIf { it.isNotEmpty() }
            if (id != null && name != null) id to name else null
        }.toMap()

    fun channels(
        reader: Reader,
        urls: XtreamUrls,
        extension: String,
        categories: Map<String, String>,
        active: () -> Boolean
    ): List<XtreamChannelDto> {
        val rows = readObjects(reader, 30_000, active) { values ->
            val id = values["stream_id"]?.toLongOrNull()?.takeIf { it > 0 }?.toString()
            val type = values["stream_type"]
            if (id == null || (type != null && !type.equals("live", ignoreCase = true))) null
            else {
                val category = values["category_id"]?.takeIf { it.isNotBlank() }
                XtreamChannelDto(
                    streamId = id,
                    name = values["name"]?.trim()?.takeIf { it.isNotEmpty() } ?: "قناة $id",
                    streamUrl = urls.stream(id, extension),
                    icon = urls.icon(values["stream_icon"]),
                    categoryId = category,
                    categoryName = categories[category] ?: category?.let { "تصنيف $it" } ?: "غير مصنّف",
                    channelId = urls.channelId(id)
                )
            }
        }
        val unique = rows.distinctBy { it.channelId }
        if (unique.isEmpty()) throw PlaylistLoadException("لا توجد قنوات مباشرة صالحة في حساب Xtream.")
        return unique
    }

    private fun <T : Any> readObjects(
        reader: Reader,
        limit: Int,
        active: () -> Boolean,
        convert: (Map<String, String>) -> T?
    ): List<T> = JsonReader(reader).use { json ->
        if (json.peek() != JsonToken.BEGIN_ARRAY) throw PlaylistLoadException("صيغة بيانات Xtream غير متوقعة.")
        val result = ArrayList<T>()
        var count = 0
        json.beginArray()
        while (json.hasNext()) {
            if (!active()) throw CancellationException("Xtream parsing cancelled")
            if (++count > limit) throw PlaylistLoadException("عدد عناصر Xtream يتجاوز الحد المسموح ($limit).")
            if (json.peek() != JsonToken.BEGIN_OBJECT) {
                json.skipValue()
                continue
            }
            val values = HashMap<String, String>()
            json.beginObject()
            while (json.hasNext()) {
                val name = json.nextName()
                val token = json.peek()
                if (name in fields && (token == JsonToken.STRING || token == JsonToken.NUMBER)) {
                    values[name] = json.nextString()
                } else json.skipValue()
            }
            json.endObject()
            convert(values)?.let(result::add)
        }
        json.endArray()
        if (json.peek() != JsonToken.END_DOCUMENT) throw PlaylistLoadException("استجابة Xtream غير مكتملة أو غير صالحة.")
        result
    }

    private val fields = setOf("category_id", "category_name", "stream_id", "name", "stream_type", "stream_icon")
}