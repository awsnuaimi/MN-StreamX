package com.yourapp.iptv.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.domain.repository.HomeAppearanceRepository
import kotlinx.coroutines.flow.map

internal val Context.homeAppearanceStore by preferencesDataStore(name = "home_appearance")
class HomeAppearanceDataStore(private val store: DataStore<Preferences>) : HomeAppearanceRepository {
    override fun observe() = store.data.map(HomeAppearanceCodec::decode)
    override suspend fun save(value: HomeAppearance) { store.edit { HomeAppearanceCodec.encode(it, value) } }
}

internal object HomeAppearanceCodec {
    private val language = stringPreferencesKey("language")
    private val side = stringPreferencesKey("sidebar_side")
    private val expanded = booleanPreferencesKey("expanded_sidebar")
    private val columns = intPreferencesKey("columns")
    fun decode(values: Preferences) = HomeAppearance(
        language = HomeLanguage.entries.firstOrNull { it.name == read(values, language) } ?: HomeLanguage.ARABIC,
        sidebarSide = SidebarSide.entries.firstOrNull { it.name == read(values, side) } ?: SidebarSide.AUTO,
        expandedSidebar = read(values, expanded) ?: false,
        columns = read(values, columns) ?: 3
    ).normalized()
    fun encode(values: MutablePreferences, appearance: HomeAppearance) {
        val value = appearance.normalized()
        values[language] = value.language.name
        values[side] = value.sidebarSide.name
        values[expanded] = value.expandedSidebar
        values[columns] = value.columns
    }
    private inline fun <reified T> read(values: Preferences, key: Preferences.Key<T>): T? = values.asMap()[key] as? T
}