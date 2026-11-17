@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package mnstudio.apps.mnstreamx.ui.screen.player

import android.view.KeyEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import mnstudio.apps.mnstreamx.R
import mnstudio.apps.mnstreamx.domain.model.Channel
import mnstudio.apps.mnstreamx.domain.organization.OrganizationTree
import mnstudio.apps.mnstreamx.ui.component.channel.ChannelLogo
import mnstudio.apps.mnstreamx.ui.component.channel.FavoriteBadge
import mnstudio.apps.mnstreamx.ui.component.common.AppButton
import mnstudio.apps.mnstreamx.ui.component.common.FocusInputEpoch
import mnstudio.apps.mnstreamx.ui.component.common.Resume
import mnstudio.apps.mnstreamx.ui.component.common.tvFocusIndicator
import mnstudio.apps.mnstreamx.ui.presentation.LiveCountry
import mnstudio.apps.mnstreamx.ui.presentation.LiveCountryBrowser
import mnstudio.apps.mnstreamx.ui.state.PlayerEpgUiState

private enum class BrowserColumn { COUNTRIES, GROUPS, CHANNELS, CONTROLS }

@Composable
fun PlayerChannelPanel(
    channels: List<Channel>,
    currentIndex: Int,
    favoritesOnly: Boolean,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    onRemoteKey: (KeyEvent, Boolean) -> Boolean,
    loading: Boolean = false,
    organization: OrganizationTree? = null,
    startWithCountries: Boolean = false,
    onMore: (() -> Unit)? = null,
    focusMore: Boolean = false,
    onFocusChannel: (String) -> Unit = {},
    previewEpg: PlayerEpgUiState? = null,
    visible: ((Channel) -> Boolean)? = null
) {
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    val unclassified = stringResource(R.string.org_folder_unclassified)
    val tree = organization ?: remember { OrganizationTree() }
    val input = remember(channels, tree, locale, unclassified, favoritesOnly, visible, loading) { Any() }
    val result by produceState<Pair<Any, List<LiveCountry>>?>(null, input) {
        if (!loading) {
            val projected = withContext(Dispatchers.Default) {
                LiveCountryBrowser.project(channels, tree, locale, unclassified, visible = {
                    (!favoritesOnly || it.isFavorite) && (visible?.invoke(it) ?: true)
                }) { ensureActive() }
            }
            value = input to projected
        }
    }
    val countries = result?.takeIf { it.first === input }?.second
    val currentId = channels.getOrNull(currentIndex)?.id
    val focus = rememberPlayerFocus()
    val prefix = "player-channels:$currentId"
    val countryContext = "$prefix:countries"
    val controlContext = "$prefix:controls"
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    var column by remember { mutableStateOf(BrowserColumn.CONTROLS) }
    var selectedCountry by remember { mutableStateOf<String?>(null) }
    var selectedGroup by remember { mutableStateOf<String?>(null) }
    var groupTarget by remember { mutableStateOf<String?>(null) }
    var focusedCountry by remember { mutableStateOf<String?>(null) }
    var channelTarget by remember { mutableStateOf<String?>(null) }
    val initialEpoch = remember { FocusInputEpoch.value }
    var initialized by remember { mutableStateOf(false) }
    val country = countries?.firstOrNull { it.code == selectedCountry }
    val groups = country?.groups.orEmpty()
    val activeGroup = groups.firstOrNull { it.key == selectedGroup }
    val groupContext = "$prefix:groups:$selectedCountry"
    val channelContext = "$groupContext:channels:$selectedGroup"
    val rows = activeGroup?.channels.orEmpty()
    val countryKeys = remember(countries) { countries.orEmpty().map { it.code } }
    val groupKeys = remember(groups) { groups.map { it.key } }
    val channelKeys = remember(rows) { rows.map { it.id } }
    val positions = remember(channelKeys) { channelKeys.withIndex().associate { it.value to it.index } }
    val controls = remember(onMore != null) { if (onMore == null) listOf("close") else listOf("more", "close") }

    fun openCountry(code: String) {
        val target = countries?.firstOrNull { it.code == code } ?: return
        selectedCountry = code
        val context = "$prefix:groups:$code"
        val groupKey = focus.bookmarks.get(context)?.key?.takeIf { key -> target.groups.any { it.key == key } }
            ?: target.groups.firstOrNull()?.key
        selectedGroup = groupKey
        groupTarget = groupKey
        channelTarget = null
        if (groupKey != null) focus.request(context, groupKey)
    }

    LaunchedEffect(Unit) {
        if (countries == null) focus.request(controlContext, "close")
    }
    LaunchedEffect(countries) {
        val available = countries ?: return@LaunchedEffect
        if (initialized && (selectedCountry == null || country != null && activeGroup != null)) return@LaunchedEffect
        val initial = !initialized
        initialized = true
        val safeToFocus = !initial || initialEpoch == FocusInputEpoch.value
        if (available.isEmpty()) {
            selectedCountry = null
            selectedGroup = null
            if (safeToFocus) focus.request(controlContext, if (focusMore) "more" else "close")
        } else if (startWithCountries || !initial) {
            selectedCountry = null
            selectedGroup = null
            if (safeToFocus) focus.request(countryContext, available.first().code)
        } else {
            val playingCountry = available.firstOrNull { entry -> entry.groups.any { group ->
                group.channels.any { it.id == currentId }
            } }
            val target = playingCountry ?: available.first()
            val preferred = target.groups.firstOrNull { group -> group.channels.any { it.id == currentId } }
                ?: target.groups.first()
            selectedCountry = target.code
            selectedGroup = preferred.key
            val context = "$prefix:groups:${target.code}:channels:${preferred.key}"
            val bookmarked = focus.bookmarks.get(context)?.key
            channelTarget = preferred.channels.firstOrNull { it.id == bookmarked }?.id
                ?: preferred.channels.firstOrNull { it.id == currentId }?.id ?: preferred.channels.first().id
            if (safeToFocus) {
                if (focusMore && onMore != null) focus.request(controlContext, "more")
                else focus.request(context, channelTarget)
            }
        }
    }

    val countryList = rememberLazyListState()
    val groupList = key(selectedCountry) { rememberLazyListState() }
    val channelList = key(selectedCountry, selectedGroup) { rememberLazyListState() }
    val towardChannels = if (rtl) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
    val towardGroups = if (rtl) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT

    fun handleKey(event: KeyEvent): Boolean {
        val code = event.keyCode
        if (code == KeyEvent.KEYCODE_CHANNEL_UP || code == KeyEvent.KEYCODE_CHANNEL_DOWN) {
            if (event.action == KeyEvent.ACTION_DOWN && !event.isCanceled && column == BrowserColumn.CHANNELS) {
                val at = positions[channelTarget] ?: 0
                val step = if (code == KeyEvent.KEYCODE_CHANNEL_UP) 1 else -1
                channelKeys.getOrNull(at + step)?.let { next ->
                    channelTarget = next
                    focus.request(channelContext, next)
                } ?: channelTarget?.let { focus.request(channelContext, it) }
            }
            return true
        }
        val down = event.action == KeyEvent.ACTION_DOWN && !event.isCanceled
        return when (code) {
            towardChannels -> when (column) {
                BrowserColumn.GROUPS -> {
                    if (down && rows.isNotEmpty()) focus.request(channelContext, channelTarget
                        ?.takeIf { positions.containsKey(it) } ?: rows.first().id)
                    true
                }
                BrowserColumn.CHANNELS -> true
                else -> false
            }
            towardGroups -> when (column) {
                BrowserColumn.CHANNELS -> {
                    if (down) focus.request(groupContext, selectedGroup)
                    true
                }
                BrowserColumn.GROUPS -> true
                else -> false
            }
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> when (column) {
                BrowserColumn.CHANNELS -> true
                BrowserColumn.COUNTRIES -> if (code == KeyEvent.KEYCODE_DPAD_DOWN) {
                    if (down) focusedCountry?.let(::openCountry)
                    true
                } else false
                BrowserColumn.GROUPS -> {
                    if (down) {
                        val at = groupKeys.indexOf(groupTarget ?: selectedGroup)
                        val step = if (code == KeyEvent.KEYCODE_DPAD_DOWN) 1 else -1
                        val next = groupKeys.getOrNull(at + step)
                        if (next != null) {
                            groupTarget = next
                            focus.request(groupContext, next)
                        } else if (step < 0) {
                            groupTarget = null
                            focus.request(countryContext, selectedCountry)
                        } else groupTarget?.let { focus.request(groupContext, it) }
                    }
                    true
                }
                BrowserColumn.CONTROLS -> false
            }
            else -> onRemoteKey(event, false)
        }
    }

    LaunchedEffect(focusMore, countries) {
        if (focusMore && onMore != null && countries != null) focus.request(controlContext, "more")
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val windowFocused = LocalWindowInfo.current.isWindowFocused
        focus.Resume(setOf(countryContext, groupContext, channelContext, controlContext), controlContext, "close", initial = false)
        focus.Restore(countryContext, countryKeys, ready = windowFocused, scrollTo = { countryList.scrollToItem(it) })
        focus.Restore(groupContext, groupKeys, ready = windowFocused, scrollTo = {
            if (groupList.layoutInfo.visibleItemsInfo.none { item -> item.index == it }) groupList.scrollToItem(it)
        })
        focus.Restore(channelContext, channelKeys, ready = windowFocused, scrollTo = {
            if (channelList.layoutInfo.visibleItemsInfo.none { item -> item.index == it }) channelList.scrollToItem(it)
        })
        focus.Restore(controlContext, controls, ready = windowFocused)
        Box(Modifier.fillMaxSize().padding(horizontal = 24.dp)
            .then(focus.inputModifier()).onPreviewKeyEvent { handleKey(it.nativeKeyEvent) }) {
            Surface(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp)
                    .widthIn(max = 920.dp).fillMaxWidth().fillMaxHeight(0.91f),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.player_channels), Modifier.weight(1f),
                            style = MaterialTheme.typography.titleLarge)
                        if (onMore != null) AppButton(stringResource(R.string.player_more), onMore,
                            focus.target(controlContext, "more", 0).onFocusChanged {
                                if (it.isFocused) column = BrowserColumn.CONTROLS
                            }.testTag("player-more"))
                        AppButton(stringResource(R.string.player_close), onDismiss,
                            focus.target(controlContext, "close", controls.lastIndex).onFocusChanged {
                                if (it.isFocused) column = BrowserColumn.CONTROLS
                            }.testTag("player-channels-close"))
                    }
                    if (countries == null) {
                        CircularProgressIndicator(Modifier.testTag("player-channel-loading"))
                    } else if (countries.isEmpty()) {
                        Text(stringResource(R.string.player_empty_list))
                    } else {
                        LazyRow(state = countryList, modifier = Modifier.fillMaxWidth().focusGroup().testTag("live-countries"),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            itemsIndexed(countries, key = { _, item -> item.code }) { index, item ->
                                AppButton(item.name, { openCountry(item.code) },
                                    focus.target(countryContext, item.code, index).onFocusChanged {
                                        if (it.isFocused) {
                                            column = BrowserColumn.COUNTRIES
                                            focusedCountry = item.code
                                        }
                                    }.semantics { selected = item.code == selectedCountry }.testTag("live-country-${item.code}"))
                            }
                        }
                        if (country != null && groups.isNotEmpty()) {
                            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                LazyColumn(state = groupList, modifier = Modifier.weight(0.8f).fillMaxHeight()
                                    .focusGroup().testTag("live-country-groups"),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    itemsIndexed(groups, key = { _, item -> item.key }) { index, item ->
                                        AppButton(item.name ?: stringResource(R.string.org_folder_other),
                                            {
                                                if (selectedGroup != item.key) channelTarget = null
                                                selectedGroup = item.key
                                                groupTarget = item.key
                                                val context = "$groupContext:channels:${item.key}"
                                                focus.request(context, channelTarget ?: item.channels.first().id)
                                            },
                                            focus.target(groupContext, item.key, index).fillMaxWidth().onFocusChanged {
                                                if (it.isFocused) {
                                                    column = BrowserColumn.GROUPS
                                                    if (selectedGroup != item.key) channelTarget = null
                                                    selectedGroup = item.key
                                                    val pending = focus.pending?.preferred
                                                    if (pending == null || pending == item.key) groupTarget = item.key
                                                }
                                            }.semantics { selected = item.key == selectedGroup }.testTag("live-group-${item.key}"))
                                    }
                                }
                                LazyColumn(state = channelList, modifier = Modifier.weight(1.2f).fillMaxHeight()
                                    .focusGroup().testTag("player-channel-list"),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    itemsIndexed(rows, key = { _, channel -> channel.id }) { index, channel ->
                                        PlayerChannelRow(channel, index + 1, channel.id == currentId,
                                            onClick = {
                                                focus.record(channelContext, channel.id, index)
                                                onSelect(channel.id)
                                            },
                                            modifier = focus.target(channelContext, channel.id, index).onFocusChanged {
                                                if (it.isFocused) {
                                                    column = BrowserColumn.CHANNELS
                                                    val pending = focus.pending?.preferred
                                                    if (pending == null || pending == channel.id) channelTarget = channel.id
                                                    onFocusChannel(channel.id)
                                                }
                                            }.testTag("player-channel-${channel.id}"))
                                    }
                                }
                            }
                            if (column == BrowserColumn.CHANNELS) {
                                val program = previewEpg?.takeIf { it.channelId == channelTarget }
                                    ?.let { it.schedule.current(it.nowMillis) }
                                if (program != null) Text(program.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    Text(stringResource(R.string.live_country_browser_hint),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun PlayerChannelRow(
    channel: Channel, number: Int, playing: Boolean, onClick: () -> Unit, modifier: Modifier
) {
    var focused by remember { mutableStateOf(false) }
    val playingLabel = stringResource(R.string.player_playing)
    Surface(onClick = onClick,
        modifier = modifier.fillMaxWidth().tvFocusIndicator(MaterialTheme.shapes.medium)
            .semantics { if (playing) stateDescription = playingLabel }
            .onFocusChanged { focused = it.isFocused },
        shape = MaterialTheme.shapes.medium,
        color = if (focused) MaterialTheme.colorScheme.primaryContainer else if (playing) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(if (focused) 3.dp else if (playing) 2.dp else 1.dp,
            when {
                focused -> MaterialTheme.colorScheme.primary
                playing -> MaterialTheme.colorScheme.secondary
                else -> MaterialTheme.colorScheme.outline
            })) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(number.toString(), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
            ChannelLogo(channel.name, channel.logoUrl, Modifier.size(40.dp))
            FavoriteBadge(channel.isFavorite)
            Column(Modifier.weight(1f)) {
                Text(channel.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium)
                if (playing) Text(stringResource(R.string.player_playing), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}