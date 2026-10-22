package com.yourapp.iptv.data.caption

import com.google.gson.stream.JsonReader
import java.io.File

/** Streaming vocabulary, one lookup map and a compact reverse array. No JSON DOM or boxed reverse map. */
internal class MarianTokenizer(folder: File) : AutoCloseable {
    private val native = NativePieces()
    private val vocabulary = HashMap<String, Int>(85500)
    private val decoder = arrayOfNulls<String>(64110)
    init {
        File(folder, "vocab.json").reader().buffered().use { reader ->
            JsonReader(reader).use { json ->
                json.beginObject()
                while (json.hasNext()) {
                    val piece = json.nextName(); val id = json.nextInt()
                    require(id in decoder.indices && decoder[id] == null)
                    vocabulary[piece] = id; decoder[id] = piece
                }
                json.endObject()
            }
        }
        require(vocabulary.size == decoder.size)
    }
    private val unknown = vocabulary.getValue("<unk>")
    private var handle = native.create(File(folder, "source.spm").absolutePath)
    fun encode(text: String, target: String): LongArray {
        check(handle != 0L)
        val language = when (target) { "ar" -> "ara"; "de" -> "deu"; "pl" -> "pol"; "hi" -> "hin"; else -> "eng" }
        val prefix = vocabulary.getValue(">>$language<<")
        val pieces = native.encode(handle, text.take(1000)).take(94)
        return LongArray(pieces.size + 2).also { result ->
            result[0] = prefix.toLong()
            pieces.forEachIndexed { index, piece -> result[index + 1] = (vocabulary[piece] ?: unknown).toLong() }
        }
    }
    fun decode(ids: List<Long>): String = ids.mapNotNull { decoder.getOrNull(it.toInt()) }
        .filterNot { it.startsWith("<") || it.startsWith(">>") }
        .joinToString("").replace('▁', ' ').trim()
    override fun close() { if (handle != 0L) { native.release(handle); handle = 0 } }
}