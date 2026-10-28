package com.yourapp.iptv.ui.component.channel

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yourapp.iptv.domain.model.HomeLanguage
import com.yourapp.iptv.ui.presentation.ChannelProgramInfo
import com.yourapp.iptv.ui.presentation.ProgramPresentation

@Composable
internal fun ChannelProgramGuide(value: ChannelProgramInfo, language: HomeLanguage, channelId: String) {
    fun text(ar: String, en: String, de: String) = when (language) {
        HomeLanguage.ARABIC -> ar; HomeLanguage.ENGLISH -> en; HomeLanguage.GERMAN -> de
    }
    val current = value.current
    val next = value.next
    val currentLabel = text("الآن", "Now", "Jetzt")
    val nextLabel = text("التالي", "Next", "Danach")
    val stale = if (value.stale) text(" · بيانات محفوظة", " · Cached guide", " · Gespeicherter Programmführer") else ""
    val currentText = current?.let { "$currentLabel: ${it.title}" }
        ?: text("لا توجد بيانات للبرنامج الحالي", "No current programme data", "Keine aktuellen Programmdaten")
    val nextText = next?.let {
        val start = ProgramPresentation.timeRange(it.startTime, it.endTime).substringBefore(" – ")
        "$nextLabel $start · ${it.title}"
    } ?: "$nextLabel: —"
    Column(Modifier.fillMaxWidth().testTag("channel-guide-$channelId"), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(currentText, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.testTag("channel-guide-now-$channelId").semantics {
                contentDescription = currentText + (current?.let { " · " + ProgramPresentation.timeRange(it.startTime, it.endTime) } ?: "") + stale
            })
        Text(nextText, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("channel-guide-next-$channelId"))
        // Keep geometry stable when data is absent, expired or unsupported.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(current?.let { ProgramPresentation.timeRange(it.startTime, it.endTime) } ?: "—",
                maxLines = 1, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("channel-guide-time-$channelId"))
            Box(Modifier.weight(1f).height(3.dp)) {
                value.progress?.let { progress ->
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxSize()
                        .testTag("channel-guide-progress-$channelId"))
                }
            }
        }
    }
}