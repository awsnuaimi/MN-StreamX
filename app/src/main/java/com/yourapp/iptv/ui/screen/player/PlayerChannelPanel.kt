package com.yourapp.iptv.ui.screen.player

import android.view.KeyEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yourapp.iptv.domain.model.Channel
import com.yourapp.iptv.ui.component.channel.ChannelLogo
import com.yourapp.iptv.ui.component.channel.FavoriteBadge
import com.yourapp.iptv.ui.component.common.AppButton
import com.yourapp.iptv.ui.theme.NeonBlue

@Composable
fun PlayerChannelPanel(
    channels: List<Channel>,
    currentIndex: Int,
    group: String?,
    query: String,
    favoritesOnly: Boolean,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    onRemoteKey: (KeyEvent, Boolean) -> Boolean
) {
    val selectedFocus = remember { FocusRequester() }
    val list = rememberLazyListState(initialFirstVisibleItemIndex = currentIndex.coerceAtLeast(0))
    LaunchedEffect(currentIndex, channels.size) {
        if (currentIndex in channels.indices) {
            list.scrollToItem(currentIndex)
            withFrameNanos { }
            runCatching { selectedFocus.requestFocus() }
        }
    }
    Dialog(onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f))
            .onPreviewKeyEvent { onRemoteKey(it.nativeKeyEvent, false) }) {
            Surface(
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight()
                    .widthIn(max = 460.dp).fillMaxWidth(),
                color = Color(0xFF101C32), contentColor = Color.White
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("قائمة القنوات · ${channels.size}", style = MaterialTheme.typography.titleLarge)
                            Text((if (favoritesOnly) "★ المفضلة · " else "") + (group ?: "كل التصنيفات"), maxLines = 1, overflow = TextOverflow.Ellipsis,
                                color = NeonBlue, style = MaterialTheme.typography.bodyMedium)
                        }
                        AppButton("إغلاق", onDismiss)
                    }
                    if (query.isNotBlank()) Text("البحث: $query", maxLines = 1,
                        overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                    if (channels.isEmpty()) Text("لا توجد قنوات في قائمة المفضلة الحالية. يستمر بث القناة حتى تختار غيرها.")
                    LazyColumn(state = list, modifier = Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(channels, key = { _, channel -> channel.id }) { index, channel ->
                            PlayerChannelRow(channel, index + 1, index == currentIndex,
                                onClick = { onSelect(channel.id) },
                                modifier = if (index == currentIndex) Modifier.focusRequester(selectedFocus)
                                    else Modifier)
                        }
                    }
                    Text("↑ ↓ للتنقل · OK لتشغيل القناة · رجوع لإغلاق القائمة",
                        style = MaterialTheme.typography.bodySmall, color = Color(0xFFBCCDE1))
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
    Surface(onClick = onClick,
        modifier = modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
        shape = MaterialTheme.shapes.medium,
        color = if (focused) Color(0xFF164362) else if (playing) Color(0xFF163148) else Color(0xFF17243D),
        contentColor = Color.White,
        border = BorderStroke(if (focused) 3.dp else 1.dp,
            if (focused || playing) NeonBlue else Color(0xFF2D3F58))) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(number.toString(), color = NeonBlue, style = MaterialTheme.typography.labelLarge)
            ChannelLogo(channel.name, channel.logoUrl, Modifier.size(40.dp))
            FavoriteBadge(channel.isFavorite)
            Column(Modifier.weight(1f)) {
                Text(channel.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium)
                if (playing) Text("تعمل الآن", color = NeonBlue, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}