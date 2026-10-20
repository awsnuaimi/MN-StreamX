package com.yourapp.iptv.data.datastore

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.yourapp.iptv.domain.organization.*
import java.io.IOException

internal object OrganizationCodec {
    fun decode(text: String?, source: String): OrganizationConfig {
        if (text == null) return OrganizationConfig(sourceKey = source)
        try {
            require(text.length <= 8 * 1024 * 1024)
            val root = JsonParser.parseString(text).asJsonObject
            require(root.get("version").asInt == 1)
            val enabled = root.get("enabled").also { require(it.isJsonPrimitive && it.asJsonPrimitive.isBoolean) }.asBoolean
            val folders = root.getAsJsonArray("folders").map { element ->
                val row = element.asJsonObject
                val labelName = row.string("label")
                val label = labelName?.let { name -> requireNotNull(FolderLabel.entries.firstOrNull { it.name == name }) }
                ChannelFolder(requireNotNull(row.string("id")), row.string("parent"), label,
                    row.string("name").orEmpty(), row.get("order").asInt.also { require(it in 0..1000000) })
            }
            return FolderEdits.validate(OrganizationConfig(source, enabled, folders,
                readMap(root.getAsJsonObject("groups")), readMap(root.getAsJsonObject("channels")),
                root.get("revision").asLong.also { require(it >= 0) }))
        } catch (error: Exception) { throw IOException("Cannot read organization configuration", error) }
    }
    fun encode(value: OrganizationConfig): String {
        FolderEdits.validate(value)
        val root = JsonObject().apply {
            addProperty("version", 1); addProperty("enabled", value.enabled); addProperty("revision", value.revision)
            add("folders", com.google.gson.JsonArray().apply { value.folders.forEach { f -> add(JsonObject().apply {
                addProperty("id", f.id); addProperty("parent", f.parentId); addProperty("label", f.label?.name)
                addProperty("name", f.name); addProperty("order", f.order)
            }) } })
            add("groups", writeMap(value.groupOverrides)); add("channels", writeMap(value.channelOverrides))
        }
        return root.toString().also { require(it.length <= 8 * 1024 * 1024) }
    }
    private fun JsonObject.string(key: String): String? = get(key)?.takeUnless { it.isJsonNull }?.let {
        require(it.isJsonPrimitive && it.asJsonPrimitive.isString); it.asString
    }
    private fun readMap(value: JsonObject): Map<String, String> = value.entrySet().associate { (key, item) ->
        require(item.isJsonPrimitive && item.asJsonPrimitive.isString); key to item.asString
    }
    private fun writeMap(value: Map<String, String>) = JsonObject().apply { value.forEach { (k, v) -> addProperty(k, v) } }
}