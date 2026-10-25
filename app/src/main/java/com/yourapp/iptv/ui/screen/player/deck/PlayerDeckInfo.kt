package com.yourapp.iptv.ui.screen.player.deck

import com.yourapp.iptv.ui.theme.AppThemeColors
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yourapp.iptv.R
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.ui.component.channel.ChannelLogo
import com.yourapp.iptv.ui.presentation.PlayerDeckWindow
import com.yourapp.iptv.ui.presentation.ProgramPresentation
import com.yourapp.iptv.ui.screen.player.playerEpgStatus
import com.yourapp.iptv.ui.state.PlayerEpgUiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PlayerDeckInfo(channel: Channel, position: Int, total: Int, epg: PlayerEpgUiState,
    appearance: PlayerAppearance, technical: PlaybackTechnicalInfo, dense: Boolean, preview: Boolean = false) {
    val current = epg.schedule.current(epg.nowMillis)
    val next = epg.schedule.next(epg.nowMillis)
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (appearance.showLogo) Box(Modifier.width(if (dense) 88.dp else 104.dp).height(56.dp)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), RoundedCornerShape(10.dp)).padding(8.dp)) {
                ChannelLogo(channel.name, channel.logoUrl, Modifier.fillMaxSize())
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(channel.name, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface, maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleLarge.copy(textDirection = TextDirection.Content))
                    Text(stringResource(if (preview) R.string.player_preview else R.string.player_live),
                        Modifier.background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(4.dp)).padding(horizontal = 7.dp, vertical = 3.dp),
                        color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                }
                if (current != null) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(current.title, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface, maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium.copy(textDirection = TextDirection.Content))
                    Text(ProgramPresentation.timeRange(current.startTime, current.endTime), color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Ltr))
                } else Text(playerEpgStatus(epg), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (appearance.showCounter) Text(
                        if (position in 1..total) stringResource(R.string.player_position, isolate(position), isolate(total))
                        else stringResource(R.string.player_outside_queue, isolate(total.coerceAtLeast(0))),
                        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    technical.badges().forEach { label ->
                        Text(label, Modifier.background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f), RoundedCornerShape(3.dp))
                            .padding(horizontal = 5.dp, vertical = 2.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall.copy(textDirection = TextDirection.Ltr))
                    }
                    current?.let { program -> PlayerDeckWindow.remainingMinutes(program.endTime, epg.nowMillis)?.let {
                        Text(stringResource(R.string.player_remaining, isolate(it)), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                    } }
                }
                if (appearance.showGroup) channel.group?.takeIf { it.isNotBlank() }?.let {
                    Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall.copy(textDirection = TextDirection.Content))
                }
            }
        }
        if (appearance.showDescription && !dense) current?.description?.takeIf { it.isNotBlank() }?.let {
            Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Content))
        }
        if (appearance.showNext && next != null) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.player_next), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            Text(ProgramPresentation.timeRange(next.startTime, next.endTime), color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Ltr))
            Text(next.title, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Content))
        }
        if (epg.schedule.isStale) Text(stringResource(R.string.player_cached), color = AppThemeColors.warning(MaterialTheme.colorScheme), style = MaterialTheme.typography.labelSmall)
        if (current != null) DeckProgramProgress(epg.schedule.progress(epg.nowMillis))
    }
}

private fun isolate(value: Number) = "\u2066$value\u2069"

@Composable
private fun DeckProgramProgress(value: Float) {
    val colors = MaterialTheme.colorScheme
    val progress = value.coerceIn(0f, 1f)
    val label = stringResource(R.string.player_progress)
    Canvas(Modifier.fillMaxWidth().height(5.dp).semantics {
        contentDescription = label
        progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f)
    }) {
        val y = size.height / 2
        drawLine(colors.onSurface.copy(alpha = 0.22f), Offset(0f, y), Offset(size.width, y), strokeWidth = 2.dp.toPx())
        drawLine(colors.primary, Offset(0f, y), Offset(size.width * progress, y), strokeWidth = 2.dp.toPx())
        drawCircle(colors.primary, 2.5.dp.toPx(), Offset(size.width * progress, y))
    }
}