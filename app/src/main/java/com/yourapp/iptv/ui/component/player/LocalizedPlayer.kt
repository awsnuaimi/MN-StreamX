package com.yourapp.iptv.ui.component.player

import androidx.compose.runtime.Composable
import com.yourapp.iptv.domain.model.HomeLanguage
import com.yourapp.iptv.ui.component.common.LocalizedResources

@Composable
internal fun LocalizedPlayer(language: HomeLanguage, content: @Composable () -> Unit) =
    LocalizedResources(language, content)