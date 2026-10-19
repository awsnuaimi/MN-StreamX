package com.yourapp.iptv.data.datastore

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.yourapp.iptv.domain.model.PlayerAppearance
import com.yourapp.iptv.domain.model.PlayerInfoStyle
import com.yourapp.iptv.domain.model.PlayerPanelShade
import com.yourapp.iptv.domain.model.PlayerTextSize

internal object PlayerAppearanceCodec {
    private val style = stringPreferencesKey("info_style")
    private val size = stringPreferencesKey("text_size")
    private val shade = stringPreferencesKey("panel_shade")
    private val seconds = intPreferencesKey("display_seconds")
    private val logo = booleanPreferencesKey("show_logo")
    private val group = booleanPreferencesKey("show_group")
    private val counter = booleanPreferencesKey("show_counter")
    private val next = booleanPreferencesKey("show_next")
    private val description = booleanPreferencesKey("show_description")
    private val channelButtons = booleanPreferencesKey("show_channel_buttons")
    private val hints = booleanPreferencesKey("show_hints")

    fun decode(values: Preferences) = PlayerAppearance(
        style = PlayerInfoStyle.entries.firstOrNull { it.name == read(values, style) } ?: PlayerInfoStyle.COMPACT,
        textSize = PlayerTextSize.entries.firstOrNull { it.name == read(values, size) } ?: PlayerTextSize.NORMAL,
        panelShade = PlayerPanelShade.entries.firstOrNull { it.name == read(values, shade) } ?: PlayerPanelShade.BALANCED,
        displaySeconds = read(values, seconds) ?: 5,
        showLogo = read(values, logo) ?: true,
        showGroup = read(values, group) ?: true,
        showCounter = read(values, counter) ?: true,
        showNext = read(values, next) ?: true,
        showDescription = read(values, description) ?: true,
        showChannelButtons = read(values, channelButtons) ?: true,
        showHints = read(values, hints) ?: false
    ).normalized()

    fun encode(values: MutablePreferences, appearance: PlayerAppearance) {
        val value = appearance.normalized()
        values[style] = value.style.name
        values[size] = value.textSize.name
        values[shade] = value.panelShade.name
        values[seconds] = value.displaySeconds
        values[logo] = value.showLogo
        values[group] = value.showGroup
        values[counter] = value.showCounter
        values[next] = value.showNext
        values[description] = value.showDescription
        values[channelButtons] = value.showChannelButtons
        values[hints] = value.showHints
    }

    private inline fun <reified T> read(values: Preferences, key: Preferences.Key<T>): T? = values.asMap()[key] as? T
}