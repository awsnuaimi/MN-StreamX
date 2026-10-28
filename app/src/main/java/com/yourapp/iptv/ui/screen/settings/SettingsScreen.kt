package com.yourapp.iptv.ui.screen.settings

import androidx.compose.runtime.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.yourapp.iptv.ui.screen.parental.LocalParentalSession
import com.yourapp.iptv.viewmodel.HomeAppearanceSettingsViewModel
import com.yourapp.iptv.viewmodel.ProfilesViewModel
import com.yourapp.iptv.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onBack: (() -> Unit)? = null,
    onPlayerAppearance: () -> Unit = {},
    onHomeAppearance: () -> Unit = {},
    onSubscriptions: () -> Unit = {},
    onBackup: () -> Unit = {},
    onDiagnostics: () -> Unit = {},
    onParental: () -> Unit = {},
    onProfiles: () -> Unit = {},
    appearanceModel: HomeAppearanceSettingsViewModel = hiltViewModel(key = "settings-home-editor"),
    profilesModel: ProfilesViewModel = hiltViewModel()
) {
    val account by viewModel.uiState.collectAsStateWithLifecycle()
    val editor by appearanceModel.uiState.collectAsStateWithLifecycle()
    val profiles by profilesModel.people.collectAsStateWithLifecycle()
    val labels = SettingsLabels(LocalParentalSession.current.language)
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, appearanceModel, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val current = appearanceModel.uiState.value
                if (!current.dirty && !current.saving) appearanceModel.load()
                val settings = viewModel.uiState.value
                if (!settings.isLoading && !settings.isSaving) viewModel.loadSavedSettings()
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    // Each destination retains its original PIN gate and navigation owner.
    val navigate: (SettingsAction) -> Unit = { action -> when (action) {
        SettingsAction.SUBSCRIPTIONS -> onSubscriptions()
        SettingsAction.PROFILES -> onProfiles()
        SettingsAction.PLAYER -> onPlayerAppearance()
        SettingsAction.BACKUP -> onBackup()
        SettingsAction.DIAGNOSTICS -> onDiagnostics()
        SettingsAction.PARENTAL -> onParental()
        SettingsAction.BACK -> onBack?.invoke()
        SettingsAction.CAPTIONS -> Unit
    } }
    SettingsWorkspace(labels, profiles.catalog.active.name.takeIf { profiles.ready && !profiles.failed }.orEmpty(),
        editor.dirty, editor.saving, editor.saved, editor.error != null,
        canSave = !editor.loading && !editor.failed, onSave = appearanceModel::save,
        onReset = appearanceModel::reset, onDiscard = appearanceModel::discard, onAction = navigate) { category, first, action ->
        if (category == SettingsCategory.APPEARANCE) SettingsAppearancePanel(editor, labels, first, appearanceModel::update, appearanceModel::load, onPlayerText = { action(SettingsAction.PLAYER) })
        else SettingsSection(category, labels, first, account, viewModel::loadSavedSettings, action)
    }
}