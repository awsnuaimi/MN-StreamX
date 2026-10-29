package com.yourapp.iptv.data.backup

import androidx.datastore.preferences.core.Preferences
import com.google.gson.*
import com.yourapp.iptv.data.datastore.*
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.domain.search.SearchText
import java.util.UUID

internal data class BackupAccountMerge(val value: Subscriptions, val newCount: Int, val existingCount: Int)
internal object BackupMerge {
    private fun placeholder(value: Subscription) = value.id == Subscriptions.DEFAULT_ID &&
        value.name.isBlank() && value.settings == AppSettings()
    fun usefulEntries(value: Subscriptions) = value.entries.filterNot(::placeholder)
    fun accounts(current: Subscriptions, incoming: Subscriptions, activate: Boolean): BackupAccountMerge {
        val imported = usefulEntries(incoming)
        val entries = current.entries.filterNot { placeholder(it) && imported.isNotEmpty() }.toMutableList()
        val mapping = HashMap<String, String>()
        var added = 0; var existing = 0
        for (item in imported) {
            val match = entries.firstOrNull {
                if (item.settings.hasSelectedSource()) it.settings.hasSelectedSource() &&
                    PlaylistCacheKey.of(it.settings) == PlaylistCacheKey.of(item.settings)
                else !it.settings.hasSelectedSource() && it.settings == item.settings && it.name == item.name
            }
            if (match != null) { mapping[item.id] = match.id; existing++; continue }
            if (entries.size >= Subscriptions.LIMIT) throw BackupException(BackupProblem.ACCOUNT_LIMIT)
            val id = item.id.takeUnless { candidate -> entries.any { it.id == candidate } } ?: UUID.randomUUID().toString()
            entries.add(item.copy(id = id)); mapping[item.id] = id; added++
        }
        if (entries.isEmpty()) entries.addAll(current.entries)
        val active = if (activate) mapping[incoming.activeId] else null
        val nextActive = active ?: current.activeId.takeIf { id -> entries.any { it.id == id } } ?: entries.first().id
        return BackupAccountMerge(Subscriptions(entries, nextActive, current.legacySourceKey), added, existing)
    }

    fun stores(current: Map<BackupStore, Preferences>, incoming: BackupPayload,
        options: BackupRestoreOptions): Map<BackupStore, Preferences> = current.mapValues { (area, prefs) ->
        val old = BackupPreferenceCodec.values(prefs).toMutableMap()
        incoming.areas[area].orEmpty().forEach { (name, value) ->
            when {
                area in PortablePreferences.global && options.settings -> old[name] = value
                area == BackupStore.ORGANIZATION && name.startsWith("live_") -> {
                    if (options.settings) old[name] = value
                }
                area == BackupStore.ORGANIZATION && options.folders -> {
                    val key = name.removePrefix("source_")
                    val local = OrganizationCodec.decode(old[name]?.text, key)
                    val saved = OrganizationCodec.decode(value.text, key)
                    val revision = maxOf(local.revision, saved.revision)
                    require(revision < Long.MAX_VALUE)
                    old[name] = BackupValue(BackupValueType.STRING, OrganizationCodec.encode(saved.copy(revision = revision + 1)))
                }
                area == BackupStore.HISTORY -> {
                    val rows = (WatchHistoryCodec.decode(old[name]?.text) + WatchHistoryCodec.decode(value.text))
                        .sortedByDescending { it.watchedAtMillis }.distinctBy { it.channelId }.take(WatchHistory.LIMIT)
                    old[name] = BackupValue(BackupValueType.STRING, WatchHistoryCodec.encode(rows, System.currentTimeMillis()))
                }
                area == BackupStore.VOD -> {
                    val rows = (VodProgressCodec.decode(old[name]?.text) + VodProgressCodec.decode(value.text))
                        .sortedByDescending { it.updatedAtMs }.distinctBy { it.kind to it.id }.take(VodProgress.LIMIT)
                    old[name] = BackupValue(BackupValueType.STRING, VodProgressCodec.encode(rows))
                }
                area == BackupStore.SEARCH -> old[name] = mergeSearch(old[name], value)
            }
        }
        BackupPreferenceCodec.preferences(old)
    }

    private fun mergeSearch(old: BackupValue?, saved: BackupValue): BackupValue {
        fun rows(value: BackupValue?): List<String> = value?.let {
            JsonParser.parseString(it.text).asJsonObject.getAsJsonArray("queries").map { row -> row.asString }
        }.orEmpty()
        val queries = (rows(old) + rows(saved)).distinctBy(SearchText::normalized).take(20)
        return BackupValue(BackupValueType.STRING, JsonObject().apply {
            addProperty("version", 1); addProperty("updated", System.currentTimeMillis())
            add("queries", JsonArray().apply { queries.forEach(::add) })
        }.toString())
    }
}