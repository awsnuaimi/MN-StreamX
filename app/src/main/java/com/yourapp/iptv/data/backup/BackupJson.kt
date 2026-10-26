package com.yourapp.iptv.data.backup

import com.google.gson.JsonParser
import com.google.gson.JsonObject
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.yourapp.iptv.domain.model.*
import java.io.StringReader
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

internal object BackupJson {
    fun read(bytes: ByteArray, limit: Int): JsonObject {
        if (bytes.size > limit) throw BackupException(BackupProblem.TOO_LARGE)
        val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString()
        JsonReader(StringReader(text)).use { reader ->
            var depth = 0; var tokens = 0
            while (reader.peek() != JsonToken.END_DOCUMENT) {
                require(++tokens <= 300_000)
                when (reader.peek()) {
                    JsonToken.BEGIN_OBJECT -> { reader.beginObject(); require(++depth <= 32) }
                    JsonToken.END_OBJECT -> { reader.endObject(); depth-- }
                    JsonToken.BEGIN_ARRAY -> { reader.beginArray(); require(++depth <= 32) }
                    JsonToken.END_ARRAY -> { reader.endArray(); depth-- }
                    JsonToken.NAME -> reader.nextName()
                    JsonToken.STRING, JsonToken.NUMBER -> reader.nextString()
                    JsonToken.BOOLEAN -> reader.nextBoolean()
                    JsonToken.NULL -> reader.nextNull()
                    else -> error("Invalid JSON")
                }
            }
            require(depth == 0)
        }
        return JsonParser.parseString(text).asJsonObject
    }
}