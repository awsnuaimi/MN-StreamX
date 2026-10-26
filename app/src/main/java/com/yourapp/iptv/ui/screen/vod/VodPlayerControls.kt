package com.yourapp.iptv.ui.screen.vod

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yourapp.iptv.domain.model.VodKind
import com.yourapp.iptv.domain.model.VodSeek
import com.yourapp.iptv.player.vod.VodPlayerSnapshot
import com.yourapp.iptv.ui.component.common.AppButton
import com.yourapp.iptv.viewmodel.VodPlaybackState

internal data class VodPlayerActions(
    val back: () -> Unit, val toggle: () -> Unit, val step: (Long) -> Unit, val seek: (Long) -> Unit,
    val restart: () -> Unit, val audio: () -> Unit, val subtitles: () -> Unit,
    val next: () -> Unit, val automatic: (Boolean) -> Unit, val cancelNext: () -> Unit,
    val retryEpisodes: () -> Unit
)

@Composable
internal fun VodPlayerControls(item: VodPlaybackState.Ready, snapshot: VodPlayerSnapshot,
    labels: VodPlaybackLabels, autoNext: Boolean, countdown: Int?, actions: VodPlayerActions, playFocus: FocusRequester) {
    val timeline = remember { FocusRequester() }
    val audio = remember { FocusRequester() }
    Column(Modifier.fillMaxSize().testTag("vod-controls")
        .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .65f),Color.Transparent,Color.Black.copy(alpha = .94f))))
        .padding(horizontal = 24.dp,vertical = 14.dp),verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically,horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            AppButton(labels.back(),actions.back,Modifier.testTag("vod-back"))
            Text(item.title,Modifier.weight(1f),color = Color.White,style = MaterialTheme.typography.titleLarge,
                maxLines = 2,overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.weight(1f))
        if (snapshot.ended && item.next != null) {
            Row(Modifier.fillMaxWidth(),verticalAlignment = Alignment.CenterVertically,horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("${labels.next()}: ${item.next.name}",color = Color.White,maxLines = 1,overflow = TextOverflow.Ellipsis)
                    countdown?.let { Text(labels.nextIn(it),color = Color.White,modifier = Modifier.testTag("vod-countdown")) }
                }
                if (countdown != null) AppButton(labels.cancelNext(),actions.cancelNext,Modifier.testTag("vod-cancel-next"))
            }
        }
        if (item.episodesLoading) Text(labels.episodesLoading(),color = Color.White,style = MaterialTheme.typography.bodySmall)
        if (item.episodesFailed) Row(verticalAlignment = Alignment.CenterVertically,horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(labels.episodesFailed(),Modifier.weight(1f),color = Color.White,style = MaterialTheme.typography.bodySmall)
            AppButton(labels.retryEpisodes(),actions.retryEpisodes,Modifier.testTag("vod-retry-episodes"))
        }
        if (item.kind == VodKind.SERIES && item.seriesId == null) Text(labels.noParent(),color = Color.White,
            style = MaterialTheme.typography.bodySmall)
        VodTimeline(snapshot,labels,timeline,actions.seek,actions.step)
        Row(Modifier.fillMaxWidth(),horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AppButton(labels.rewind(),{ actions.step(-VodSeek.STEP_MS) },Modifier.weight(1f).testTag("vod-rewind"),snapshot.seekable)
            AppButton(if (snapshot.playWhenReady && !snapshot.ended) labels.pause() else labels.play(),actions.toggle,
                Modifier.weight(1f).focusRequester(playFocus).focusProperties {
                    if (snapshot.seekable) up = timeline
                    down = audio
                }.testTag("vod-toggle"))
            AppButton(labels.forward(),{ actions.step(VodSeek.STEP_MS) },Modifier.weight(1f).testTag("vod-forward"),snapshot.seekable)
            AppButton(labels.restart(),actions.restart,Modifier.weight(1f).testTag("vod-restart"),snapshot.seekable)
            if (item.kind == VodKind.SERIES) AppButton(labels.next(),actions.next,
                Modifier.weight(1f).testTag("vod-next"),item.next != null)
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AppButton("${labels.audio()} (${snapshot.audio.choices.size})",actions.audio,
                Modifier.weight(1f).focusRequester(audio).focusProperties { up = playFocus }.testTag("vod-audio"))
            AppButton("${labels.subtitles()} (${snapshot.subtitles.choices.size})",actions.subtitles,
                Modifier.weight(1f).testTag("vod-subtitles"))
            if (item.kind == VodKind.SERIES) AppButton(labels.autoNext(autoNext),{ actions.automatic(!autoNext) },
                Modifier.weight(1.4f).testTag("vod-auto-next"),item.seriesId != null)
        }
    }
}