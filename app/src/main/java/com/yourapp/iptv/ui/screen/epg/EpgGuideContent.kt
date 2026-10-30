package com.yourapp.iptv.ui.screen.epg

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.yourapp.iptv.R
import com.yourapp.iptv.ui.component.channel.FavoriteFeedback
import com.yourapp.iptv.ui.component.common.AppButton
import com.yourapp.iptv.ui.component.common.FocusInputEpoch
import com.yourapp.iptv.ui.component.common.Resume
import com.yourapp.iptv.ui.component.common.rememberContextFocus
import com.yourapp.iptv.ui.state.*

internal const val EPG_FILTERS = "guide-filters"
internal const val EPG_GROUPS = "guide-groups"
internal const val EPG_CHANNELS = "guide-channels"
internal fun epgProgramContext(channelId: String?) = "guide-programs:$channelId"

internal data class EpgGuideActions(
    val back: () -> Unit, val query: (String) -> Unit, val group: (String?) -> Unit,
    val favorites: () -> Unit, val selectChannel: (String) -> Unit, val selectProgram: (String) -> Unit,
    val toggleFavorite: () -> Unit, val retryFavorites: () -> Unit, val reload: () -> Unit,
    val clearFilters: () -> Unit, val restoreChannel: (String) -> Unit, val returnHandled: () -> Unit,
    val play: (String) -> Unit
)

