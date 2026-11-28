package mnstudio.apps.mnstreamx.player.caption

import android.os.Bundle
import android.os.Message
import android.os.Messenger
import mnstudio.apps.mnstreamx.domain.caption.CaptionPipelineState

internal object PolishCaptionWire {
    const val START = 1
    const val STOP = 2
    const val AUDIO = 3
    const val READY = 4
    const val ACK = 5
    const val STATE = 6
    const val DONE = 7
    const val LIMIT = 8
    const val MAX_SAMPLES = 80_000

    fun send(to: Messenger, type: Int, id: Int, from: Messenger? = null, data: Bundle = Bundle()) {
        val message = Message.obtain(null, type, id, 0)
        message.replyTo = from
        message.data = data
        to.send(message)
    }

    fun state(value: CaptionPipelineState) = Bundle().apply { putSerializable("state", value) }

    @Suppress("DEPRECATION")
    fun read(data: Bundle): CaptionPipelineState? {
        data.classLoader = CaptionPipelineState::class.java.classLoader
        return data.getSerializable("state") as? CaptionPipelineState
    }
}