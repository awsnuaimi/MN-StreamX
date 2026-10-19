package com.yourapp.iptv.ui.screen.player.deck

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.ui.component.channel.ChannelLogo
import com.yourapp.iptv.ui.presentation.PlayerDeckWindow
import com.yourapp.iptv.ui.presentation.ProgramPresentation
import com.yourapp.iptv.ui.screen.player.playerEpgStatus
import com.yourapp.iptv.ui.state.PlayerEpgUiState
import com.yourapp.iptv.ui.theme.NeonBlue

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PlayerDeckInfo(channel: Channel, position: Int, total: Int, epg: PlayerEpgUiState,
    appearance: PlayerAppearance, technical: PlaybackTechnicalInfo, dense: Boolean) {
    val current = epg.schedule.current(epg.nowMillis)
    val next = epg.schedule.next(epg.nowMillis)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (appearance.showLogo) Box(Modifier.width(if (dense) 80.dp else 100.dp).height(if (dense) 48.dp else 64.dp)
                .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(10.dp)).padding(8.dp)) {
                ChannelLogo(channel.name, channel.logoUrl, Modifier.fillMaxSize())
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(current?.title ?: channel.name, Modifier.weight(1f), color = Color.White,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleLarge.copy(textDirection = TextDirection.Content))
                    Text("● مباشر", color = NeonBlue, style = MaterialTheme.typography.labelMedium)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    if (current != null) {
                        Text(ProgramPresentation.timeRange(current.startTime, current.endTime), color = Color(0xFFD3DEE9),
                            style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Ltr))
                        PlayerDeckWindow.remaining(current.endTime, epg.nowMillis)?.let {
                            Text(it, color = NeonBlue, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Text(channel.name, color = Color(0xFFD3DEE9), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content))
                    if (appearance.showCounter) Text(ProgramPresentation.playerPosition(position, total),
                        color = Color(0xFF96AFC5), style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Rtl))
                    technical.badges().forEach { label ->
                        Text(label, Modifier.background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(3.dp))
                            .padding(horizontal = 5.dp, vertical = 2.dp), color = Color(0xFFD3DEE9),
                            style = MaterialTheme.typography.labelSmall.copy(textDirection = TextDirection.Ltr))
                    }
                    if (appearance.showGroup) channel.group?.takeIf { it.isNotBlank() }?.let {
                        Text(it, color = Color(0xFF96AFC5), maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelSmall.copy(textDirection = TextDirection.Content))
                    }
                }
            }
        }
        if (current == null) Text(playerEpgStatus(epg), color = Color(0xFFBCCBD9),
            maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
        if (appearance.showDescription && !dense) current?.description?.takeIf { it.isNotBlank() }?.let {
            Text(it, color = Color(0xFFBCCBD9), maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Content))
        }
        if (appearance.showNext && next != null) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("التالي", color = NeonBlue, style = MaterialTheme.typography.bodySmall)
            Text(ProgramPresentation.timeRange(next.startTime, next.endTime), color = Color(0xFFBCCBD9),
                style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Ltr))
            Text(next.title, Modifier.weight(1f), color = Color(0xFFBCCBD9), maxLines = 1,
                overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Content))
        }
        if (epg.schedule.isStale) Text("جدول محفوظ مؤقتًا", color = Color(0xFFFFD180), style = MaterialTheme.typography.labelSmall)
        if (current != null) DeckProgramProgress(epg.schedule.progress(epg.nowMillis))
    }
}

@Composable
private fun DeckProgramProgress(value: Float) {
    val progress = value.coerceIn(0f, 1f)
    Canvas(Modifier.fillMaxWidth().height(5.dp).semantics {
        contentDescription = "تقدم البرنامج الحالي"
        progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f)
    }) {
        val y = size.height / 2
        drawLine(Color.White.copy(alpha = 0.22f), Offset(0f, y), Offset(size.width, y), strokeWidth = 2.dp.toPx())
        drawLine(NeonBlue, Offset(0f, y), Offset(size.width * progress, y), strokeWidth = 2.dp.toPx())
        drawCircle(NeonBlue, 2.5.dp.toPx(), Offset(size.width * progress, y))
    }
}