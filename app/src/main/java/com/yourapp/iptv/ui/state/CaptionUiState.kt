package com.yourapp.iptv.ui.state

import com.yourapp.iptv.domain.model.CaptionSettings

data class CaptionUiState(val settings: CaptionSettings = CaptionSettings(), val loaded: Boolean = false,
    val saving: Boolean = false, val readFailed: Boolean = false, val saveFailed: Boolean = false)