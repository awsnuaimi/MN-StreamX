package com.yourapp.iptv.data.datastore

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.yourapp.iptv.domain.model.WatchHistory
import com.yourapp.iptv.domain.model.WatchHistoryEntry

internal object WatchHistoryCodec {
    private const val MAX_TEXT = 64 * 1024
    fun encode(rows: List<WatchHistoryEntry>, updated: Long): String = JsonObject().apply {
        require(rows.size <= WatchHistory.LIMIT && rows.map { it.channelId }.distinct().size == rows.size)
        addProperty("version", 1); addProperty("updated", updated)
        add("rows", JsonArray().apply { rows.forEach { row ->
            require(row.channelId.isNotBlank() && row.channelId.length <= 512 && row.watchedAtMillis >= 0)
            add(JsonObject().apply { addProperty("id", row.channelId); addProperty("at", row.watchedAtMillis) })
        } })
    }.toString().also { require(it.length <= MAX_TEXT) }

    fun decode(text: String?): List<WatchHistoryEntry> {
        if (text == null) return emptyList()
        return try {
        val root = root(text)
        val rows = root.getAsJsonArray("rows").also { require(it.size() <= WatchHistory.LIMIT) }.map {
            val row = it.asJsonObject
            val id = row.get("id").also { require(it.isJsonPrimitive && it.asJsonPrimitive.isString) }.asString
            val at = row.get("at").asLong
            require(id.isNotBlank() && id.length <= 512 && at >= 0)
            WatchHistoryEntry(id, at)
        }
        require(rows.map { it.channelId }.distinct().size == rows.size)
        rows.sortedByDescending { it.watchedAtMillis }
        } catch (_: Exception) { emptyList() }
    }

    fun updated(text: String): Long = try { root(text).get("updated").asLong.coerceIn(0, Long.MAX_VALUE - 2) }
        catch (_: Exception) { 0L }

    private fun root(text: String): JsonObject {
        require(text.length <= MAX_TEXT)
        return JsonParser.parseString(text).asJsonObject.also { require(it.get("version").asInt == 1) }
    }
}