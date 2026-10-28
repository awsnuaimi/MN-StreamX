package com.yourapp.iptv.ui.screen.home

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.ui.presentation.ChannelProgramInfo
import com.yourapp.iptv.ui.screen.parental.LocalParentalSession
import com.yourapp.iptv.ui.tv.TvChannelGrid
import com.yourapp.iptv.viewmodel.HomeGuideViewModel

/** Mounted only for the live/favourite browser; never shares Home's binding or starts a player. */
@Composable
internal fun HomeLiveChannelGrid(channels: List<Channel>, selectedChannelId: String?, onChannelClick: (Channel) -> Unit,
    modifier: Modifier, columns: Int, showGroup: Boolean, restore: HomeGridRestore?,
    layout: HomeChannelLayout, showLogos: Boolean, language: HomeLanguage, owner: String,
    onBrowsePosition: (String, HomeGridPosition) -> Unit, onRestoreHandled: (Long) -> Unit,
    model: HomeGuideViewModel = hiltViewModel(key = "live-channel-guide")) {
    val guide by model.uiState.collectAsStateWithLifecycle()
    val access = LocalParentalSession.current.state
    DisposableEffect(model) { onDispose { model.bind(emptyList()) } }
    TvChannelGrid(channels, selectedChannelId, onChannelClick, modifier, columns, showGroup,
        restore, onBrowsePosition, onRestoreHandled, layout, showLogos,
        showGuide = true, language = language,
        program = { channel -> ChannelProgramInfo.from(channel, guide.forChannel(channel, owner, access.epoch)) },
        onVisibleChannels = { model.bind(it, HomeGuideViewModel.MAX_BROWSER_CHANNELS) })
}