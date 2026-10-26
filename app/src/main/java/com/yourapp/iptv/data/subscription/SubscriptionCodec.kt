package com.yourapp.iptv.data.subscription

import com.google.gson.JsonObject
import com.google.gson.JsonArray
import com.google.gson.JsonParser
import com.yourapp.iptv.domain.model.*
import java.io.IOException

internal object SubscriptionCodec {
    fun encode(value: Subscriptions): String = JsonObject().apply {
        addProperty("version", 1); addProperty("active", value.activeId)
        value.legacySourceKey?.let { addProperty("legacy", it) }
        add("accounts", JsonArray().apply { value.entries.forEach { item ->
            add(JsonObject().apply {
                addProperty("id", item.id); addProperty("name", item.name)
                val s = item.settings
                addProperty("source", s.source.name); addProperty("m3u", s.m3uUrl)
                addProperty("server", s.xtreamServer); addProperty("user", s.xtreamUsername)
                addProperty("password", s.xtreamPassword); addProperty("epg", s.epgUrl)
            })
        } })
    }.toString()

    fun decode(json: String): Subscriptions = try {
        require(json.toByteArray(Charsets.UTF_8).size <= KeystoreSubscriptionCipher.MAX_PLAIN)
        val root = JsonParser.parseString(json).asJsonObject
        require(root["version"].asInt == 1)
        val raw = root.getAsJsonArray("accounts")
        require(raw.size() in 1..Subscriptions.LIMIT)
        val entries = raw.map { element ->
            val o = element.asJsonObject
            val id = o.text("id", 80)
            require(id == Subscriptions.DEFAULT_ID || Regex("[0-9a-f-]{36}").matches(id))
            val source = PlaylistSource.valueOf(o.text("source", 10))
            Subscription(id, o.text("name", Subscriptions.NAME_LIMIT), AppSettings(
                m3uUrl = o.text("m3u"), xtreamServer = o.text("server"), xtreamUsername = o.text("user"),
                xtreamPassword = o.text("password"), epgUrl = o.text("epg"), source = source))
        }
        require(entries.map { it.id }.distinct().size == entries.size)
        val active = root.text("active", 80); require(entries.any { it.id == active })
        val legacy = root.get("legacy")?.takeUnless { it.isJsonNull }?.asString
        require(legacy == null || legacy.matches(Regex("[0-9a-f]{64}")))
        Subscriptions(entries, active, legacy)
    } catch (_: Exception) { throw IOException("Cannot read saved subscriptions") }

    private fun JsonObject.text(key: String, limit: Int = 8192): String = get(key).let {
        require(it != null && it.isJsonPrimitive && it.asJsonPrimitive.isString)
        it.asString.also { text -> require(text.length <= limit) }
    }
}