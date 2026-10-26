package com.yourapp.iptv.player.vod

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.TrackNameProvider
import com.yourapp.iptv.domain.model.VodSeek

internal data class VodPlayerSnapshot(
    val position: Long = 0, val duration: Long = -1, val playing: Boolean = false,
    val seekable: Boolean = false, val buffering: Boolean = true,
    val ended: Boolean = false, val failed: Boolean = false,
    val audio: VodTrackList = VodTrackList(), val subtitles: VodTrackList = VodTrackList(),
    val playWhenReady: Boolean = false
)

@androidx.annotation.OptIn(UnstableApi::class)
internal class VodPlaybackSession(
    val player: Player, private val names: TrackNameProvider,
    private val preferences: VodTrackPreferences,
    private val capture: (Long,Long,Boolean) -> Unit,
    private val clock: () -> Long = SystemClock::elapsedRealtime,
    private val clearResume: (Long) -> Unit = {},
    private val diagnosticSource: String? = null
) {
    var snapshot by mutableStateOf(VodPlayerSnapshot())
        private set
    private var closed = false
    private var completed = false
    private var lastSaved = clock()
    private var lastCapture: Triple<Long,Long,Boolean>? = null
    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            val wasPlaying = snapshot.playing
            val wasFailed = snapshot.failed
            refresh()
            if (!wasFailed) player.playerError?.let { com.yourapp.iptv.player.PlaybackDiagnostics.record(it,diagnosticSource) }
            if (player.playbackState == Player.STATE_ENDED && !completed) { completed = true; save() }
            else if (wasPlaying && !snapshot.playing) save()
        }
    }
    init { player.addListener(listener); refresh() }

    fun tick(visible: Boolean) {
        if (closed) return
        if (visible) refresh(positionOnly = true)
        if (player.isPlaying && clock() - lastSaved >= 10_000) save()
    }
    private fun refresh(positionOnly: Boolean = false) {
        if (closed) return
        val duration = player.duration.takeIf { it > 0 } ?: -1
        snapshot = VodPlayerSnapshot(player.currentPosition.coerceAtLeast(0),duration,player.isPlaying,
            duration > 0 && player.isCurrentMediaItemSeekable && player.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM),
            player.playbackState == Player.STATE_BUFFERING,player.playbackState == Player.STATE_ENDED,player.playerError != null,
            if (positionOnly) snapshot.audio else vodTracks(player,C.TRACK_TYPE_AUDIO,names),
            if (positionOnly) snapshot.subtitles else vodTracks(player,C.TRACK_TYPE_TEXT,names),player.playWhenReady)
    }
    fun seekBy(delta: Long) { VodSeek.target(player.currentPosition,player.duration,delta)?.let(::seekTo) }
    fun seekTo(position: Long) {
        if (closed || !snapshot.seekable) return
        val target = VodSeek.target(position,player.duration,0) ?: return
        completed = false
        if (target < 10_000) resetResume()
        player.seekTo(target); refresh(positionOnly = true); save()
    }
    fun play() {
        if (closed) return
        if (player.playbackState == Player.STATE_ENDED) restart() else { player.play(); refresh() }
    }
    fun toggle() { if (player.playWhenReady && !snapshot.ended) pause() else play() }
    fun pause() { if (!closed) { save(); player.pause(); refresh() } }
    fun restart() {
        if (closed || !snapshot.seekable) return
        completed = false; resetResume(); player.seekTo(0); player.play(); refresh()
    }
    fun retry() {
        if (closed) return
        player.prepare(); player.play(); refresh()
    }
    fun selectTrack(type: Int, choice: VodTrackChoice?) { if (!closed) { preferences.select(player,type,choice); refresh() } }
    fun disableSubtitles() { if (!closed) { preferences.disableText(player); refresh() } }
    private fun resetResume() { try { clearResume(player.duration) } catch (_: Exception) { } }
    fun save() {
        if (closed) return
        lastSaved = clock()
        val value = Triple(player.currentPosition,player.duration,completed || player.playbackState == Player.STATE_ENDED)
        if (lastCapture != value) {
            try { capture(value.first,value.second,value.third); lastCapture = value } catch (_: Exception) { }
        }
    }
    fun close() {
        if (closed) return
        try { save() } finally {
            closed = true
            try { player.removeListener(listener) } finally { player.release() }
        }
    }
}