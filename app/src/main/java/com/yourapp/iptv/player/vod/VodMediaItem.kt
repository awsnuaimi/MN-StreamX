package com.yourapp.iptv.player.vod

import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi

/** Signed provider endpoints can omit the HLS suffix from their URL. */
@androidx.annotation.OptIn(UnstableApi::class)
internal fun vodMediaItem(url: String, extension: String): MediaItem = MediaItem.Builder()
    .setUri(url)
    .apply { if (extension.equals("m3u8",ignoreCase = true)) setMimeType(MimeTypes.APPLICATION_M3U8) }
    .build()