package com.yourapp.iptv.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourapp.iptv.data.datastore.SettingsDataStore
import com.yourapp.iptv.util.validator.UrlValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    // حالة الشاشة
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState

    init {
        loadSavedSettings()
    }

    private fun loadSavedSettings() {
        viewModelScope.launch {
            val m3uUrl = settingsDataStore.getM3uUrl().first()
            val xtreamData = settingsDataStore.getXtreamData().first()
            val epgUrl = settingsDataStore.getEpgUrl().first()

            _uiState.value = _uiState.value.copy(
                m3uUrl = m3uUrl ?: "",
                xtreamServer = xtreamData.first ?: "",
                xtreamUsername = xtreamData.second ?: "",
                xtreamPassword = xtreamData.third ?: "",
                epgUrl = epgUrl ?: ""
            )
        }
    }

    fun updateM3uUrl(url: String) {
        _uiState.value = _uiState.value.copy(m3uUrl = url)
    }

    fun updateXtreamServer(server: String) {
        _uiState.value = _uiState.value.copy(xtreamServer = server)
    }

    fun updateXtreamUsername(username: String) {
        _uiState.value = _uiState.value.copy(xtreamUsername = username)
    }

    fun updateXtreamPassword(password: String) {
        _uiState.value = _uiState.value.copy(xtreamPassword = password)
    }

    fun updateEpgUrl(url: String) {
        _uiState.value = _uiState.value.copy(epgUrl = url)
    }

    fun saveSettings() {
        viewModelScope.launch {
            val state = _uiState.value

            // التحقق من صحة الروابط
            val isValidM3u = UrlValidator.isValidUrl(state.m3uUrl)
            val isValidEpg = state.epgUrl.isEmpty() || UrlValidator.isValidUrl(state.epgUrl)

            if (state.m3uUrl.isNotEmpty() && !isValidM3u) {
                _uiState.value = _uiState.value.copy(
                    isValid = false,
                    errorMessage = "رابط M3U غير صالح. يرجى التأكد من أن الرابط يبدأ بـ http:// أو https://"
                )
                return@launch
            }

            if (state.epgUrl.isNotEmpty() && !isValidEpg) {
                _uiState.value = _uiState.value.copy(
                    isValid = false,
                    errorMessage = "رابط EPG غير صالح. يرجى التأكد من أن الرابط يبدأ بـ http:// أو https://"
                )
                return@launch
            }

            // حفظ الإعدادات
            settingsDataStore.saveM3uUrl(state.m3uUrl)
            settingsDataStore.saveXtreamData(
                server = state.xtreamServer,
                username = state.xtreamUsername,
                password = state.xtreamPassword
            )
            settingsDataStore.saveEpgUrl(state.epgUrl)

            _uiState.value = _uiState.value.copy(
                isValid = true,
                isSaved = true,
                errorMessage = null
            )
        }
    }

    fun resetSaveStatus() {
        _uiState.value = _uiState.value.copy(isSaved = false)
    }
}

// حالة واجهة الإعدادات
data class SettingsUiState(
    val m3uUrl: String = "",
    val xtreamServer: String = "",
    val xtreamUsername: String = "",
    val xtreamPassword: String = "",
    val epgUrl: String = "",
    val isValid: Boolean = true,
    val isSaved: Boolean = false,
    val errorMessage: String? = null
)