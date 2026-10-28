package com.yourapp.iptv.ui.screen.vod

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yourapp.iptv.domain.model.VodKind
import com.yourapp.iptv.domain.model.VodSeek
import com.yourapp.iptv.player.vod.VodPlayerSnapshot
import com.yourapp.iptv.player.vod.VodTrackList
import com.yourapp.iptv.viewmodel.VodPlaybackState

internal data class VodPlayerActions(
    val back: () -> Unit, val toggle: () -> Unit, val step: (Long) -> Unit, val seek: (Long) -> Unit,
    val restart: () -> Unit, val audio: () -> Unit, val subtitles: () -> Unit,
    val next: () -> Unit, val automatic: (Boolean) -> Unit, val cancelNext: () -> Unit,
    val retryEpisodes: () -> Unit, val speed: () -> Unit = {}, val display: () -> Unit = {},
    val scrubbing: (Boolean) -> Unit = {}
)

@Composable
internal fun VodPlayerControls(item: VodPlaybackState.Ready, snapshot: VodPlayerSnapshot,
    labels: VodPlaybackLabels, autoNext: Boolean, countdown: Int?, actions: VodPlayerActions, playFocus: FocusRequester,
    displayMode: VodDisplayMode = VodDisplayMode.FIT, restore: VodControlTarget = VodControlTarget.PLAY,
    focusEpoch: Int = 0, manageFocus: Boolean = false) {
    val timeline = remember { FocusRequester() }
    val toolScale = LocalDensity.current.fontScale.coerceIn(1f,1.8f)
    val targets = remember { VodControlTarget.entries.associateWith { FocusRequester() } }
    fun target(id: VodControlTarget) = if (id == VodControlTarget.PLAY) playFocus else targets.getValue(id)
    LaunchedEffect(focusEpoch,manageFocus) {
        if (manageFocus) {
            withFrameNanos { }
            val enabled = when (restore) { VodControlTarget.SPEED -> snapshot.speedSupported
                VodControlTarget.RESTART -> snapshot.seekable;else -> true }
            target(if (enabled) restore else VodControlTarget.PLAY).requestFocus()
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().testTag("vod-controls")
        .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .76f),Color.Transparent,Color.Black.copy(alpha = .98f))))) {
        val available = (maxHeight-90.dp).coerceAtLeast(120.dp)
        Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(24.dp,14.dp),verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            VodControlButton(labels.back(),VodGlyph.BACK,actions.back,Modifier.testTag("vod-back"))
            Column(Modifier.weight(1f)) {
                Text(if (item.kind == VodKind.SERIES) labels.series() else labels.movie(),color = Color.White.copy(alpha = .72f),
                    style = MaterialTheme.typography.labelMedium)
                Text(item.title,color = Color.White,style = MaterialTheme.typography.titleLarge,maxLines = 2,overflow = TextOverflow.Ellipsis)
            }
            if (snapshot.ended) Text(labels.finished(),color = Color.White,style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.weight(1f))
        Column(Modifier.fillMaxWidth().heightIn(max = available)
            .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp,vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            VodEpisodeNotice(item,labels,countdown,actions)
            VodTimeline(snapshot,labels,timeline,actions.seek,actions.step,actions.scrubbing,playFocus)
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp,Alignment.CenterHorizontally)) {
                VodControlButton(labels.rewind(),VodGlyph.REWIND,{ actions.step(-VodSeek.STEP_MS) },
                    Modifier.testTag("vod-rewind"),snapshot.seekable)
                VodControlButton(if (snapshot.playWhenReady && !snapshot.ended) labels.pause() else labels.play(),
                    if (snapshot.playWhenReady && !snapshot.ended) VodGlyph.PAUSE else VodGlyph.PLAY,actions.toggle,
                    Modifier.widthIn(min = 150.dp).focusRequester(playFocus).focusProperties {
                        if (snapshot.seekable) up = timeline
                        down = target(VodControlTarget.AUDIO)
                    }.testTag("vod-toggle"),prominent = true)
                VodControlButton(labels.forward(),VodGlyph.FORWARD,{ actions.step(VodSeek.STEP_MS) },
                    Modifier.testTag("vod-forward"),snapshot.seekable)
                if (item.kind == VodKind.SERIES) VodControlButton(labels.next(),VodGlyph.NEXT,actions.next,
                    Modifier.testTag("vod-next"),item.next != null)
            }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                VodControlButton(labels.audio(),VodGlyph.AUDIO,actions.audio,Modifier.width(140.dp*toolScale)
                    .focusRequester(target(VodControlTarget.AUDIO)).focusProperties { up = playFocus }.testTag("vod-audio"),
                    value = vodTrackSummary(snapshot.audio,labels))
                VodControlButton(labels.subtitles(),VodGlyph.SUBTITLES,actions.subtitles,Modifier.width(140.dp*toolScale)
                    .focusRequester(target(VodControlTarget.SUBTITLES)).testTag("vod-subtitles"),value = vodTrackSummary(snapshot.subtitles,labels))
                VodControlButton(labels.text("السرعة","Speed","Tempo"),VodGlyph.SPEED,actions.speed,Modifier.width(116.dp*toolScale)
                    .focusRequester(target(VodControlTarget.SPEED)).testTag("vod-speed"),snapshot.speedSupported,
                    value = "${snapshot.speed}×")
                VodControlButton(labels.picture(),VodGlyph.DISPLAY,actions.display,Modifier.width(124.dp*toolScale)
                    .focusRequester(target(VodControlTarget.DISPLAY)).testTag("vod-display"),value = labels.display(displayMode))
                VodControlButton(labels.restart(),VodGlyph.RESTART,actions.restart,Modifier.width(130.dp*toolScale)
                    .focusRequester(target(VodControlTarget.RESTART)).testTag("vod-restart"),snapshot.seekable,value = "00:00")
                if (item.kind == VodKind.SERIES) VodControlButton(labels.text("تشغيل تلقائي","Auto next","Automatisch"),VodGlyph.AUTO,{ actions.automatic(!autoNext) },
                    Modifier.width(156.dp*toolScale).testTag("vod-auto-next"),item.seriesId != null,
                    value = if (autoNext) labels.automatic() else labels.off())
            }
            Text(labels.hint(),color = Color.White.copy(alpha = .65f),style = MaterialTheme.typography.labelSmall)
        }
        }
    }
}

