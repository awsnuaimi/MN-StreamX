package com.yourapp.iptv.ui.screen.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yourapp.iptv.domain.model.Channel
import com.yourapp.iptv.ui.state.HomeProgramUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Missing, expired or foreign-source guide data leaves the artwork/action usable. */
@Composable
internal fun HomeNowPlaying(
    channel: Channel,
    state: HomeProgramUiState,
    labels: HomeLabels,
    colors: HomeTvPalette,
    modifier: Modifier = Modifier
) {
    if (!state.matches(channel)) return
    val program = state.schedule.current(state.nowMillis) ?: return
    val time = remember(program.startTime, program.endTime, Locale.getDefault()) {
        val format = SimpleDateFormat("HH:mm", Locale.getDefault())
        "${format.format(Date(program.startTime))} – ${format.format(Date(program.endTime))}"
    }
    Column(modifier.testTag("home-now-playing"), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(labels.text("الآن: ${program.title}", "Now: ${program.title}", "Jetzt: ${program.title}"),
            color = colors.text, style = MaterialTheme.typography.bodySmall,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(time, color = colors.muted, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        LinearProgressIndicator(progress = { state.schedule.progress(state.nowMillis) },
            modifier = Modifier.fillMaxWidth().height(3.dp), color = colors.accent,
            trackColor = colors.outline)
    }
}