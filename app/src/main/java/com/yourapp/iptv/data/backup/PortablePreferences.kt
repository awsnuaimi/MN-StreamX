package com.yourapp.iptv.data.backup

import androidx.datastore.preferences.core.*
import com.google.gson.JsonObject
import com.yourapp.iptv.domain.profile.*
import com.yourapp.iptv.data.datastore.*
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.domain.organization.OrganizationNames
import com.yourapp.iptv.domain.search.SearchText

internal object PortablePreferences {
    val global = setOf(BackupStore.HOME, BackupStore.PLAYER, BackupStore.STARTUP, BackupStore.CAPTIONS)
    val portable = BackupStore.entries.toSet() - setOf(BackupStore.SETTINGS,BackupStore.PROFILES)

    fun collect(stores: Map<BackupStore, Preferences>, accounts: Subscriptions, profiles: List<UserProfile> = UserProfiles().entries): Map<BackupStore, Map<String, BackupValue>> {
        val keys = sourceKeys(accounts,profiles)
        val organizationKeys = accounts.entries.filter { it.settings.hasSelectedSource() }
            .map { "source_" + OrganizationNames.sourceKey(it.settings) }.toSet()
        return portable.associateWith { area ->
            val original = requireNotNull(stores[area])
            if (area in global) canonicalGlobal(area, original)
            else BackupPreferenceCodec.values(original).filterKeys { name ->
                if (area == BackupStore.ORGANIZATION) name in organizationKeys
                else name.removePrefix(prefix(area)).let { it in keys && name == prefix(area) + it }
            }.mapValues { (name, value) -> canonicalRecord(area, name, value) }
        }
    }

    fun validate(areas: Map<BackupStore, Map<String, BackupValue>>, accounts: Subscriptions, profiles: List<UserProfile> = UserProfiles().entries) {
        require(areas.keys == portable)
        val keys = sourceKeys(accounts,profiles)
        val organizationKeys = accounts.entries.filter { it.settings.hasSelectedSource() }
            .map { "source_" + OrganizationNames.sourceKey(it.settings) }.toSet()
        areas.forEach { (area, values) ->
            if (area in global) {
                require(values == canonicalGlobal(area, BackupPreferenceCodec.preferences(values)))
            } else {
                require(values.size <= Subscriptions.LIMIT * (if (area == BackupStore.ORGANIZATION) 1 else profiles.size))
                values.forEach { (name, value) ->
                    require(if (area == BackupStore.ORGANIZATION) name in organizationKeys
                        else name.startsWith(prefix(area)) && name.removePrefix(prefix(area)) in keys)
                    canonicalRecord(area, name, value)
                }
            }
        }
    }
    fun sourceKeys(accounts: Subscriptions, profiles: List<UserProfile> = UserProfiles().entries) =
        profiles.flatMap { person -> accounts.entries.filter { it.settings.hasSelectedSource() }
            .map { ProfileScope.key(person.id,PlaylistCacheKey.of(it.settings)) } }.toSet()
    fun prefix(area: BackupStore) = when (area) {
        BackupStore.HISTORY -> "history_"
        BackupStore.VOD -> "vod_"
        BackupStore.SEARCH -> "search_"
        BackupStore.ORGANIZATION -> "source_"
        else -> error("Not a scoped store")
    }
    private fun canonicalGlobal(area: BackupStore, source: Preferences): Map<String, BackupValue> {
        val result = mutablePreferencesOf()
        when (area) {
            BackupStore.HOME -> HomeAppearanceCodec.encode(result, HomeAppearanceCodec.decode(source))
            BackupStore.PLAYER -> PlayerAppearanceCodec.encode(result, PlayerAppearanceCodec.decode(source))
            BackupStore.CAPTIONS -> CaptionCodec.encode(result, CaptionCodec.decode(source))
            BackupStore.STARTUP -> result[booleanPreferencesKey("resume_last_channel")] =
                source.asMap()[booleanPreferencesKey("resume_last_channel")] as? Boolean ?: false
            else -> error("Not a global store")
        }
        return BackupPreferenceCodec.values(result)
    }
    fun canonicalRecord(area: BackupStore, name: String, value: BackupValue): BackupValue {
        require(value.type == BackupValueType.STRING)
        val root = record(value.text, if (area == BackupStore.ORGANIZATION) 8 * 1024 * 1024 else 64 * 1024)
        val text = when (area) {
            BackupStore.ORGANIZATION -> OrganizationCodec.encode(OrganizationCodec.decode(value.text, name.removePrefix("source_")))
            BackupStore.HISTORY -> {
                val rows = WatchHistoryCodec.decode(value.text)
                require(root.getAsJsonArray("rows").size() == rows.size)
                WatchHistoryCodec.encode(rows, WatchHistoryCodec.updated(value.text))
            }
            BackupStore.VOD -> {
                val rows = VodProgressCodec.decode(value.text)
                require(root.getAsJsonArray("entries").size() == rows.size)
                VodProgressCodec.encode(rows)
            }
            BackupStore.SEARCH -> {
                val queries = root.getAsJsonArray("queries").also { require(it.size() <= 20) }.map {
                    require(it.isJsonPrimitive && it.asJsonPrimitive.isString)
                    it.asString.also { query -> require(query == SearchText.query(query) && SearchText.normalized(query).isNotBlank()) }
                }
                require(queries.map(SearchText::normalized).distinct().size == queries.size)
                JsonObject().apply {
                    addProperty("version", 1)
                    addProperty("updated", root.get("updated")?.asLong?.coerceIn(0, Long.MAX_VALUE - 2) ?: 0L)
                    add("queries", com.google.gson.JsonArray().apply { queries.forEach(::add) })
                }.toString()
            }
            else -> error("Not a scoped store")
        }
        return BackupValue(BackupValueType.STRING, text)
    }
    private fun record(text: String, limit: Int): JsonObject = BackupJson.read(text.toByteArray(Charsets.UTF_8), limit)
        .also { require(it.get("version").asInt == 1) }
}