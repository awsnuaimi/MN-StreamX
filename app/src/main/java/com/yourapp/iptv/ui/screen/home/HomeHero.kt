package com.yourapp.iptv.ui.screen.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
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

/** Cinematic Home artwork is a saved frame, never an extra player connection. */
@Composable
internal fun HomeHero(channel: Channel?, labels: HomeLabels, colors: HomeTvPalette,
    state: HomeProgramUiState, showLogos: Boolean, onWatch: (Channel) -> Unit,
    modifier: Modifier = Modifier, focus: FocusRequester? = null, languageBadge: String? = null,
    qualityBadge: String? = null) {
    val frame = rememberChannelFrame(channel)
    val guide = state.takeIf { channel != null && it.matches(channel) }
    val current = guide?.schedule?.current(guide.nowMillis)
    val scale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    Surface(modifier.fillMaxWidth().testTag("home-hero"), shape = HomeHeroShape,
        color = colors.surface, border = BorderStroke(1.5.dp, colors.accent)) {
        Box(Modifier.fillMaxWidth().heightIn(min = (224f * scale).dp)) {
            HomeImageBackdrop(channel?.let {
                if (showLogos) HomeArtworkSource.of(it) else HomeArtworkSource.fallback(it.name)
            } ?: HomeArtworkSource.Empty, colors, Modifier.matchParentSize(), large = true, frame = frame)
            if (channel != null) Surface(Modifier.align(Alignment.TopEnd).padding(12.dp),
                color = colors.liveBadge, shape = RoundedCornerShape(4.dp)) {
                Text("LIVE", Modifier.padding(horizontal = 7.dp, vertical = 2.dp), color = androidx.compose.ui.graphics.Color.White,
                    fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.fillMaxWidth().heightIn(min = (224f * scale).dp).padding(horizontal = 24.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.fillMaxWidth(if (scale > 1.5f) 0.75f else 0.56f).testTag("hero-copy"),
                    verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(channel?.group?.takeIf { it.isNotBlank() }?.let {
                        labels.text("من مجموعتك · $it", "From your group · $it", "Aus deiner Gruppe · $it")
                    } ?: labels.section(HomeSection.LIVE), color = colors.accent,
                        fontSize = 9.sp, lineHeight = 12.sp, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(channel?.name ?: "MN StreamX", Modifier.testTag("hero-channel-name"),
                        color = colors.text, fontSize = 22.sp, lineHeight = 25.sp,
                        fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(current?.title ?: labels.text("بث مباشر", "Live TV", "Live-TV"),
                        color = colors.text, fontSize = 14.sp, lineHeight = 17.sp,
                        fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (current != null) {
                        val format = remember(Locale.getDefault()) { SimpleDateFormat("HH:mm", Locale.getDefault()) }
                        val remaining = (current.endTime - guide.nowMillis).coerceAtLeast(0)
                        val left = (remaining / 60_000L + if (remaining % 60_000L == 0L) 0 else 1).coerceAtMost(9999)
                        Text("${format.format(Date(current.startTime))} – ${format.format(Date(current.endTime))} · " +
                            labels.text("باقٍ $left د", "$left min left", "noch $left Min."),
                            color = colors.muted, fontSize = 11.sp, lineHeight = 13.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    current?.description?.takeIf { it.isNotBlank() }?.let {
                        Text(it, color = colors.muted, fontSize = 11.sp, lineHeight = 14.sp,
                            maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("hero-description"))
                    }
                    FlowHeroActions(channel, labels, colors, languageBadge, qualityBadge, focus, onWatch)
                }
                if (channel != null) HomeProgramTimeline(channel, state, labels, colors)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowHeroActions(channel: Channel?, labels: HomeLabels, colors: HomeTvPalette,
    language: String?, quality: String?, focus: FocusRequester?, onWatch: (Channel) -> Unit) {
    FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        HomeWatchButton(labels.text("مشاهدة", "Watch", "Ansehen"), colors, { channel?.let(onWatch) },
            Modifier.widthIn(min = 150.dp).testTag("hero-continue")
                .then(if (focus != null) Modifier.focusRequester(focus) else Modifier), enabled = channel != null)
        listOfNotNull(language, quality).forEach { label ->
            Surface(color = colors.selected, shape = RoundedCornerShape(5.dp)) {
                Text(label, Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = colors.text, fontSize = 10.sp)
            }
        }
    }
}