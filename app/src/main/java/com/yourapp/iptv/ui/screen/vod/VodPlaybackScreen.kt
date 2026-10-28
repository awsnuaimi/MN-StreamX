package com.yourapp.iptv.ui.screen.vod

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yourapp.iptv.domain.model.HomeLanguage
import com.yourapp.iptv.player.vod.VodTrackPreferences
import com.yourapp.iptv.ui.component.common.AppButton
import com.yourapp.iptv.ui.component.common.LocalizedResources
import com.yourapp.iptv.viewmodel.*

@Composable
fun VodPlaybackScreen(onBack: () -> Unit, model: VodPlaybackViewModel = hiltViewModel(),
    appearance: HomeAppearanceViewModel = hiltViewModel(),
    playerAppearance: PlayerAppearanceViewModel = hiltViewModel()) {
    val state by model.state.collectAsStateWithLifecycle()
    val playerStyle by playerAppearance.appearance.collectAsStateWithLifecycle()
    val autoNext by model.autoNext.collectAsStateWithLifecycle()
    val style by appearance.appearance.collectAsStateWithLifecycle()
    val language = style?.language ?: HomeLanguage.ARABIC
    val labels = remember(language) { VodPlaybackLabels(language) }
    val preferences = remember { VodTrackPreferences() }
    LocalizedResources(language) {
        when (val value = state) {
            VodPlaybackState.Loading -> {
                BackHandler(onBack = onBack)
                Box(Modifier.fillMaxSize().background(Color.Black),contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
            VodPlaybackState.Error -> { BackHandler(onBack = onBack); VodPlaybackFailure(labels.unavailable(),labels,onBack) }
            is VodPlaybackState.Ready -> key(value.sourceKey,value.kind,value.id) {
                VodPlayerHost(value,model,labels,preferences,autoNext,onBack,playerStyle)
            }
        }
    }
}

@Composable
internal fun VodPlaybackFailure(message: String, labels: VodPlaybackLabels, onBack: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black),contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 600.dp).padding(24.dp),horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(message,color = Color.White)
            AppButton(labels.back(),onBack)
        }
    }
}