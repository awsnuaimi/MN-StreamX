package com.yourapp.iptv.data.datastore

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.yourapp.iptv.domain.model.VodKind
import com.yourapp.iptv.domain.model.VodProgress
import com.yourapp.iptv.domain.model.VodProgressEntry

internal object VodProgressCodec {
    private const val MAX_JSON = 40 * 1024

    fun encode(entries: List<VodProgressEntry>): String = JsonObject().apply {
        require(entries.size <= VodProgress.LIMIT && entries.all(VodProgress::resumable))
        require(entries.map { it.kind to it.id }.distinct().size == entries.size)
        addProperty("version", 1)
        add("entries", JsonArray().apply { entries.forEach { entry ->
            add(JsonObject().apply {
                addProperty("kind", entry.kind.name)
                addProperty("id", entry.id)
                addProperty("title", entry.title)
                addProperty("ext", entry.extension)
                addProperty("pos", entry.positionMs)
                addProperty("dur", entry.durationMs)
                addProperty("at", entry.updatedAtMs)
                entry.seriesId?.let { addProperty("series", it) }
            })
        } })
    }.toString().also { require(it.length <= MAX_JSON) }

    fun decode(text: String?): List<VodProgressEntry> {
        if (text.isNullOrBlank() || text.length > MAX_JSON) return emptyList()
        return try {
            val root = JsonParser.parseString(text).asJsonObject
            require(root.get("version").asInt == 1)
            val array = root.getAsJsonArray("entries")
            require(array.size() <= VodProgress.LIMIT)
            val rows = array.map { element ->
                val obj = element.asJsonObject
                val kind = VodKind.valueOf(obj.get("kind").asString)
                val entry = VodProgressEntry(
                    kind, obj.get("id").asString, obj.get("title").asString,
                    obj.get("ext").asString, obj.get("pos").asLong,
                    obj.get("dur").asLong, obj.get("at").asLong,
                    obj.get("series")?.takeUnless { it.isJsonNull }?.let {
                        require(it.isJsonPrimitive && it.asJsonPrimitive.isString); it.asString
                    }
                )
                require(VodProgress.resumable(entry))
                entry
            }
            require(rows.map { it.kind to it.id }.distinct().size == rows.size)
            rows.sortedByDescending { it.updatedAtMs }
        } catch (_: Exception) { emptyList() }
    }
}