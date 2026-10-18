package com.yourapp.iptv.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourapp.iptv.domain.model.AppSettings
import com.yourapp.iptv.domain.repository.SettingsRepository
import com.yourapp.iptv.ui.state.SettingsUiState
import com.yourapp.iptv.util.validator.UrlValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {
    private val state = MutableStateFlow(SettingsUiState())
    val uiState = state.asStateFlow()

    init { loadSavedSettings() }

    fun loadSavedSettings() {
        if (state.value.isSaving) return
        state.update { it.copy(isLoading = true, loadFailed = false, errorMessage = null) }
        viewModelScope.launch {
            try {
                val settings = settingsRepository.observeSettings().first()
                state.value = SettingsUiState(settings = settings, isLoading = false)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                state.update {
                    it.copy(isLoading = false, loadFailed = true, errorMessage = "تعذر قراءة الإعدادات المحفوظة.")
                }
            }
        }
    }

    fun updateM3uUrl(value: String) = edit { copy(m3uUrl = value) }
    fun updateXtreamServer(value: String) = edit { copy(xtreamServer = value) }
    fun updateXtreamUsername(value: String) = edit { copy(xtreamUsername = value) }
    fun updateXtreamPassword(value: String) = edit { copy(xtreamPassword = value) }
    fun updateEpgUrl(value: String) = edit { copy(epgUrl = value) }

    private fun edit(change: AppSettings.() -> AppSettings) {
        if (state.value.isLoading || state.value.isSaving || state.value.loadFailed) return
        state.update { it.copy(settings = it.settings.change(), errorMessage = null, isSaved = false) }
    }

    fun saveSettings() {
        val current = state.value
        if (current.isLoading || current.isSaving || current.loadFailed) return
        val settings = current.settings.let {
            it.copy(
                m3uUrl = it.m3uUrl.trim(),
                xtreamServer = it.xtreamServer.trim(),
                xtreamUsername = it.xtreamUsername.trim(),
                epgUrl = it.epgUrl.trim()
            )
        }
        val error = validationError(settings)
        if (error != null) {
            state.update { it.copy(errorMessage = error, isSaved = false) }
            return
        }
        state.update { it.copy(isSaving = true, errorMessage = null, isSaved = false) }
        viewModelScope.launch {
            try {
                settingsRepository.saveSettings(settings)
                state.update { it.copy(settings = settings, isSaving = false, isSaved = true) }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                state.update { it.copy(isSaving = false, errorMessage = "تعذر حفظ الإعدادات. أعد المحاولة.") }
            }
        }
    }

    fun resetSaveStatus() { state.update { it.copy(isSaved = false) } }

    private fun validationError(settings: AppSettings): String? {
        val hasXtream = listOf(settings.xtreamServer, settings.xtreamUsername, settings.xtreamPassword)
            .any { it.isNotBlank() }
        return when {
            settings.m3uUrl.isBlank() && !hasXtream -> "أدخل رابط قائمة M3U أو بيانات المصدر."
            settings.m3uUrl.isNotBlank() && !UrlValidator.isValidUrl(settings.m3uUrl) ->
                "رابط M3U غير صالح. استخدم رابط http:// أو https:// كاملًا."
            hasXtream && (settings.xtreamServer.isBlank() || settings.xtreamUsername.isBlank() ||
                settings.xtreamPassword.isBlank()) -> "أكمل السيرفر واسم المستخدم وكلمة المرور."
            hasXtream && !UrlValidator.isValidUrl(settings.xtreamServer) -> "عنوان السيرفر غير صالح."
            settings.epgUrl.isNotBlank() && !UrlValidator.isValidUrl(settings.epgUrl) -> "رابط EPG غير صالح."
            else -> null
        }
    }
}