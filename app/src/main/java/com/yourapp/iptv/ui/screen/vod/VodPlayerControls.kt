package com.yourapp.iptv.ui.screen.vod

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.yourapp.iptv.player.vod.VodPlayerSnapshot
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
    focusEpoch: Int = 0, manageFocus: Boolean = false, showHints: Boolean = false, seekFeedback: Pair<Long,Long>? = null, onDockFraction: (Float) -> Unit = {}) {
    val timeline = remember { FocusRequester() }
    val back = remember { FocusRequester() }
    val targets = remember { VodControlTarget.entries.associateWith { FocusRequester() } }
    fun target(id: VodControlTarget) = if (id == VodControlTarget.PLAY) playFocus else targets.getValue(id)
    val audio = target(VodControlTarget.AUDIO)
    BoxWithConstraints(Modifier.fillMaxSize().testTag("vod-controls")) {
        val compact = maxHeight/LocalDensity.current.fontScale < 360.dp
        val fullHeight = constraints.maxHeight.toFloat()
        LaunchedEffect(focusEpoch,manageFocus,compact) {
            if (manageFocus) {
                withFrameNanos { }
                val enabled = when (restore) {
                    VodControlTarget.SPEED -> snapshot.speedSupported
                    VodControlTarget.RESTART -> snapshot.seekable
                    else -> true
                }
                target(if (enabled) restore else VodControlTarget.PLAY).requestFocus()
            }
        }
        Column(Modifier.fillMaxSize()) {
            VodCinemaHeader(item,snapshot,labels,actions.back,back,playFocus)
            if (compact) {
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                    VodTransport(snapshot,labels,actions,playFocus,back,if (snapshot.seekable) timeline else audio,
                        Modifier.padding(vertical = 20.dp),seekFeedback)
                    VodCinemaDock(item,snapshot,labels,autoNext,countdown,actions,displayMode,timeline,playFocus,
                        ::target,showHints,Modifier.onSizeChanged { if (fullHeight > 0) onDockFraction(it.height/fullHeight) })
                }
            } else {
                Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment = Alignment.Center) {
                    VodTransport(snapshot,labels,actions,playFocus,back,if (snapshot.seekable) timeline else audio,feedback = seekFeedback)
                }
                VodCinemaDock(item,snapshot,labels,autoNext,countdown,actions,displayMode,timeline,playFocus,
                    ::target,showHints,Modifier.onSizeChanged { if (fullHeight > 0) onDockFraction(it.height/fullHeight) })
            }
        }
    }
}

@Composable
private fun VodCinemaDock(item: VodPlaybackState.Ready, snapshot: VodPlayerSnapshot, labels: VodPlaybackLabels,
    autoNext: Boolean, countdown: Int?, actions: VodPlayerActions, display: VodDisplayMode,
    timeline: FocusRequester, play: FocusRequester, target: (VodControlTarget) -> FocusRequester, showHints: Boolean, modifier: Modifier) {
    // Independent shading under the actual controls preserves contrast over bright video frames.
    Column(modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0x9905090F),Color(0xF005090F),Color(0xFA05090F))))
        .padding(start = 32.dp,end = 32.dp,top = 14.dp,bottom = 18.dp),verticalArrangement = Arrangement.spacedBy(6.dp)) {
        VodCinemaEpisode(item,labels,countdown,actions)
        VodTimeline(snapshot,labels,timeline,actions.seek,actions.step,actions.scrubbing,target(VodControlTarget.AUDIO),play)
        Spacer(Modifier.height(5.dp))
        VodCinemaTools(item,snapshot,labels,autoNext,actions,display,target,if (snapshot.seekable) timeline else play)
        if (showHints) {
            Spacer(Modifier.height(4.dp))
            androidx.compose.material3.Text(labels.hint(),Modifier.testTag("vod-hint"),color = Color.White.copy(alpha = .58f),
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
        }
    }
}