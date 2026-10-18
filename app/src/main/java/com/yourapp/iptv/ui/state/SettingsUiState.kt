package com.yourapp.iptv.ui.state

import com.yourapp.iptv.domain.model.AppSettings

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val loadFailed: Boolean = false,
    val errorMessage: String? = null
)