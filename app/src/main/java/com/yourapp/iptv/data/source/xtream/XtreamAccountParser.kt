package com.yourapp.iptv.data.source.xtream

import com.google.gson.JsonParser
import com.yourapp.iptv.domain.model.PlaylistLoadException
import java.io.Reader
import java.util.Locale

internal object XtreamAccountParser {
    fun extension(reader: Reader): String {
        val root = JsonParser.parseReader(reader).asJsonObject
        val info = root.getAsJsonObject("user_info")
            ?: throw PlaylistLoadException("استجابة السيرفر لا تحتوي بيانات حساب Xtream.")
        val status = info.get("status")?.takeIf { it.isJsonPrimitive }?.asString
            ?.trim()?.lowercase(Locale.ROOT)
        when (status) {
            "expired" -> throw PlaylistLoadException("اشتراك Xtream منتهي الصلاحية.")
            "banned", "disabled" -> throw PlaylistLoadException("حساب Xtream موقوف أو معطّل لدى المزوّد.")
        }
        val auth = info.get("auth")?.takeIf { it.isJsonPrimitive }?.asString
        if (auth !in setOf("1", "true") || status != "active") {
            throw PlaylistLoadException("بيانات الدخول إلى Xtream غير صحيحة، أو الحساب غير نشط.")
        }
        val formats = info.get("allowed_output_formats")?.takeIf { it.isJsonArray }?.asJsonArray
            ?.mapNotNull { it.takeIf { value -> value.isJsonPrimitive }?.asString?.trim()?.lowercase(Locale.ROOT) }
        return when {
            formats == null || formats.isEmpty() -> "ts"
            "m3u8" in formats -> "m3u8"
            "ts" in formats -> "ts"
            else -> throw PlaylistLoadException("الحساب لا يتيح صيغة بث HLS أو TS المدعومة.")
        }
    }
}