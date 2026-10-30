package com.yourapp.iptv.ui.screen.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yourapp.iptv.ui.presentation.ChannelProgramInfo
import com.yourapp.iptv.ui.presentation.LiveChannelRow

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LiveTvChannelRow(row: LiveChannelRow, program: ChannelProgramInfo, badge: String?,
    active: Boolean, colors: HomeTvPalette, labels: HomeLabels, onClick: () -> Unit, onLongClick: () -> Unit,
    modifier: Modifier = Modifier, narrow: Boolean = false, showLogos: Boolean = true, preferredChannelId: String? = null) {
    val shape = if (active) HomeHeroShape else RoundedCornerShape(10.dp)
    Row(modifier.fillMaxWidth().clip(shape).background(if (active) colors.focused else colors.surface)
        .then(if (active) Modifier.border(1.5.dp, colors.accent, shape) else Modifier)
        .liveTvRowInput(onClick, onLongClick)
        .focusProperties { canFocus = true }
        .combinedClickable(interactionSource = remember { MutableInteractionSource() }, indication = null,
            role = Role.Button, onLongClick = onLongClick, onClick = onClick)
        .semantics { selected = active }
        .padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp)) {
        if (row.number > 0) Text(row.number.toString(), color = colors.muted, fontSize = 10.sp,
            fontWeight = FontWeight.Medium, maxLines = 1, modifier = Modifier.widthIn(min = 16.dp)
                .testTag("live-number-${row.key}"),
            style = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr))
        LiveTvChannelLogo(row, preferredChannelId, colors, showLogos)
        Column(Modifier.weight(if (narrow) 0.55f else 0.76f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(row.title, color = colors.text, fontSize = 14.sp, lineHeight = 16.sp,
                fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = LocalTextStyle.current.copy(textDirection = TextDirection.Content))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.qualities.takeIf { it.isNotEmpty() }?.joinToString(" · ")?.let { LiveTvBadge(it, colors, false) }
                badge?.takeIf { "→" in it }?.let { LiveTvBadge(it, colors, true) }
                if (row.favorite) Text("★", color = colors.accent, fontSize = 10.sp)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            if (program.current != null) {
                Text(program.current.title, color = colors.text, fontSize = 12.sp, lineHeight = 15.sp,
                    fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("live-now-${row.key}"))
                program.progress?.let { LiveTvProgress(it, colors, Modifier.fillMaxWidth().testTag("live-progress-${row.key}")) }
            } else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(6.dp).clip(RoundedCornerShape(50)).background(colors.liveBadge))
                Text(labels.text("مباشر", "Live", "Live"), color = colors.muted, fontSize = 12.sp)
            }
        }
        if (!narrow) Box(Modifier.weight(0.68f)) {
            program.next?.let {
                Text("${liveTvTime(it.startTime)} · ${it.title}", color = colors.muted, fontSize = 11.sp,
                    lineHeight = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("live-next-${row.key}"))
            }
        }
    }
}

@Composable
internal fun LiveTvBadge(text: String, colors: HomeTvPalette, translated: Boolean) {
    Box(Modifier.clip(RoundedCornerShape(3.dp)).background(if (translated) colors.accent else colors.focused)
        .padding(horizontal = 4.dp, vertical = 1.dp)) {
        Text(text, color = if (translated) liveTvOnAccent(colors) else colors.text, fontSize = 9.sp,
            lineHeight = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun LiveTvProgress(progress: Float, colors: HomeTvPalette, modifier: Modifier = Modifier) {
    Box(modifier.height(3.dp).clip(RoundedCornerShape(50)).background(colors.outline)
        .semantics { progressBarRangeInfo = ProgressBarRangeInfo(progress.coerceIn(0f, 1f), 0f..1f) }) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(progress.coerceIn(0f, 1f)).background(colors.accent))
    }
}