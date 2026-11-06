package mnstudio.apps.mnstreamx.ui.screen.vod

import mnstudio.apps.mnstreamx.player.vod.VodPlayerSnapshot

internal enum class VodDisplayMode { FIT, ZOOM, STRETCH }
internal enum class VodPlayerPanel { AUDIO, SUBTITLES, SPEED, DISPLAY, RESTART, EPISODES }
internal enum class VodControlTarget { PLAY, AUDIO, SUBTITLES, SPEED, DISPLAY, RESTART, EPISODES }

/** Buffering, a held slider and modal choices must not lose their controls to a timer. */
internal fun vodMayHideControls(snapshot: VodPlayerSnapshot, active: Boolean, modal: Boolean, scrubbing: Boolean) =
    active && snapshot.playing && !snapshot.buffering && !snapshot.ended && !snapshot.failed && !modal && !scrubbing