@Composable
internal fun EpgGuideContent(state: EpgUiState, favorites: FavoriteUiState, epg: PlayerEpgUiState,
    returnedChannelId: String?, actions: EpgGuideActions) {
    val focus = rememberContextFocus("guide")
    val channel = state.channel
    val programs = epgProgramContext(channel?.id)
    val ready = !state.isLoading && !state.isFiltering
    val channelKeys = remember(state.queue) { state.queue.channels.map { it.id } }
    var mobilePane by rememberSaveable { mutableStateOf(EpgFocusArea.CHANNELS.name) }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val contexts = setOf(EPG_FILTERS, EPG_GROUPS, EPG_CHANNELS, programs, "guide-status")
    focus.Resume(contexts, EPG_CHANNELS, state.selectedChannelId)
    focus.Maintain(EPG_CHANNELS, channelKeys, ready)

    fun requestArea(area: EpgFocusArea) {
        when {
            area == EpgFocusArea.FILTERS || channel == null -> {
                mobilePane = EpgFocusArea.CHANNELS.name
                focus.restore(setOf(EPG_FILTERS, EPG_GROUPS), EPG_FILTERS, "query")
            }
            area == EpgFocusArea.CHANNELS -> {
                mobilePane = area.name
                focus.request(EPG_CHANNELS)
            }
            else -> {
                mobilePane = area.name
                focus.request(programs)
            }
        }
    }
    val returnInputEpoch = remember(returnedChannelId) { FocusInputEpoch.value }
    LaunchedEffect(returnedChannelId) {
        returnedChannelId?.let { id ->
            actions.restoreChannel(id)
            if (returnInputEpoch == FocusInputEpoch.value) {
                if (mobilePane == EpgFocusArea.PROGRAMS.name) focus.request(epgProgramContext(id))
                else focus.request(EPG_CHANNELS, preferred = id)
            }
            actions.returnHandled()
        }
    }
    LaunchedEffect(focus.pending?.context) {
        when (focus.pending?.context) {
            programs -> mobilePane = EpgFocusArea.PROGRAMS.name
            EPG_CHANNELS, EPG_FILTERS, EPG_GROUPS -> mobilePane = EpgFocusArea.CHANNELS.name
            else -> Unit
        }
    }
    fun paneKeys(area: EpgFocusArea) = Modifier.onPreviewKeyEvent {
        val event = it.nativeKeyEvent
        handleEpgPaneKey(event.keyCode, event.action, event.repeatCount, area, rtl, ::requestArea)
    }
    if (ready && (channel == null || state.errorMessage != null)) {
        focus.Restore(EPG_CHANNELS, emptyList(), onEmpty = { focus.request("guide-status") })
        focus.Restore("guide-status", listOf("action"))
    }
    BoxWithConstraints(Modifier.fillMaxSize().then(focus.inputModifier()).testTag("epg-page")) {
        val wide = maxWidth >= 680.dp
        fun back() {
            if (!wide && mobilePane == EpgFocusArea.PROGRAMS.name) requestArea(EpgFocusArea.CHANNELS)
            else actions.back()
        }
        BackHandler { back() }
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            EpgFilters(state, focus, actions.query, { id ->
                focus.request(EPG_CHANNELS, preferred = state.selectedChannelId.takeIf { id == state.selectedGroup }, place = null)
                actions.group(id)
            }, onFavorites = {
                focus.request(EPG_CHANNELS, place = null)
                actions.favorites()
            }, onBack = ::back, compact = !wide && mobilePane == EpgFocusArea.PROGRAMS.name)
            FavoriteFeedback(favorites, actions.retryFavorites)
            when {
                !ready -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.errorMessage != null || channel == null -> Box(Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(when {
                            state.errorMessage != null -> R.string.ui_epg_read_error
                            state.favoritesOnly -> R.string.ui_epg_no_favorites
                            else -> R.string.ui_epg_no_channels
                        }))
                        AppButton(stringResource(if (state.errorMessage != null) R.string.ui_retry else R.string.ui_show_all_channels), {
                            focus.request(EPG_CHANNELS, place = null)
                            if (state.errorMessage != null) actions.reload() else actions.clearFilters()
                        }, focus.target("guide-status", "action", 0).testTag("epg-status-action"))
                    }
                }
                wide -> Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    EpgChannelList(state, focus, actions.selectChannel, actions.play,
                        Modifier.weight(0.36f).fillMaxHeight().onFocusChanged {
                            if (it.hasFocus) mobilePane = EpgFocusArea.CHANNELS.name
                        }.then(paneKeys(EpgFocusArea.CHANNELS)))
                    EpgProgramPane(channel, epg, state.selectedProgramKey, focus, actions.selectProgram,
                        { actions.play(channel.id) }, favorites, actions.toggleFavorite,
                        Modifier.weight(0.64f).fillMaxHeight().onFocusChanged {
                            if (it.hasFocus) mobilePane = EpgFocusArea.PROGRAMS.name
                        }.then(paneKeys(EpgFocusArea.PROGRAMS)))
                }
                else -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = mobilePane == EpgFocusArea.CHANNELS.name,
                            onClick = { requestArea(EpgFocusArea.CHANNELS) }, label = { Text(stringResource(R.string.ui_channels)) })
                        FilterChip(selected = mobilePane == EpgFocusArea.PROGRAMS.name,
                            onClick = { requestArea(EpgFocusArea.PROGRAMS) }, label = { Text(stringResource(R.string.ui_programs)) })
                    }
                    if (mobilePane == EpgFocusArea.CHANNELS.name) {
                        EpgChannelList(state, focus, actions.selectChannel, { id ->
                            actions.selectChannel(id)
                            mobilePane = EpgFocusArea.PROGRAMS.name
                            focus.request(epgProgramContext(id))
                        }, Modifier.weight(1f).fillMaxWidth().then(paneKeys(EpgFocusArea.CHANNELS)))
                    } else EpgProgramPane(channel, epg, state.selectedProgramKey, focus, actions.selectProgram,
                        { actions.play(channel.id) }, favorites, actions.toggleFavorite,
                        Modifier.weight(1f).fillMaxWidth().then(paneKeys(EpgFocusArea.PROGRAMS)))
                }
            }
            Text(stringResource(R.string.ui_epg_hint), style = MaterialTheme.typography.labelSmall)
        }
    }
}