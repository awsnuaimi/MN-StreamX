package com.yourapp.iptv.ui.screen.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yourapp.iptv.domain.model.Channel
import com.yourapp.iptv.ui.state.HomeProgramUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun HomeProgramTimeline(channel: Channel, state: HomeProgramUiState,
    labels: HomeLabels, colors: HomeTvPalette, modifier: Modifier = Modifier) {
    if (!state.matches(channel)) return
    val current = state.schedule.current(state.nowMillis)
    val upcoming = state.schedule.upcoming(state.nowMillis, 3)
    if (current == null && upcoming.isEmpty()) return
    val format = remember(Locale.getDefault()) { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    Row(modifier.fillMaxWidth().testTag("home-epg-strip"), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        current?.let { program ->
            Box(Modifier.weight(2f).heightIn(min = 30.dp).clip(RoundedCornerShape(6.dp))
                .background(colors.outline.copy(alpha = 0.5f)).testTag("home-now-playing")) {
                Box(Modifier.matchParentSize().drawBehind {
                    drawRect(colors.accent.copy(alpha = 0.4f), size = Size(size.width * state.schedule.progress(state.nowMillis), size.height))
                })
                Text(labels.text("الآن", "Now", "Jetzt") + ": " + program.title,
                    Modifier.padding(horizontal = 9.dp, vertical = 7.dp), color = colors.text,
                    fontWeight = FontWeight.Bold, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        upcoming.forEach { program ->
            Box(Modifier.weight(1f).heightIn(min = 30.dp).clip(RoundedCornerShape(6.dp))
                .background(colors.outline.copy(alpha = 0.5f))) {
                Text("${format.format(Date(program.startTime))} · ${program.title}",
                    Modifier.padding(horizontal = 9.dp, vertical = 7.dp), color = colors.muted,
                    fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}