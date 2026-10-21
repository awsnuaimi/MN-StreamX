package com.yourapp.iptv.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.domain.repository.CaptionRepository
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

internal val Context.captionStore by preferencesDataStore(name = "player_captions")
class CaptionDataStore(private val store: DataStore<Preferences>) : CaptionRepository {
    override fun observe() = store.data.map(CaptionCodec::decode).distinctUntilChanged()
    override suspend fun save(value: CaptionSettings) { store.edit { CaptionCodec.encode(it, value.normalized()) } }
}
internal object CaptionCodec {
    private val mode = stringPreferencesKey("mode")
    private val target = stringPreferencesKey("target")
    private val source = stringPreferencesKey("source")
    private val size = stringPreferencesKey("size")
    private val shaded = booleanPreferencesKey("shaded")
    fun decode(p: Preferences) = CaptionSettings(
        CaptionMode.entries.firstOrNull { it.name == read<String>(p, mode) } ?: CaptionMode.OFF,
        CaptionLanguage.fromTag(read(p, target)) ?: CaptionLanguage.ARABIC,
        read(p, source) ?: "auto",
        CaptionTextSize.entries.firstOrNull { it.name == read<String>(p, size) } ?: CaptionTextSize.MEDIUM,
        read(p, shaded) ?: true).normalized()
    fun encode(p: MutablePreferences, v: CaptionSettings) {
        p[mode] = v.mode.name; p[target] = v.target.tag; p[source] = v.source
        p[size] = v.textSize.name; p[shaded] = v.shaded
    }
    private inline fun <reified T> read(p: Preferences, key: Preferences.Key<T>): T? = p.asMap()[key] as? T
}