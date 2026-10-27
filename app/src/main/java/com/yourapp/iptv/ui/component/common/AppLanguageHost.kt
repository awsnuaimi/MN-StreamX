package com.yourapp.iptv.ui.component.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yourapp.iptv.viewmodel.HomeAppearanceViewModel

@Composable
internal fun AppLanguageHost(viewModel: HomeAppearanceViewModel = hiltViewModel(),
    fallbackLanguage: com.yourapp.iptv.domain.model.HomeLanguage? = null, content: @Composable () -> Unit) {
    val appearance by viewModel.appearance.collectAsStateWithLifecycle()
    val language = appearance?.language ?: fallbackLanguage
    if (language == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    } else {
        // No language key around navigation: changing language keeps its saved state.
        LocalizedResources(language, content)
    }
}