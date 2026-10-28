package com.yourapp.iptv.ui.screen.vod

import com.yourapp.iptv.player.vod.VodPlayerSnapshot

internal enum class VodDisplayMode { FIT, ZOOM, STRETCH }
internal enum class VodPlayerPanel { AUDIO, SUBTITLES, SPEED, DISPLAY, RESTART }
internal enum class VodControlTarget { PLAY, AUDIO, SUBTITLES, SPEED, DISPLAY, RESTART }

/** Buffering, a held slider and modal choices must not lose their controls to a timer. */
internal fun vodMayHideControls(snapshot: VodPlayerSnapshot, active: Boolean, modal: Boolean, scrubbing: Boolean) =
    active && snapshot.playing && !snapshot.buffering && !snapshot.ended && !snapshot.failed && !modal && !scrubbing