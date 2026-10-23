package com.yourapp.iptv.data.caption

import com.google.gson.JsonParser
import java.io.File

/** Token and cache dimensions belong to the selected model, never to the UI language. */
internal data class MarianModelConfig(val vocabulary: Int, val width: Int, val heads: Int,
    val start: Long, val eos: Long, val pad: Long) {
    init {
        require(vocabulary in 1000..100000 && width in 128..1024 && heads in 1..16 && width % heads == 0)
        require(listOf(start, eos, pad).all { it in 0 until vocabulary.toLong() })
    }
    companion object {
        fun read(folder: File): MarianModelConfig {
            val file = folder.resolve("config.json")
            require(file.length() in 1..65536)
            val json = file.reader().use { JsonParser.parseReader(it).asJsonObject }
            require(json.get("model_type").asString == "marian")
            return MarianModelConfig(json.get("vocab_size").asInt, json.get("d_model").asInt,
                json.get("decoder_attention_heads").asInt, json.get("decoder_start_token_id").asLong,
                json.get("eos_token_id").asLong, json.get("pad_token_id").asLong)
        }
    }
}