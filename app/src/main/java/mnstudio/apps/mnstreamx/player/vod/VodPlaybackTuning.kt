package mnstudio.apps.mnstreamx.player.vod

import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl

/** Start after 500 ms of buffered media; continue filling a bounded steady-state buffer. */
@androidx.annotation.OptIn(UnstableApi::class)
internal object VodPlaybackTuning {
    fun createLoadControl(): DefaultLoadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(15_000,30_000,500,2_000)
        .setTargetBufferBytes(24 * 1024 * 1024)
        .setPrioritizeTimeOverSizeThresholds(false)
        .setBackBuffer(0,false)
        .build()
}