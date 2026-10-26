package com.yourapp.iptv.data.datastore

import com.yourapp.iptv.data.backup.editAppData

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
    override suspend fun save(value: HomeAppearance) { store.editAppData { HomeAppearanceCodec.encode(it, value) } }
}

internal object HomeAppearanceCodec {
    private val language = stringPreferencesKey("language")
    private val side = stringPreferencesKey("sidebar_side")
    private val expanded = booleanPreferencesKey("expanded_sidebar")
    private val columns = intPreferencesKey("columns")
    private val restore = booleanPreferencesKey("restore_browse_session")
    private val darkTheme = booleanPreferencesKey("is_dark_theme")
    private val theme = stringPreferencesKey("color_theme")
    private val nightMode = booleanPreferencesKey("night_mode")
    fun decode(values: Preferences) = HomeAppearance(
        language = HomeLanguage.entries.firstOrNull { it.name == read(values, language) } ?: HomeLanguage.ARABIC,
        sidebarSide = SidebarSide.entries.firstOrNull { it.name == read(values, side) } ?: SidebarSide.AUTO,
        expandedSidebar = read(values, expanded) ?: false,
        columns = read(values, columns) ?: 3,
        restoreBrowseSession = read(values, restore) ?: true,
        theme = HomeTheme.entries.firstOrNull { it.name == read(values, theme) }
            ?: if (read(values, darkTheme) == false) HomeTheme.LIGHT else HomeTheme.DARK_BLUE,
        isNightMode = read(values, nightMode) ?: false
    ).normalized()
    fun encode(values: MutablePreferences, appearance: HomeAppearance) {
        val value = appearance.normalized()
        values[language] = value.language.name
        values[side] = value.sidebarSide.name
        values[expanded] = value.expandedSidebar
        values[columns] = value.columns
        values[restore] = value.restoreBrowseSession
        values[theme] = value.theme.name
        values[darkTheme] = value.isDarkTheme
        values[nightMode] = value.isNightMode
    }
    private inline fun <reified T> read(values: Preferences, key: Preferences.Key<T>): T? = values.asMap()[key] as? T
}