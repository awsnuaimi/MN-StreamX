package com.yourapp.iptv.ui.screen.epg

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yourapp.iptv.domain.model.Channel
import com.yourapp.iptv.domain.model.EpgProgramSelection
import com.yourapp.iptv.ui.component.common.AppButton
import com.yourapp.iptv.ui.state.PlayerEpgUiState
import com.yourapp.iptv.ui.theme.NeonBlue
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun EpgProgramPane(
    channel: Channel, epg: PlayerEpgUiState, selectedKey: String?, request: EpgFocusRequest,
    onSelectProgram: (String) -> Unit, onPlay: () -> Unit, onFocused: () -> Unit,
    modifier: Modifier = Modifier
) {
    val programs = remember(epg.schedule.programs) {
        epg.schedule.programs.sortedBy { it.startTime }.distinctBy(EpgProgramSelection::key)
    }
    val selected = EpgProgramSelection.resolve(programs, selectedKey, epg.nowMillis)
    val resolvedKey = selected?.let(EpgProgramSelection::key)
    val missingSelection = selectedKey != null && programs.none { EpgProgramSelection.key(it) == selectedKey }
    val focus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }
    val list = key(channel.id) { rememberLazyListState() }
    val zone = ZoneId.systemDefault()
    val locale = Locale.getDefault()
    val formatter = remember(zone, locale) { DateTimeFormatter.ofPattern("dd/MM HH:mm", locale).withZone(zone) }
    LaunchedEffect(request.serial, missingSelection) {
        if (request.area == EpgFocusArea.PROGRAMS) {
            val index = programs.indexOfFirst { EpgProgramSelection.key(it) == resolvedKey }
            if (index >= 0 && list.layoutInfo.visibleItemsInfo.none { it.index == index }) list.scrollToItem(index)
            withFrameNanos { }
            runCatching { if (index >= 0) focus.requestFocus() else playFocus.requestFocus() }
        }
    }
    Surface(modifier, color = Color(0xFF101C32), contentColor = Color.White,
        shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxSize().padding(12.dp)
            .onFocusChanged { if (it.hasFocus) onFocused() }.focusGroup(),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(channel.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleLarge, color = NeonBlue)
                    Text("جدول المزوّد · التوقيت المحلي", style = MaterialTheme.typography.bodySmall)
                }
                AppButton("بث القناة", onPlay, Modifier.focusRequester(playFocus))
            }
            if (epg.schedule.isStale) {
                Text("جدول محفوظ مؤقتًا · تعذر تحديثه الآن", color = Color(0xFFFFD180),
                    style = MaterialTheme.typography.bodySmall)
            }
            if (programs.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (epg.isLoading) CircularProgressIndicator(Modifier.size(28.dp))
                        Text(when {
                            epg.isLoading -> "جارٍ تحميل جدول البرامج…"
                            epg.errorMessage != null -> epg.errorMessage
                            !epg.isSupported -> "دليل Xtream غير متاح لهذه القناة. يمكنك تشغيل بثها."
                            else -> "لا يوفّر المزوّد جدول برامج لهذه القناة حاليًا."
                        }, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            } else {
                LazyColumn(state = list, modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(programs, key = EpgProgramSelection::key) { program ->
                        val key = EpgProgramSelection.key(program)
                        EpgProgramCard(program, key == resolvedKey, epg.nowMillis, formatter,
                            onSelect = { onSelectProgram(key) }, onPlay = onPlay,
                            modifier = if (key == resolvedKey) Modifier.focusRequester(focus) else Modifier)
                    }
                }
            }
            Text("OK يشغّل البث المباشر للقناة", color = Color(0xFFBCCDE1),
                style = MaterialTheme.typography.labelSmall)
        }
    }
}