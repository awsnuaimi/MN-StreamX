package com.yourapp.iptv.data.datastore

import com.yourapp.iptv.domain.profile.ProfileScope
import com.yourapp.iptv.domain.repository.ProfileRepository
import com.yourapp.iptv.data.backup.editAppData

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.yourapp.iptv.domain.repository.SearchHistoryRepository
import com.yourapp.iptv.domain.search.SearchText
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*

internal val Context.searchHistoryStore by preferencesDataStore(name = "search_history")

class SearchHistoryDataStore(private val store: DataStore<Preferences>, private val profiles: ProfileRepository = ProfileRepository.Disabled) : SearchHistoryRepository {
    override fun observe(sourceKey: String): Flow<List<String>> {
        require(validKey(sourceKey))
        return store.data.map { decode(it[key(sourceKey)]) }.distinctUntilChanged().flowOn(Dispatchers.IO)
    }

    override suspend fun record(sourceKey: String, query: String) {
        require(validKey(sourceKey))
        val clean = SearchText.query(query)
        val normalized = SearchText.normalized(clean)
        if (normalized.isBlank()) return
        store.editAppData { values ->
            check(ProfileScope.belongs(sourceKey,profiles.state.value))
            val previous = decode(values[key(sourceKey)])
            val rows = (listOf(clean) + previous.filterNot { SearchText.normalized(it) == normalized }).take(LIMIT)
            val timestamp = values.asMap().filter { it.key.name.startsWith(PREFIX) }.values
                .maxOfOrNull { updated(it as? String) }?.coerceAtMost(Long.MAX_VALUE - 2)?.plus(1) ?: 1L
            values[key(sourceKey)] = encode(rows, timestamp)
            values.asMap().filter { it.key.name.startsWith(PREFIX) &&
                ProfileScope.profile(it.key.name.removePrefix(PREFIX)) == ProfileScope.profile(sourceKey) }.entries
                .sortedByDescending { updated(it.value as? String) }.drop(32)
                .forEach { values.remove(stringPreferencesKey(it.key.name)) }
        }
    }

    override suspend fun clear(sourceKey: String) {
        require(validKey(sourceKey))
        store.editAppData { check(ProfileScope.belongs(sourceKey,profiles.state.value));it.remove(key(sourceKey)) }
    }

    private fun key(source: String) = stringPreferencesKey(PREFIX + source)
    private fun validKey(value: String) = ProfileScope.valid(value)
    private fun encode(rows: List<String>, updated: Long) = JsonObject().apply {
        addProperty("version", 1); addProperty("updated", updated)
        add("queries", JsonArray().apply { rows.forEach(::add) })
    }.toString()

    private fun decode(text: String?): List<String> {
        if (text == null) return emptyList()
        return try {
            val root = root(text)
            val rows = root.getAsJsonArray("queries").also { require(it.size() <= LIMIT) }.map { element ->
                require(element.isJsonPrimitive && element.asJsonPrimitive.isString)
                element.asString.also { require(it == SearchText.query(it) && SearchText.normalized(it).isNotBlank()) }
            }
            require(rows.map(SearchText::normalized).distinct().size == rows.size)
            rows
        } catch (_: Exception) { throw IOException("Search history could not be read") }
    }

    private fun updated(text: String?): Long = try { root(requireNotNull(text)).get("updated").asLong.coerceAtLeast(0) }
        catch (_: Exception) { 0L }

    private fun root(text: String): JsonObject {
        require(text.length <= 16 * 1024)
        return JsonParser.parseString(text).asJsonObject.also { require(it.get("version").asInt == 1) }
    }

    companion object { const val LIMIT = 20; private const val PREFIX = "search_" }
}