package com.yourapp.iptv.data.source.m3u

import com.yourapp.iptv.data.model.ChannelDto

object M3uParser {
    fun parse(content: String): List<ChannelDto> {
        val channels = mutableListOf<ChannelDto>()
        val lines = content.lines()
        var index = 0

        while (index < lines.size) {
            val line = lines[index]
            if (line.startsWith("#EXTINF:")) {
                // استخراج الاسم من السطر
                val name = line.substringAfterLast(",").trim()
                // السطر التالي هو الرابط
                val url = if (index + 1 < lines.size) lines[index + 1].trim() else ""
                
                if (url.isNotEmpty() && url.startsWith("http")) {
                    channels.add(
                        ChannelDto(
                            id = "ch_${channels.size}",
                            name = name,
                            url = url,
                            logo = null,
                            group = null
                        )
                    )
                }
                index += 2 // تخطي السطر التالي (الرابط)
            } else {
                index++
            }
        }

        return channels
    }
}