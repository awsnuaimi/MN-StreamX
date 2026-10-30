package com.yourapp.iptv.data.datastore

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.yourapp.iptv.domain.model.*

internal object HomeSessionCodec {
    private const val MAX_TEXT = 16 * 1024
    fun encode(value: HomeBrowseSession, now: Long): String {
        val root = fields(value)
        root.addProperty("version", 2); root.addProperty("updated", now)
        val bookmarks = value.bookmarks.takeLast(HomeBrowseMemory.LIMIT).filter { HomeBrowseMemory.valid(it) }.toMutableList()
        while (true) {
            root.add("bookmarks", JsonArray().apply { bookmarks.forEach { add(fields(it.session())) } })
            val text = root.toString()
            if (text.length <= MAX_TEXT) return text
            require(bookmarks.isNotEmpty())
            bookmarks.removeAt(0)
        }
    }

    private fun fields(value: HomeBrowseSession) = JsonObject().apply {
        require(value.group == null || value.group.length <= 4096)
        require(listOf(value.rootFolderId, value.focusedChannelId, value.position.anchorId).all { it == null || it.length <= 512 })
        addProperty("group", value.group); addProperty("root", value.rootFolderId)
        addProperty("favorites", value.favoritesOnly); addProperty("focused", value.focusedChannelId)
        addProperty("anchor", value.position.anchorId)
        addProperty("offset", value.position.offsetPx.coerceIn(0, 100_000))
        addProperty("columns", value.position.columns.takeIf { it in 1..5 } ?: 0)
    }

    fun decode(text: String): HomeBrowseSession? = try {
        require(text.length <= MAX_TEXT)
        val root = JsonParser.parseString(text).asJsonObject
        val version = root.get("version").asInt
        require(version in 1..2)
        val base = readFields(root)
        val bookmarks = if (version == 1 || !root.has("bookmarks")) emptyList() else {
            val array = root.getAsJsonArray("bookmarks")
            require(array.size() <= HomeBrowseMemory.LIMIT)
            array.mapNotNull { item -> runCatching {
                val row = readFields(item.asJsonObject)
                HomeBrowseBookmark(row.group, row.rootFolderId, row.favoritesOnly, row.focusedChannelId, row.position)
                    .takeIf { HomeBrowseMemory.valid(it) }
            }.getOrNull() }
        }
        base.copy(bookmarks = bookmarks)
    } catch (_: Exception) { null }

    private fun readFields(root: JsonObject): HomeBrowseSession {
        fun string(name: String, max: Int = 512): String? = root.get(name)?.takeUnless { it.isJsonNull }?.let {
            require(it.isJsonPrimitive && it.asJsonPrimitive.isString)
            it.asString.also { text -> require(text.length <= max) }
        }
        val favorites = root.get("favorites").also { require(it.isJsonPrimitive && it.asJsonPrimitive.isBoolean) }.asBoolean
        val offset = root.get("offset").asInt.also { require(it in 0..100_000) }
        val columns = root.get("columns").asInt.also { require(it in 0..5) }
        return HomeBrowseSession(string("group", 4096), string("root"), favorites, string("focused"),
            HomeGridPosition(string("anchor"), offset, columns))
    }

    fun updated(text: String): Long = try {
        require(text.length <= MAX_TEXT)
        JsonParser.parseString(text).asJsonObject.get("updated").asLong.coerceAtLeast(0)
    }
        catch (_: Exception) { 0L }
}