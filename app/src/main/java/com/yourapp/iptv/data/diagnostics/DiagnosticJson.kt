package com.yourapp.iptv.data.diagnostics

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.StringReader

internal object DiagnosticJson {
    fun root(bytes: ByteArray): JsonObject {
        val text = String(bytes,Charsets.UTF_8)
        JsonReader(StringReader(text)).use { reader ->
            var depth = 0;var tokens = 0
            while (reader.peek() != JsonToken.END_DOCUMENT) {
                require(++tokens <= 25_000)
                when (reader.peek()) {
                    JsonToken.BEGIN_ARRAY -> { reader.beginArray();require(++depth <= 24) }
                    JsonToken.BEGIN_OBJECT -> { reader.beginObject();require(++depth <= 24) }
                    JsonToken.END_ARRAY -> { reader.endArray();depth-- }
                    JsonToken.END_OBJECT -> { reader.endObject();depth-- }
                    JsonToken.NAME -> reader.nextName()
                    JsonToken.STRING,JsonToken.NUMBER -> reader.nextString()
                    JsonToken.BOOLEAN -> reader.nextBoolean()
                    JsonToken.NULL -> reader.nextNull()
                    else -> error("Unexpected JSON")
                }
            }
            require(depth == 0)
        }
        return JsonParser.parseString(text).asJsonObject
    }
}