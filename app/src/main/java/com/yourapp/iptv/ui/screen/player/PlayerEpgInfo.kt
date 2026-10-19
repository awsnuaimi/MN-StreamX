package com.yourapp.iptv.ui.screen.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yourapp.iptv.ui.state.PlayerEpgUiState
import com.yourapp.iptv.ui.theme.NeonBlue
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun PlayerEpgInfo(state: PlayerEpgUiState) {
    val current = state.schedule.current(state.nowMillis)
    val next = state.schedule.next(state.nowMillis)
    val zone = ZoneId.systemDefault()
    val locale = Locale.getDefault()
    val formatter = remember(zone, locale) { DateTimeFormatter.ofPattern("HH:mm", locale).withZone(zone) }
    fun time(value: Long) = formatter.format(Instant.ofEpochMilli(value))

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (current != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("الآن: ${current.title}", Modifier.weight(1f), color = Color.White,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                Text("${time(current.startTime)} – ${time(current.endTime)}", color = NeonBlue,
                    style = MaterialTheme.typography.bodyMedium)
            }
            LinearProgressIndicator(progress = { state.schedule.progress(state.nowMillis) },
                modifier = Modifier.fillMaxWidth().height(4.dp), color = NeonBlue,
                trackColor = Color.White.copy(alpha = 0.2f))
            current.description?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = Color(0xFFCCD9E8), maxLines = 2, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall)
            }
        } else {
            val text = when {
                state.isLoading -> "جارٍ تحميل معلومات البرامج…"
                state.errorMessage != null -> state.errorMessage
                !state.isSupported -> "لا تتوفر معلومات برامج لهذه القناة."
                state.schedule.isStale -> "لا توجد معلومات برامج محدثة حاليًا."
                else -> "لا تتوفر معلومات عن البرنامج الحالي."
            }
            Text(text, color = Color(0xFFCCD9E8), style = MaterialTheme.typography.bodyMedium)
        }
        if (next != null) {
            Text("التالي ${time(next.startTime)} – ${time(next.endTime)}: ${next.title}", color = Color.White,
                maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
        }
        if (state.schedule.isStale && (current != null || next != null)) {
            Text("جدول محفوظ مؤقتًا · تعذر تحديثه الآن", color = Color(0xFFFFD180),
                style = MaterialTheme.typography.bodySmall)
        }
    }
}