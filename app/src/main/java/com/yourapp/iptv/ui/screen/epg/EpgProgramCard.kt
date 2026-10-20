package com.yourapp.iptv.ui.screen.epg

import androidx.compose.ui.res.stringResource
import com.yourapp.iptv.R
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yourapp.iptv.domain.model.Program
import com.yourapp.iptv.ui.theme.NeonBlue
import java.time.Instant
import java.time.format.DateTimeFormatter

@Composable
fun EpgProgramCard(
    program: Program, selected: Boolean, now: Long, formatter: DateTimeFormatter,
    onSelect: () -> Unit, onPlay: () -> Unit, modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    val live = program.startTime <= now && now < program.endTime
    val label = when { live -> stringResource(R.string.ui_epg_now); now >= program.endTime -> stringResource(R.string.ui_epg_ended); else -> stringResource(R.string.ui_epg_upcoming) }
    val times = "${formatter.format(Instant.ofEpochMilli(program.startTime))} – " +
        formatter.format(Instant.ofEpochMilli(program.endTime))
    Surface(onClick = { onSelect(); onPlay() },
        modifier = modifier.fillMaxWidth().onFocusChanged {
            focused = it.isFocused
            if (it.isFocused) onSelect()
        }, shape = MaterialTheme.shapes.medium, contentColor = Color.White,
        color = if (focused) Color(0xFF164362) else if (live) Color(0xFF153529) else Color(0xFF17243D),
        border = BorderStroke(if (focused) 3.dp else 1.dp,
            if (focused || selected) NeonBlue else Color(0xFF2D3F58))) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(label, color = if (live) Color(0xFF8FE4A0) else NeonBlue,
                    style = MaterialTheme.typography.labelMedium)
                Text(times, Modifier.weight(1f), color = NeonBlue,
                    style = MaterialTheme.typography.labelMedium.copy(textDirection = TextDirection.Ltr))
            }
            Text(program.title, maxLines = 2, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall)
            if (selected) {
                Text(program.description?.takeIf { it.isNotBlank() } ?: stringResource(R.string.player_no_description),
                    maxLines = 3, overflow = TextOverflow.Ellipsis, color = Color(0xFFCCD9E8),
                    style = MaterialTheme.typography.bodySmall)
            }
            if (live) {
                LinearProgressIndicator(progress = {
                    ((now - program.startTime).toDouble() / (program.endTime - program.startTime))
                        .toFloat().coerceIn(0f, 1f)
                }, modifier = Modifier.fillMaxWidth(), color = NeonBlue,
                    trackColor = Color.White.copy(alpha = 0.2f))
            }
        }
    }
}