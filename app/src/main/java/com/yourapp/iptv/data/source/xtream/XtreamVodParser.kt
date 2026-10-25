package com.yourapp.iptv.data.source.xtream

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.yourapp.iptv.domain.model.*
import java.io.Reader
import java.util.concurrent.CancellationException

internal object XtreamVodParser {
    fun categories(reader: Reader, active: () -> Boolean): List<VodCategory> =
        readArray(reader, 10_000, active) { obj ->
            val id = obj.string("category_id")?.takeIf { it.isNotBlank() } ?: return@readArray null
            VodCategory(id, obj.string("category_name")?.takeIf { it.isNotBlank() } ?: id)
        }.distinctBy { it.id }

    fun items(reader: Reader, kind: VodKind, active: () -> Boolean): List<VodItem> =
        readArray(reader, 60_000, active) { obj ->
            val id = obj.string(if (kind == VodKind.MOVIE) "stream_id" else "series_id")
                ?.takeIf { it.isValidXtreamId() } ?: return@readArray null
            val poster = obj.string(if (kind == VodKind.MOVIE) "stream_icon" else "cover")
            val categories = (listOfNotNull(obj.string("category_id")) +
                obj.get("category_ids")?.takeIf { it.isJsonArray }?.asJsonArray?.toList().orEmpty()
                    .filter { it.isJsonPrimitive }.map { it.asString.trim() })
                .filter { it.isNotBlank() && it != "null" }.distinct()
            VodItem(id = id, name = obj.string("name")?.trim()?.takeIf { it.isNotBlank() } ?: id,
                categoryId = categories.firstOrNull(), categoryIds = categories, poster = poster,
                extension = XtreamVodFormat.safeExtension(obj.string("container_extension")),
                plot = obj.string("plot"), rating = obj.string("rating"),
                year = obj.string("releaseDate") ?: obj.string("year"))
        }.distinctBy { it.id }

    fun details(reader: Reader, kind: VodKind, original: VodItem): VodDetails {
        val root = JsonParser.parseReader(reader).takeIf { it.isJsonObject }?.asJsonObject
            ?: return VodDetails(original)
        val info = root.obj("info")
        val plot = info?.string("plot") ?: info?.string("description") ?: original.plot
        val poster = info?.string("cover_big") ?: info?.string("cover") ?: original.poster
        val rating = info?.string("rating") ?: original.rating
        val year = info?.string("releasedate") ?: info?.string("year") ?: original.year
        val movieData = root.obj("movie_data")
        val item = original.copy(poster = poster, plot = plot, rating = rating, year = year,
            extension = if (kind == VodKind.MOVIE)
                XtreamVodFormat.safeExtension(movieData?.string("container_extension") ?: original.extension)
            else original.extension)
        if (kind == VodKind.MOVIE) return VodDetails(item)

        val episodes = root.obj("episodes") ?: return VodDetails(item)
        val result = ArrayList<VodSeason>()
        episodes.entrySet().take(200).forEach season@{ (seasonKey, episodeList) ->
            val season = seasonKey.toIntOrNull()?.takeIf { it in 0..999 } ?: return@season
            if (!episodeList.isJsonArray) return@season
            val entries = ArrayList<VodEpisode>()
            episodeList.asJsonArray.take(2000).forEach episode@{ raw ->
                if (!raw.isJsonObject) return@episode
                val value = raw.asJsonObject
                val id = value.string("id")?.takeIf { it.isValidXtreamId() } ?: return@episode
                val num = value.string("episode_num")?.toIntOrNull()?.coerceIn(0, 9999) ?: entries.size + 1
                val extension = XtreamVodFormat.safeExtension(value.string("container_extension"))
                entries += VodEpisode(id, value.string("title")?.takeIf { it.isNotBlank() } ?: "Episode $num",
                    season, num, extension, value.obj("info")?.string("plot"))
            }
            if (entries.isNotEmpty()) result += VodSeason(season, "Season $season", entries.sortedBy { it.number })
        }
        return VodDetails(item, result.sortedBy { it.number })
    }

    private fun <T : Any> readArray(reader: Reader, limit: Int, active: () -> Boolean,
        parse: (JsonObject) -> T?): List<T> = JsonReader(reader).use { json ->
        if (json.peek() != JsonToken.BEGIN_ARRAY) {
            throw PlaylistLoadException("أعاد الخادم بيانات غير صالحة لكتالوج الأفلام أو المسلسلات.")
        }
        val result = ArrayList<T>()
        var count = 0
        json.beginArray()
        while (json.hasNext()) {
            if (!active()) throw CancellationException("VOD parsing cancelled")
            if (++count > limit) throw IllegalArgumentException("VOD catalog exceeds safe item limit")
            val element = JsonParser.parseReader(json)
            if (element.isJsonObject) parse(element.asJsonObject)?.let(result::add)
        }
        json.endArray()
        require(json.peek() == JsonToken.END_DOCUMENT) { "Unexpected data after VOD catalog" }
        result
    }

    private fun JsonObject.string(key: String): String? = get(key)?.let { value ->
        if (value.isJsonPrimitive) value.asString.trim().takeIf { it.isNotBlank() } else null
    }
    private fun JsonObject.obj(key: String): JsonObject? = get(key)?.takeIf { it.isJsonObject }?.asJsonObject
    private fun String.isValidXtreamId() = length in 1..18 && all { it in '0'..'9' }
}

internal object XtreamVodFormat {
    private val extensions = setOf("mp4", "mkv", "avi", "mov", "ts", "m3u8", "webm")
    fun safeExtension(value: String?): String = value?.lowercase(java.util.Locale.ROOT)
        ?.removePrefix(".")?.takeIf { it in extensions } ?: "mp4"
}