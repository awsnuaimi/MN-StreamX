package com.yourapp.iptv.ui.component.player

import com.yourapp.iptv.domain.model.Channel
import java.security.MessageDigest

/** Stable, source-scoped cache names, without saving URLs or credentials in filenames. */
internal object ChannelFramePolicy {
    fun filename(channel: Channel): String {
        val source = "${channel.id}\u001f${channel.streamUrl}"
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(source.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xFF) }
        return "frame-$hash.jpg"
    }

    fun isFresh(modifiedAt: Long, now: Long, maxAgeMs: Long = 12L * 60L * 60L * 1000L): Boolean =
        modifiedAt > 0 && modifiedAt <= now && now - modifiedAt < maxAgeMs
}