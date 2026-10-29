package com.yourapp.iptv.ui.state

import com.yourapp.iptv.domain.model.LiveTvPreferences

data class LiveTvPreferencesState(val scope: String = "", val ready: Boolean = false,
    val saving: Boolean = false, val error: Boolean = false, val value: LiveTvPreferences = LiveTvPreferences())