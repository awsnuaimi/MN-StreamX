package com.yourapp.iptv.ui.screen.vod

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yourapp.iptv.domain.model.VodKind
import com.yourapp.iptv.player.vod.VodPlayerSnapshot
import com.yourapp.iptv.player.vod.VodTrackList
import com.yourapp.iptv.viewmodel.VodPlaybackState

@Composable
internal fun VodCinemaHeader(item: VodPlaybackState.Ready, snapshot: VodPlayerSnapshot, labels: VodPlaybackLabels,
    onBack: () -> Unit, back: FocusRequester, play: FocusRequester) {
    Row(Modifier.fillMaxWidth().background(Brush.verticalGradient(0f to Color.Black.copy(alpha = .92f),.8f to Color.Black.copy(alpha = .80f),1f to Color.Transparent))
        .padding(horizontal = 32.dp,vertical = 20.dp),verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        VodControlButton(labels.back(),VodGlyph.BACK,onBack,Modifier.focusRequester(back).focusProperties { down = play }.testTag("vod-back"))
        Column(Modifier.weight(1f),verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(if (item.kind == VodKind.SERIES) labels.series() else labels.movie(),color = Color.White.copy(alpha = .65f),
                fontSize = 10.sp,letterSpacing = 1.sp)
            Text(item.title,Modifier.testTag("vod-title"),color = Color.White,style = MaterialTheme.typography.titleLarge,maxLines = 2,overflow = TextOverflow.Ellipsis)
        }
        if (snapshot.ended) Text(labels.finished(),color = Color.White.copy(alpha = .8f),style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
internal fun VodCinemaTools(item: VodPlaybackState.Ready, snapshot: VodPlayerSnapshot, labels: VodPlaybackLabels,
    autoNext: Boolean, actions: VodPlayerActions, display: VodDisplayMode, target: (VodControlTarget) -> FocusRequester,
    up: FocusRequester) {
    val scale = LocalDensity.current.fontScale.coerceIn(1f,1.8f)
    fun tool(width: Int, id: VodControlTarget, tag: String) = Modifier.width(width.dp*scale)
        .focusRequester(target(id)).focusProperties { this.up = up }.testTag(tag)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp,Alignment.CenterHorizontally)) {
        VodControlButton(labels.audio(),VodGlyph.AUDIO,actions.audio,tool(140,VodControlTarget.AUDIO,"vod-audio"),
            value = vodTrackSummary(snapshot.audio,labels))
        VodControlButton(labels.subtitles(),VodGlyph.SUBTITLES,actions.subtitles,tool(140,VodControlTarget.SUBTITLES,"vod-subtitles"),
            value = vodTrackSummary(snapshot.subtitles,labels))
        VodControlButton(labels.text("السرعة","Speed","Tempo"),VodGlyph.SPEED,actions.speed,tool(112,VodControlTarget.SPEED,"vod-speed"),
            snapshot.speedSupported,value = "${snapshot.speed}×")
        VodControlButton(labels.picture(),VodGlyph.DISPLAY,actions.display,tool(140,VodControlTarget.DISPLAY,"vod-display"),value = labels.display(display))
        VodControlButton(labels.restart(),VodGlyph.RESTART,actions.restart,tool(144,VodControlTarget.RESTART,"vod-restart"),snapshot.seekable,value = "00:00")
        if (item.kind == VodKind.SERIES) VodControlButton(labels.text("تشغيل تلقائي","Auto next","Automatisch"),VodGlyph.AUTO,
            { actions.automatic(!autoNext) },Modifier.width(156.dp*scale).focusProperties { this.up = up }.testTag("vod-auto-next"),
            item.seriesId != null,value = if (autoNext) labels.automatic() else labels.off())
    }
}

internal fun vodTrackSummary(tracks: VodTrackList, labels: VodPlaybackLabels): String = when {
    tracks.disabled -> labels.off()
    tracks.automatic -> labels.automatic()
    else -> tracks.choices.firstOrNull { it.chosen }?.name ?: labels.automatic()
}

@Composable
internal fun VodCinemaEpisode(item: VodPlaybackState.Ready, labels: VodPlaybackLabels, countdown: Int?, actions: VodPlayerActions) {
    if (item.kind == VodKind.SERIES) Row(Modifier.fillMaxWidth(),verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f),verticalArrangement = Arrangement.spacedBy(3.dp)) {
            item.next?.let { Text("${labels.next()}: ${it.name}",color = Color.White.copy(alpha = .9f),fontSize = 12.sp,maxLines = 1,overflow = TextOverflow.Ellipsis) }
            countdown?.let { Text(labels.nextIn(it),color = Color.White,modifier = Modifier.testTag("vod-countdown"),fontSize = 12.sp) }
        }
        if (countdown != null) VodControlButton(labels.cancelNext(),VodGlyph.PAUSE,actions.cancelNext,Modifier.testTag("vod-cancel-next"))
        VodControlButton(labels.next(),VodGlyph.NEXT,actions.next,Modifier.testTag("vod-next"),item.next != null)
    }
    if (item.episodesLoading) Text(labels.episodesLoading(),color = Color.White.copy(alpha = .8f),style = MaterialTheme.typography.bodySmall)
    if (item.episodesFailed) Row(verticalAlignment = Alignment.CenterVertically,horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(labels.episodesFailed(),Modifier.weight(1f),color = Color.White,style = MaterialTheme.typography.bodySmall)
        VodControlButton(labels.retryEpisodes(),VodGlyph.RESTART,actions.retryEpisodes,Modifier.testTag("vod-retry-episodes"))
    }
    if (item.kind == VodKind.SERIES && item.seriesId == null) Text(labels.noParent(),color = Color.White.copy(alpha = .8f),
        style = MaterialTheme.typography.bodySmall)
}