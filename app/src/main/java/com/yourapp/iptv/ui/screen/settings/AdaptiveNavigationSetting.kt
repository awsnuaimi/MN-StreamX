package com.yourapp.iptv.ui.screen.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yourapp.iptv.domain.model.PlaylistCacheKey
import com.yourapp.iptv.domain.profile.ProfileScope
import com.yourapp.iptv.domain.profile.ProfilesState
import com.yourapp.iptv.ui.state.LiveTvPreferencesState
import com.yourapp.iptv.ui.state.SettingsUiState
import com.yourapp.iptv.viewmodel.LiveTvPreferencesViewModel

/** Shared subscription records have no active profile; never use the editor's unsaved draft. */
internal fun adaptiveSettingsScope(account: SettingsUiState, profiles: ProfilesState): String {
    val active = account.subscriptions.firstOrNull { it.id == account.activeSubscriptionId }?.settings
    return if (account.enabled && profiles.ready && !profiles.failed && !profiles.switching &&
        active != null && active.hasSelectedSource()) ProfileScope.key(profiles.catalog.activeId, PlaylistCacheKey.of(active)) else ""
}

@Composable
internal fun AdaptiveNavigationSetting(scope: String, labels: SettingsLabels,
    model: LiveTvPreferencesViewModel = hiltViewModel(key = "adaptive-settings")) {
    val observed by model.state.collectAsStateWithLifecycle()
    LaunchedEffect(scope) { model.bind(scope) }
    val state = observed.takeIf { it.scope == scope } ?: LiveTvPreferencesState(scope)
    val available = scope.isNotEmpty() && state.ready && !state.saving
    val category = SettingsCategory.PLAYBACK
    fun text(ar: String, en: String, de: String) = labels.text(ar, en, de)
    SettingsActionRow(text("التنقّل التكيفي", "Adaptive navigation", "Adaptives Navigieren"),
        text("يتعلّم من اختيارات المشاهدة المتكررة لتحديد بداية القائمة. لموضع الرجوع المحفوظ الأولوية. تبقى الأسهم ثابتة. لهذا الملف والاشتراك.",
            "Repeated watch choices guide fresh list entry. Remembered positions take priority. Arrows stay fixed. For this profile and subscription.",
            "Wiederholte Senderwahl bestimmt neue Listeneinstiege. Gespeicherte Positionen haben Vorrang. Pfeile bleiben fest. Für dieses Profil und Abo."),
        value = when {
            scope.isEmpty() -> text("اختر اشتراكًا أولًا", "Select a subscription first", "Zuerst ein Abo wählen")
            state.saving -> text("جارٍ الحفظ…", "Saving…", "Wird gespeichert…")
            !state.ready -> text("غير متاح", "Unavailable", "Nicht verfügbar")
            state.value.adaptive.enabled -> text("مفعّل", "On", "Ein")
            else -> text("معطّل", "Off", "Aus")
        }, category = category, enabled = available,
        checked = state.value.adaptive.enabled.takeIf { state.ready }, focusKey = "adaptive-toggle",
        modifier = Modifier.testTag("adaptive-toggle")) {
        model.change(scope) { it.copy(adaptive = it.adaptive.withEnabled(!it.adaptive.enabled)) }
    }
    SettingsActionRow(text("مسح التعلّم", "Clear learning", "Lerndaten löschen"),
        text("يمسح الاختيارات المتعلّمة لهذا الملف والاشتراك.", "Clear learned choices for this profile and subscription.",
            "Gelernte Auswahl für dieses Profil und Abo löschen."), category = category,
        enabled = available, focusKey = "adaptive-reset",
        modifier = Modifier.testTag("adaptive-reset")) {
        model.change(scope) { it.copy(adaptive = it.adaptive.clear()) }
    }
    if (scope.isNotEmpty() && state.error) {
        Text(text("تعذر قراءة أو حفظ بيانات التنقّل. أعد المحاولة.", "Navigation data could not be loaded or saved. Try again.",
            "Navigationsdaten konnten nicht geladen oder gespeichert werden. Erneut versuchen."), color = MaterialTheme.colorScheme.error)
        SettingsActionRow(text("إعادة المحاولة", "Retry", "Erneut versuchen"), category = category,
            enabled = !state.saving, focusKey = "adaptive-retry", modifier = Modifier.testTag("adaptive-retry")) {
            model.bind(scope, retry = true)
        }
    }
}