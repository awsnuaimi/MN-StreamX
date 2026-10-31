package com.yourapp.iptv.data.datastore

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.yourapp.iptv.data.backup.BackupJson
import com.yourapp.iptv.domain.model.LiveTvPreferences
import com.yourapp.iptv.domain.model.LiveTvSort
import com.yourapp.iptv.domain.model.AdaptiveLiveChoice
import com.yourapp.iptv.domain.model.AdaptiveLiveNavigation
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
                qualities, decodeAdaptive(root)).validate()
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
            // Keep the canonical representation of existing records unchanged until this feature is used.
            if (value.adaptive != AdaptiveLiveNavigation()) add("adaptive", JsonObject().apply {
                addProperty("enabled", value.adaptive.enabled)
                addProperty("epoch", value.adaptive.epoch)
                add("contexts", JsonArray().apply {
                    value.adaptive.contexts.forEach { (context, choices) -> add(JsonObject().apply {
                        addProperty("context", context)
                        add("choices", JsonArray().apply { choices.forEach { choice -> add(JsonObject().apply {
                            addProperty("family", choice.family); addProperty("score", choice.score)
                        }) } })
                    }) }
                })
            })
        }.toString().also { require(it.toByteArray(Charsets.UTF_8).size <= LIMIT) }
    }

    private fun decodeAdaptive(root: JsonObject): AdaptiveLiveNavigation {
        if (!root.has("adaptive")) return AdaptiveLiveNavigation()
        val value = root.getAsJsonObject("adaptive")
        val enabled = value.get("enabled").also { require(it.isJsonPrimitive && it.asJsonPrimitive.isBoolean) }.asBoolean
        val epoch = if (!value.has("epoch")) 0L else value.get("epoch").also {
            require(it.isJsonPrimitive && it.asJsonPrimitive.isNumber && Regex("0|[1-9][0-9]{0,18}").matches(it.toString()))
        }.asString.toLong().also { require(it >= 0) }
        val rows = value.getAsJsonArray("contexts")
        require(rows.size() <= AdaptiveLiveNavigation.MAX_CONTEXTS)
        val contexts = linkedMapOf<String, List<AdaptiveLiveChoice>>()
        rows.forEach { element ->
            val row = element.asJsonObject
            val context = row.get("context").also { require(it.isJsonPrimitive && it.asJsonPrimitive.isString) }.asString
            require(context !in contexts)
            val choices = row.getAsJsonArray("choices")
            require(choices.size() <= AdaptiveLiveNavigation.MAX_CHOICES)
            contexts[context] = choices.map { choice ->
                val item = choice.asJsonObject
                val family = item.get("family").also { require(it.isJsonPrimitive && it.asJsonPrimitive.isString) }.asString
                val score = item.get("score").also {
                    require(it.isJsonPrimitive && it.asJsonPrimitive.isNumber && Regex("[1-9][0-9]?").matches(it.toString()))
                }.asInt
                AdaptiveLiveChoice(family, score)
            }
        }
        return AdaptiveLiveNavigation(enabled, contexts, epoch).validate()
    }
}