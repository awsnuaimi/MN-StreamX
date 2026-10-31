package com.yourapp.iptv.ui.screen.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import com.yourapp.iptv.ui.component.common.ContextFocus

/** The host owns restoration; rows supply stable keys independent of their translated titles. */
internal class SettingsFocusBinding(val focus: ContextFocus, val context: String) {
    fun activate(key: String) {
        if (key.isNotBlank()) focus.record(context, key, order(key))
    }
    fun order(key: String): Int = ORDER.indexOf(key).takeIf { it >= 0 } ?: ORDER.size

    companion object {
        private val ORDER = listOf("settings-appearance-retry", "settings-language", "settings-theme-DARK_BLUE",
            "settings-theme-RED", "settings-theme-LIGHT", "settings-night", "settings-player-text", "settings-display",
            "settings-advanced", "settings-logos", "settings-sidebar-side", "settings-sidebar-expanded", "settings-browse-restore",
            "settings-open-subscriptions", "settings-open-profiles", "settings-open-player", "startup-toggle", "startup-retry",
            "adaptive-toggle", "adaptive-reset", "adaptive-retry",
            "settings-open-captions", "settings-open-parental", "settings-open-backup", "storage-refresh", "storage-delete",
            "update-action", "update-cancel", "update-install", "settings-open-diagnostics")
    }
}

internal val LocalSettingsFocus = staticCompositionLocalOf<SettingsFocusBinding?> { null }

@Composable
internal fun settingsFocusTarget(key: String, enabled: Boolean = true): Modifier {
    val binding = LocalSettingsFocus.current
    return if (binding == null || key.isBlank() || !enabled) Modifier
        else binding.focus.target(binding.context, key, binding.order(key))
}