package com.yourapp.iptv.data.caption

import com.google.gson.JsonParser
import java.io.File

/** Marian uses its vocabulary IDs, which differ from SentencePiece's internal IDs. */
internal class MarianTokenizer(folder: File) : AutoCloseable {
    private val native = NativePieces()
    private val vocabulary = File(folder, "vocab.json").reader().use { reader ->
        JsonParser.parseReader(reader).asJsonObject.entrySet().associate { it.key to it.value.asInt }
    }
    private val handle = native.create(File(folder, "source.spm").absolutePath)
    private val decoder = vocabulary.entries.associate { it.value to it.key }
    private val unknown = vocabulary.getValue("<unk>")
    fun encode(text: String, target: String): LongArray {
        val language = when (target) { "ar" -> "ara"; "de" -> "deu"; "pl" -> "pol"; "hi" -> "hin"; else -> "eng" }
        val prefix = vocabulary.getValue(">>$language<<")
        val pieces = native.encode(handle, text.take(1000)).take(94)
        return (listOf(prefix) + pieces.map { vocabulary[it] ?: unknown } + 0).map(Int::toLong).toLongArray()
    }
    fun decode(ids: List<Long>): String = ids.mapNotNull { decoder[it.toInt()] }
        .filterNot { it.startsWith("<") || it.startsWith(">>") }
        .joinToString("").replace('▁', ' ').trim()
    override fun close() { native.release(handle) }
}