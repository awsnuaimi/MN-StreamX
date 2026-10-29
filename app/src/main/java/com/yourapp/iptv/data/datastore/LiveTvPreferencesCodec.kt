package com.yourapp.iptv.data.datastore

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.yourapp.iptv.data.backup.BackupJson
import com.yourapp.iptv.domain.model.LiveTvPreferences
import com.yourapp.iptv.domain.model.LiveTvSort
import java.io.IOException

internal object LiveTvPreferencesCodec {
    const val LIMIT = 1_048_576
    fun decode(text: String?): LiveTvPreferences {
        if (text == null) return LiveTvPreferences()
        try {
            require(text.length <= LIMIT)
            val root = BackupJson.read(text.toByteArray(Charsets.UTF_8), LIMIT)
            require(root.get("version").asInt == 1)
            fun strings(name: String, count: Int): Set<String> {
                val array = root.getAsJsonArray(name) ?: return emptySet()
                require(array.size() <= count)
                val rows = array.map { require(it.isJsonPrimitive && it.asJsonPrimitive.isString); it.asString }
                require(rows.distinct().size == rows.size)
                return rows.toSet()
            }
            val preferred = root.getAsJsonObject("preferred") ?: JsonObject()
            require(preferred.size() <= 10_000)
            val qualities = preferred.entrySet().associate { (key, value) ->
                require(value.isJsonPrimitive && value.asJsonPrimitive.isString)
                key to value.asString
            }
            return LiveTvPreferences(LiveTvSort.valueOf(root.get("sort").asString),
                strings("channels", 10_000), strings("groups", 2_048), strings("folders", 2_048),
                qualities).validate()
        } catch (error: Exception) { throw IOException("Cannot read personal channel preferences", error) }
    }
    fun encode(value: LiveTvPreferences): String {
        value.validate()
        return JsonObject().apply {
            addProperty("version", 1); addProperty("sort", value.sort.name)
            fun rows(name: String, values: Set<String>) { add(name, JsonArray().apply { values.sorted().forEach(::add) }) }
            rows("channels", value.hiddenChannels); rows("groups", value.hiddenGroups)
            rows("folders", value.hiddenFolders)
            add("preferred", JsonObject().apply { value.preferredQualities.toSortedMap().forEach { (key, id) -> addProperty(key, id) } })
        }.toString().also { require(it.toByteArray(Charsets.UTF_8).size <= LIMIT) }
    }
}