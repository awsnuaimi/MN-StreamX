package com.yourapp.iptv.data.datastore

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.yourapp.iptv.domain.model.*

internal object HomeSessionCodec {
    private const val MAX_TEXT = 16 * 1024
    fun encode(value: HomeBrowseSession, now: Long): String = JsonObject().apply {
        require(value.group == null || value.group.length <= 4096)
        require(listOf(value.rootFolderId, value.focusedChannelId, value.position.anchorId).all { it == null || it.length <= 512 })
        addProperty("version", 1); addProperty("updated", now)
        addProperty("group", value.group); addProperty("root", value.rootFolderId)
        addProperty("favorites", value.favoritesOnly); addProperty("focused", value.focusedChannelId)
        addProperty("anchor", value.position.anchorId)
        addProperty("offset", value.position.offsetPx.coerceIn(0, 100_000))
        addProperty("columns", value.position.columns.takeIf { it in 1..5 } ?: 0)
    }.toString().also { require(it.length <= MAX_TEXT) }

    fun decode(text: String): HomeBrowseSession? = try {
        require(text.length <= MAX_TEXT)
        val root = JsonParser.parseString(text).asJsonObject
        require(root.get("version").asInt == 1)
        fun string(name: String, max: Int = 512): String? = root.get(name)?.takeUnless { it.isJsonNull }?.let {
            require(it.isJsonPrimitive && it.asJsonPrimitive.isString)
            it.asString.also { text -> require(text.length <= max) }
        }
        val favorites = root.get("favorites").also { require(it.isJsonPrimitive && it.asJsonPrimitive.isBoolean) }.asBoolean
        val offset = root.get("offset").asInt.also { require(it in 0..100_000) }
        val columns = root.get("columns").asInt.also { require(it in 0..5) }
        HomeBrowseSession(string("group", 4096), string("root"), favorites, string("focused"),
            HomeGridPosition(string("anchor"), offset, columns))
    } catch (_: Exception) { null }

    fun updated(text: String): Long = try {
        require(text.length <= MAX_TEXT)
        JsonParser.parseString(text).asJsonObject.get("updated").asLong.coerceAtLeast(0)
    }
        catch (_: Exception) { 0L }
}