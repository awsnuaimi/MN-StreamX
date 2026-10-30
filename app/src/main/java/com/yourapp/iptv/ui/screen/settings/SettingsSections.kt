package com.yourapp.iptv.ui.screen.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.yourapp.iptv.BuildConfig
import com.yourapp.iptv.R
import com.yourapp.iptv.domain.model.CaptionMode
import com.yourapp.iptv.ui.screen.home.HomeCaptionHost
import com.yourapp.iptv.ui.state.SettingsUiState

@Composable
internal fun SettingsSection(category: SettingsCategory, labels: SettingsLabels, first: FocusRequester,
    account: SettingsUiState, reloadAccounts: () -> Unit, onAction: (SettingsAction) -> Unit) {
    fun text(ar: String, en: String, de: String) = labels.text(ar, en, de)
    when (category) {
        SettingsCategory.ACCOUNTS -> {
            val name = account.subscriptions.firstOrNull { it.id == account.activeSubscriptionId }?.name.orEmpty()
            if (account.isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (account.loadFailed) SettingsActionRow(stringResource(R.string.ui_settings_reload),
                stringResource(R.string.ui_settings_read_error), category = category, onClick = reloadAccounts)
            SettingsActionRow(stringResource(R.string.subscription_title),
                if (account.loadFailed) stringResource(R.string.ui_settings_read_error)
                else if (name.isNotBlank()) stringResource(R.string.subscription_current, name)
                else text("إضافة الاشتراكات وتعديلها", "Add and edit subscriptions", "Abos hinzufügen und bearbeiten"),
                category = category, modifier = Modifier.focusRequester(first).testTag("settings-open-subscriptions"), focusKey = "settings-open-subscriptions") { onAction(SettingsAction.SUBSCRIPTIONS) }
            SettingsActionRow(text("إدارة الملفات الشخصية", "Manage profiles", "Profile verwalten"),
                text("الأسماء والملفات العائلية", "Names and family profiles", "Namen und Familienprofile"), category = category,
                modifier = Modifier.testTag("settings-open-profiles"), focusKey = "settings-open-profiles") { onAction(SettingsAction.PROFILES) }
        }
        SettingsCategory.PLAYBACK -> {
            SettingsActionRow(stringResource(R.string.ui_player_title), stringResource(R.string.ui_player_hint), category = category,
                modifier = Modifier.focusRequester(first).testTag("settings-open-player"), focusKey = "settings-open-player") { onAction(SettingsAction.PLAYER) }
            StartupPlaybackSetting()
        }
        SettingsCategory.CAPTIONS -> HomeCaptionHost { state, open ->
            val value = if (!state.loaded || state.readFailed) text("غير متاح", "Unavailable", "Nicht verfügbar") else when (state.settings.mode) {
                CaptionMode.OFF -> stringResource(R.string.caption_off)
                CaptionMode.EMBEDDED -> stringResource(R.string.caption_embedded)
                CaptionMode.GENERATED -> state.settings.target.nativeName
            }
            SettingsActionRow(stringResource(R.string.caption_title),
                text("اللغة والطريقة والنماذج ومفاتيح الخدمة الخاصة بك", "Language, engine, models and your service credentials", "Sprache, Verfahren, Modelle und eigene Zugangsdaten"),
                value, category, Modifier.focusRequester(first).testTag("settings-open-captions"), focusKey = "settings-open-captions", onClick = open)
            Text(text("تطبّق الترجمة عند المشاهدة. فتح الإعدادات لا يشغّل الترجمة أو البث.",
                "Translation runs during playback. Opening settings starts neither translation nor a stream.",
                "Übersetzung läuft während der Wiedergabe. Einstellungen starten weder Übersetzung noch Stream."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SettingsCategory.PARENTAL -> SettingsActionRow(stringResource(R.string.parental_title),
            labels.description(category), category = category, modifier = Modifier.focusRequester(first).testTag("settings-open-parental"), focusKey = "settings-open-parental") { onAction(SettingsAction.PARENTAL) }
        SettingsCategory.DATA -> {
            SettingsActionRow(stringResource(R.string.backup_title),
                text("إنشاء نسخة مشفّرة أو استرجاع نسخة محفوظة", "Create an encrypted backup or restore a saved one", "Verschlüsselte Sicherung erstellen oder wiederherstellen"),
                category = category, modifier = Modifier.focusRequester(first).testTag("settings-open-backup"), focusKey = "settings-open-backup") { onAction(SettingsAction.BACKUP) }
            StorageManagementSection()
        }
        SettingsCategory.UPDATES -> {
            Box(Modifier.focusRequester(first)) { AppUpdateSection() }
            Text("MN StreamX · " + BuildConfig.VERSION_NAME, style = MaterialTheme.typography.labelMedium)
        }
        SettingsCategory.HELP -> {
            SettingsActionRow(stringResource(R.string.diagnostics_title), labels.description(category), category = category,
                modifier = Modifier.focusRequester(first).testTag("settings-open-diagnostics"), focusKey = "settings-open-diagnostics") { onAction(SettingsAction.DIAGNOSTICS) }
            Text(text("استخدم الأسهم للتنقل، وOK للاختيار، ورجوع للعودة إلى الأقسام. لا تبدأ الفحوص حتى تختار تشغيلها.",
                "Use arrows to move, OK to select and Back to return to categories. Checks start only when requested.",
                "Mit Pfeilen navigieren, mit OK auswählen und mit Zurück zu den Kategorien wechseln. Prüfungen starten nur auf Wunsch."),
                style = MaterialTheme.typography.bodyMedium)
        }
        SettingsCategory.APPEARANCE -> Unit
    }
}