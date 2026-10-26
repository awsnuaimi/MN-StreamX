package com.yourapp.iptv.player.vod

import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import java.lang.reflect.Proxy

/** An event-driven Player test double; no decoder, connection, or real provider credentials. */
@androidx.annotation.OptIn(UnstableApi::class)
internal class VodTestPlayer {
    var position = 60_000L
    var duration = 200_000L
    var state = Player.STATE_READY
    var playing = true
    var playWhenReady = true
    var seekable = true
    var error: PlaybackException? = null
    var tracks = Tracks.EMPTY
    var parameters = TrackSelectionParameters.Builder(org.robolectric.RuntimeEnvironment.getApplication()).build()
    var releases = 0
    var prepares = 0
    val seeks = mutableListOf<Long>()
    val listeners = mutableListOf<Player.Listener>()
    private val commands = Player.Commands.Builder().addAll(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
        Player.COMMAND_SET_TRACK_SELECTION_PARAMETERS).build()
    val player: Player = Proxy.newProxyInstance(Player::class.java.classLoader,arrayOf(Player::class.java)) { proxy,method,args ->
        when (method.name) {
            "getCurrentPosition", "getContentPosition" -> position
            "getDuration", "getContentDuration" -> duration
            "getPlaybackState" -> state
            "isPlaying" -> playing
            "getPlayWhenReady" -> playWhenReady
            "getPlayerError" -> error
            "getCurrentTracks" -> tracks
            "getTrackSelectionParameters" -> parameters
            "setTrackSelectionParameters" -> { parameters = args!![0] as TrackSelectionParameters;fire();null }
            "getAvailableCommands" -> commands
            "isCommandAvailable" -> commands.contains(args!![0] as Int)
            "isCurrentMediaItemSeekable" -> seekable
            "addListener" -> { listeners.add(args!![0] as Player.Listener);null }
            "removeListener" -> { listeners.remove(args!![0] as Player.Listener);null }
            "seekTo" -> { position = args!!.last() as Long;seeks.add(position);if (state == Player.STATE_ENDED) state = Player.STATE_READY;fire();null }
            "play" -> { playWhenReady = true;playing = true;fire();null }
            "pause" -> { playWhenReady = false;playing = false;fire();null }
            "prepare" -> { prepares++;error = null;state = Player.STATE_READY;fire();null }
            "release" -> { releases++;null }
            "getApplicationLooper" -> android.os.Looper.getMainLooper()
            "getCurrentTimeline" -> Timeline.EMPTY
            "getMediaMetadata" -> MediaMetadata.EMPTY
            "getVideoSize" -> VideoSize.UNKNOWN
            "getAudioAttributes" -> AudioAttributes.DEFAULT
            "getPlaybackParameters" -> PlaybackParameters.DEFAULT
            "getVolume" -> 1f
            "equals" -> proxy === args?.firstOrNull()
            "hashCode" -> System.identityHashCode(proxy)
            "toString" -> "SyntheticVodPlayer"
            else -> when (method.returnType) {
                java.lang.Boolean.TYPE -> false
                java.lang.Integer.TYPE -> 0
                java.lang.Long.TYPE -> 0L
                java.lang.Float.TYPE -> 0f
                else -> null
            }
        }
    } as Player
    fun fire() {
        val events = Player.Events(FlagSet.Builder().add(Player.EVENT_PLAYBACK_STATE_CHANGED)
            .add(Player.EVENT_IS_PLAYING_CHANGED).add(Player.EVENT_TRACKS_CHANGED).build())
        listeners.toList().forEach { it.onEvents(player,events) }
    }
    companion object {
        fun group(id: String, language: String, text: Boolean = false, supported: Boolean = true): Tracks.Group {
            val format = Format.Builder().setId(id).setLabel(language).setLanguage(language)
                .setSampleMimeType(if (text) "text/vtt" else "audio/mp4a-latm").build()
            return Tracks.Group(TrackGroup(id,format),false,
                intArrayOf(if (supported) C.FORMAT_HANDLED else C.FORMAT_UNSUPPORTED_SUBTYPE),booleanArrayOf(true))
        }
    }
}