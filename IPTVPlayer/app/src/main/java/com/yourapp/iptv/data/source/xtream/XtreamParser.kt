package com.yourapp.iptv.data.source.xtream

import com.yourapp.iptv.data.model.XtreamChannelDto

object XtreamParser {
    fun parseChannels(json: String): List<XtreamChannelDto> {
        // استخدام مكتبة Gson لتحويل JSON إلى كائنات
        val gson = com.google.gson.Gson()
        val type = object : com.google.gson.reflect.TypeToken<List<XtreamChannelDto>>() {}.type
        return try {
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}