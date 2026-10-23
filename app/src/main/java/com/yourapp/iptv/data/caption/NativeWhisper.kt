package com.yourapp.iptv.data.caption

import androidx.annotation.Keep

/** Loaded only after the user enables local generation and the verified model is ready. */
@Keep
internal class NativeWhisper {
    external fun create(path: String, threads: Int): Long
    external fun decode(handle: Long, samples: FloatArray, language: String, translateToEnglish: Boolean,
        target: String): Array<String>
    external fun cancel(handle: Long)
    external fun park(handle: Long)
    external fun resetState(handle: Long)
    external fun trimMemory()
    external fun release(handle: Long)
    companion object { init { System.loadLibrary("mnx-whisper") } }
}