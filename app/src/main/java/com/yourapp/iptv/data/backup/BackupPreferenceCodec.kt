package com.yourapp.iptv.data.backup

import androidx.datastore.preferences.core.*
import com.google.gson.*

internal object BackupPreferenceCodec {
    fun values(preferences: Preferences): Map<String, BackupValue> = preferences.asMap().entries.associate { (key, value) ->
        key.name to when (value) {
            is String -> BackupValue(BackupValueType.STRING, value)
            is Boolean -> BackupValue(BackupValueType.BOOLEAN, value.toString())
            is Int -> BackupValue(BackupValueType.INT, value.toString())
            is Long -> BackupValue(BackupValueType.LONG, value.toString())
            is Float -> BackupValue(BackupValueType.FLOAT, value.toString())
            is Double -> BackupValue(BackupValueType.DOUBLE, value.toString())
            is Set<*> -> BackupValue(BackupValueType.STRING_SET, JsonArray().apply {
                value.map { it as String }.sorted().forEach(::add)
            }.toString())
            else -> error("Unsupported preference type")
        }
    }
    fun preferences(values: Map<String, BackupValue>): Preferences = mutablePreferencesOf().apply {
        values.forEach { (name, value) ->
            require(name.length in 1..160)
            when (value.type) {
                BackupValueType.STRING -> this[stringPreferencesKey(name)] = value.text
                BackupValueType.BOOLEAN -> this[booleanPreferencesKey(name)] = when (value.text) { "true" -> true; "false" -> false; else -> error("Invalid boolean") }
                BackupValueType.INT -> this[intPreferencesKey(name)] = value.text.toInt()
                BackupValueType.LONG -> this[longPreferencesKey(name)] = value.text.toLong()
                BackupValueType.FLOAT -> this[floatPreferencesKey(name)] = value.text.toFloat().also { require(it.isFinite()) }
                BackupValueType.DOUBLE -> this[doublePreferencesKey(name)] = value.text.toDouble().also { require(it.isFinite()) }
                BackupValueType.STRING_SET -> this[stringSetPreferencesKey(name)] = JsonParser.parseString(value.text).asJsonArray.map {
                    require(it.isJsonPrimitive && it.asJsonPrimitive.isString); it.asString
                }.toSet()
            }
        }
    }
    fun encode(areas: Map<BackupStore, Map<String, BackupValue>>) = JsonObject().apply {
        areas.forEach { (store, values) -> add(store.name, JsonObject().apply {
            values.forEach { (name, value) -> add(name, JsonObject().apply {
                addProperty("type", value.type.name); addProperty("value", value.text)
            }) }
        }) }
    }
    fun decode(root: JsonObject): Map<BackupStore, Map<String, BackupValue>> {
        require(root.size() <= BackupStore.entries.size)
        return root.entrySet().associate { (name, element) ->
            val area = BackupStore.valueOf(name)
            val values = element.asJsonObject.also { require(it.size() <= 1024) }.entrySet().associate { (key, raw) ->
                val row = raw.asJsonObject
                key to BackupValue(BackupValueType.valueOf(text(row, "type")), text(row, "value"))
            }
            preferences(values) // Validate every typed value before it reaches a DataStore.
            area to values
        }
    }
    fun text(root: JsonObject, name: String) = root.get(name).let {
        require(it != null && it.isJsonPrimitive && it.asJsonPrimitive.isString); it.asString
    }
}