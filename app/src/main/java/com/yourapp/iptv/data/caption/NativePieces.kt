package com.yourapp.iptv.data.caption

import androidx.annotation.Keep

@Keep internal class NativePieces {
    external fun create(path: String): Long
    external fun encode(handle: Long, text: String): Array<String>
    external fun decode(handle: Long, pieces: Array<String>): String
    external fun release(handle: Long)
    companion object { init { System.loadLibrary("mnx-whisper") } }
}