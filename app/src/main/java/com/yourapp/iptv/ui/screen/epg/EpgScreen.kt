package com.yourapp.iptv.ui.screen.epg

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yourapp.iptv.ui.component.common.AppButton
import com.yourapp.iptv.ui.state.PlayerEpgUiState
import com.yourapp.iptv.viewmodel.EpgViewModel
import com.yourapp.iptv.viewmodel.PlayerEpgViewModel

@Composable
fun EpgScreen(
    onBack: () -> Unit, onPlayChannel: (String, String?, String) -> Unit,
    returnedChannelId: String? = null, onReturnHandled: () -> Unit = {},
    viewModel: EpgViewModel = hiltViewModel(), epgViewModel: PlayerEpgViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val loadedEpg by epgViewModel.uiState.collectAsStateWithLifecycle()
    val channel = state.channel
    val epg = if (loadedEpg.channelId == channel?.id) loadedEpg
        else PlayerEpgUiState(channelId = channel?.id, isLoading = channel != null)
    var request by remember { mutableStateOf(EpgFocusRequest(EpgFocusArea.FILTERS)) }
    var restorePending by remember { mutableStateOf(true) }
    var mobilePane by rememberSaveable { mutableStateOf(EpgFocusArea.CHANNELS.name) }
    var lastBodyArea by rememberSaveable { mutableStateOf(EpgFocusArea.CHANNELS.name) }
    val searchFocus = remember { FocusRequester() }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    fun requestArea(area: EpgFocusArea) {
        val target = if (channel == null && area != EpgFocusArea.FILTERS) EpgFocusArea.FILTERS else area
        mobilePane = if (target == EpgFocusArea.FILTERS) EpgFocusArea.CHANNELS.name else target.name
        if (target != EpgFocusArea.FILTERS) lastBodyArea = target.name
        request = EpgFocusRequest(target, request.serial + 1)
    }
    fun markArea(area: EpgFocusArea) {
        if (!restorePending && area != EpgFocusArea.FILTERS) lastBodyArea = area.name
        if (request.area != area) request = request.copy(area = area)
    }
    fun play(id: String) {
        val latest = viewModel.uiState.value
        if (latest.queue.indexOf(id) < 0) return
        viewModel.selectChannel(id)
        onPlayChannel(id, latest.selectedGroup, latest.query)
    }
    fun paneKeys(area: EpgFocusArea) = Modifier.onPreviewKeyEvent {
        val event = it.nativeKeyEvent
        handleEpgPaneKey(event.keyCode, event.action, event.repeatCount, area, rtl, ::requestArea)
    }

    LaunchedEffect(channel) { epgViewModel.selectChannel(channel) }
    LaunchedEffect(returnedChannelId) {
        returnedChannelId?.let {
            viewModel.restoreChannel(it)
            restorePending = true
            onReturnHandled()
        }
    }
    LaunchedEffect(state.isLoading, state.isFiltering, state.selectedChannelId, returnedChannelId) {
        if (restorePending && !state.isLoading && !state.isFiltering && returnedChannelId == null) {
            val body = if (lastBodyArea == EpgFocusArea.PROGRAMS.name) EpgFocusArea.PROGRAMS else EpgFocusArea.CHANNELS
            requestArea(if (channel != null) body else EpgFocusArea.FILTERS)
            restorePending = false
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 680.dp
        fun back() {
            if (!wide && mobilePane == EpgFocusArea.PROGRAMS.name) requestArea(EpgFocusArea.CHANNELS)
            else onBack()
        }
        BackHandler { back() }
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            EpgFilters(state, request, searchFocus, viewModel::updateQuery, viewModel::selectGroup,
                onBack = ::back, onFocused = { markArea(EpgFocusArea.FILTERS) },
                compact = !wide && mobilePane == EpgFocusArea.PROGRAMS.name)
            when {
                state.isLoading || state.isFiltering -> Box(Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                state.errorMessage != null || channel == null -> Box(Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(state.errorMessage ?: "لا توجد قنوات مطابقة. غيّر البحث أو التصنيف.",
                            style = MaterialTheme.typography.bodyLarge)
                        if (state.errorMessage != null) AppButton("إعادة المحاولة", viewModel::reload)
                        else AppButton("عرض كل القنوات", viewModel::clearFilters)
                    }
                }
                wide -> Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    EpgChannelList(state, request, viewModel::selectChannel, ::play,
                        onFocused = { markArea(EpgFocusArea.CHANNELS) },
                        modifier = Modifier.weight(0.36f).fillMaxHeight().then(paneKeys(EpgFocusArea.CHANNELS)))
                    EpgProgramPane(channel, epg, state.selectedProgramKey, request,
                        viewModel::selectProgram, onPlay = { play(channel.id) },
                        onFocused = { markArea(EpgFocusArea.PROGRAMS) },
                        modifier = Modifier.weight(0.64f).fillMaxHeight().then(paneKeys(EpgFocusArea.PROGRAMS)))
                }
                else -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = mobilePane == EpgFocusArea.CHANNELS.name,
                            onClick = { requestArea(EpgFocusArea.CHANNELS) }, label = { Text("القنوات") })
                        FilterChip(selected = mobilePane == EpgFocusArea.PROGRAMS.name,
                            onClick = { requestArea(EpgFocusArea.PROGRAMS) }, label = { Text("البرامج") })
                    }
                    if (mobilePane == EpgFocusArea.CHANNELS.name) {
                        EpgChannelList(state, request, viewModel::selectChannel,
                            onActivate = { id -> viewModel.selectChannel(id); requestArea(EpgFocusArea.PROGRAMS) },
                            onFocused = { markArea(EpgFocusArea.CHANNELS) },
                            modifier = Modifier.weight(1f).fillMaxWidth().then(paneKeys(EpgFocusArea.CHANNELS)))
                    } else {
                        EpgProgramPane(channel, epg, state.selectedProgramKey, request, viewModel::selectProgram,
                            onPlay = { play(channel.id) }, onFocused = { markArea(EpgFocusArea.PROGRAMS) },
                            modifier = Modifier.weight(1f).fillMaxWidth().then(paneKeys(EpgFocusArea.PROGRAMS)))
                    }
                }
            }
            Text("↑ ↓ للتصفح · ← → للقنوات والبرامج · OK بث القناة · رجوع للخروج",
                style = MaterialTheme.typography.labelSmall)
        }
    }
}