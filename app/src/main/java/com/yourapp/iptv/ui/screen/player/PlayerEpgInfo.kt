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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yourapp.iptv.domain.model.Program
import com.yourapp.iptv.ui.presentation.ProgramPresentation
import com.yourapp.iptv.ui.state.PlayerEpgUiState
import com.yourapp.iptv.ui.theme.NeonBlue

@Composable
fun PlayerEpgInfo(state: PlayerEpgUiState, showNext: Boolean = true, showDescription: Boolean = false) {
    val current = state.schedule.current(state.nowMillis)
    val next = state.schedule.next(state.nowMillis)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (current != null) {
            ProgramInfoLine("الآن", current, primary = true)
            LinearProgressIndicator(progress = { state.schedule.progress(state.nowMillis) },
                modifier = Modifier.fillMaxWidth().height(3.dp), color = NeonBlue,
                trackColor = Color.White.copy(alpha = 0.2f))
            if (showDescription) current.description?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = Color(0xFFCCD9E8), maxLines = 2, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content))
            }
        } else Text(playerEpgStatus(state), color = Color(0xFFCCD9E8), style = MaterialTheme.typography.bodyMedium)
        if (showNext && next != null) ProgramInfoLine("التالي", next, primary = false)
        if (state.schedule.isStale && (current != null || next != null)) Text("جدول محفوظ مؤقتًا · تعذر تحديثه الآن",
            color = Color(0xFFFFD180), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ProgramInfoLine(label: String, program: Program, primary: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, color = if (primary) NeonBlue else Color(0xFFBCCDE1), style = MaterialTheme.typography.bodyMedium)
        Text(program.title, Modifier.weight(1f), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = (if (primary) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium)
                .copy(textDirection = TextDirection.Content))
        Text(ProgramPresentation.timeRange(program.startTime, program.endTime), color = NeonBlue,
            style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Ltr))
    }
}

internal fun playerEpgStatus(state: PlayerEpgUiState): String = when {
    state.isLoading -> "جارٍ تحميل معلومات البرامج…"
    state.errorMessage != null -> state.errorMessage
    !state.isSupported -> "لا تتوفر معلومات برامج لهذه القناة."
    state.schedule.isStale -> "لا توجد معلومات برامج محدثة حاليًا."
    else -> "لا تتوفر معلومات عن البرنامج الحالي."
}