internal fun vodTrackSummary(tracks: VodTrackList, labels: VodPlaybackLabels): String = when {
    tracks.disabled -> labels.off()
    tracks.automatic -> labels.automatic()
    else -> tracks.choices.firstOrNull { it.chosen }?.name ?: labels.automatic()
}

@Composable
private fun VodEpisodeNotice(item: VodPlaybackState.Ready, labels: VodPlaybackLabels,
    countdown: Int?, actions: VodPlayerActions) {
    if (item.next != null) Row(Modifier.fillMaxWidth(),verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(Modifier.weight(1f)) {
            Text("${labels.next()}: ${item.next.name}",color = Color.White,maxLines = 1,overflow = TextOverflow.Ellipsis)
            countdown?.let { Text(labels.nextIn(it),color = Color.White,modifier = Modifier.testTag("vod-countdown")) }
        }
        if (countdown != null) VodControlButton(labels.cancelNext(),VodGlyph.PAUSE,actions.cancelNext,Modifier.testTag("vod-cancel-next"))
    }
    if (item.episodesLoading) Text(labels.episodesLoading(),color = Color.White,style = MaterialTheme.typography.bodySmall)
    if (item.episodesFailed) Row(verticalAlignment = Alignment.CenterVertically,horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(labels.episodesFailed(),Modifier.weight(1f),color = Color.White,style = MaterialTheme.typography.bodySmall)
        VodControlButton(labels.retryEpisodes(),VodGlyph.RESTART,actions.retryEpisodes,Modifier.testTag("vod-retry-episodes"))
    }
    if (item.kind == VodKind.SERIES && item.seriesId == null) Text(labels.noParent(),color = Color.White,
        style = MaterialTheme.typography.bodySmall)
}