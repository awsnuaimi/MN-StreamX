package com.yourapp.iptv.player.caption

import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.yourapp.iptv.domain.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class EmbeddedCaptionTrack(val group: TrackGroup, val index: Int, val label: String,
    val language: String?, val selected: Boolean)

@androidx.annotation.OptIn(UnstableApi::class)
class EmbeddedCaptions(private val player: ExoPlayer) : AutoCloseable {
    private val rows = MutableStateFlow(emptyList<EmbeddedCaptionTrack>())
    val tracks = rows.asStateFlow()
    private var choice = CaptionSettings()
    private var applied = false
    private val reading = MutableStateFlow(player.playbackState != Player.STATE_READY)
    val loading = reading.asStateFlow()
    private val listener = object : Player.Listener {
        override fun onTracksChanged(tracks: Tracks) { publish() }
        override fun onPlaybackStateChanged(playbackState: Int) { reading.value = playbackState != Player.STATE_READY }
    }
    init { player.addListener(listener); publish() }
    fun apply(settings: CaptionSettings) {
        if (applied && settings.mode == choice.mode && settings.target == choice.target) return
        applied = true
        choice = settings
        val builder = player.trackSelectionParameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, settings.mode != CaptionMode.EMBEDDED)
            .setPreferredTextLanguage(settings.target.tag).setSelectUndeterminedTextLanguage(false)
        val value = builder.build()
        if (value != player.trackSelectionParameters) player.trackSelectionParameters = value
        publish()
    }
    fun select(track: EmbeddedCaptionTrack): CaptionSettings? {
        if (rows.value.none { it.group == track.group && it.index == track.index }) return null
        choice = choice.copy(mode = CaptionMode.EMBEDDED,
            target = CaptionLanguage.fromTag(track.language) ?: choice.target)
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .setOverrideForType(TrackSelectionOverride(track.group, listOf(track.index))).build()
        return choice
    }
    private fun publish() {
        rows.value = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }.flatMap { group ->
            (0 until group.length).filter { group.isTrackSupported(it) }.map { index ->
                val format = group.getTrackFormat(index)
                EmbeddedCaptionTrack(group.mediaTrackGroup, index,
                    format.label ?: CaptionLanguage.fromTag(format.language)?.nativeName ?: format.language ?: "CC ${index + 1}",
                    format.language, group.isTrackSelected(index))
            }
        }
    }
    override fun close() { player.removeListener(listener) }
}