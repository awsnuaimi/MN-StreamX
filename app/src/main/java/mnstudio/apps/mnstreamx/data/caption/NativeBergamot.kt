package mnstudio.apps.mnstreamx.data.caption

import androidx.annotation.Keep
@Keep
internal class NativeBergamot {
    external fun create(model: String, vocabulary: String, shortlist: String): Long
    external fun translate(handle: Long, text: String, budgetMillis: Int): String
    external fun cancel(handle: Long)
    external fun release(handle: Long)
    external fun unpack(source: String, destination: String, expectedBytes: Long)
    companion object { init { System.loadLibrary("mnx-whisper") } }
}