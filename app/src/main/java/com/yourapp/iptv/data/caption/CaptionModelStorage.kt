package com.yourapp.iptv.data.caption

import java.io.File
import java.io.IOException

/** Only the dedicated, reproducible model directory; never playlists, credentials or databases. */
internal class CaptionModelStorage(private val modelDirectory: File) {
    fun bytes(): Long {
        if (!modelDirectory.exists()) return 0L
        return modelDirectory.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    fun clear(): Long {
        if (!modelDirectory.exists()) return 0L
        val size = bytes()
        if (!modelDirectory.deleteRecursively()) {
            throw IOException("Some downloaded caption model files could not be removed")
        }
        return size
    }
}