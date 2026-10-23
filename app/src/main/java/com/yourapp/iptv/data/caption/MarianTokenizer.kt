package com.yourapp.iptv.data.caption

import com.google.gson.stream.JsonReader
import java.io.File

/** The selected bilingual model defines its own IDs and independent source/target tokenizers. */
internal class MarianTokenizer(folder: File, private val config: MarianModelConfig,
    private val prefix: String?) : AutoCloseable {
    private val native = NativePieces()
    private val vocabulary = HashMap<String, Int>(config.vocabulary * 4 / 3 + 1)
    private val decoder = arrayOfNulls<String>(config.vocabulary)
    private var source = 0L
    private var target = 0L
    private val unknown: Int
    init {
        folder.resolve("vocab.json").reader().buffered().use { reader ->
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
        require(vocabulary.size == config.vocabulary)
        unknown = vocabulary.getValue("<unk>")
        require(vocabulary.getValue("</s>").toLong() == config.eos)
        if (prefix != null) require(vocabulary.containsKey(prefix))
        try {
            source = native.create(folder.resolve("source.spm").absolutePath)
            target = native.create(folder.resolve("target.spm").absolutePath)
            check(source != 0L && target != 0L)
        } catch (error: Throwable) { close(); throw error }
    }
    fun encode(text: String): LongArray {
        check(source != 0L)
        require(text.length <= 2000) { "Caption text exceeds the bounded input window" }
        val pieces = native.encode(source, text)
        return MarianTokenIds.encode(pieces, vocabulary, unknown, config.eos, prefix)
    }
    fun decode(ids: List<Long>): String {
        check(target != 0L)
        val pieces = ids.takeWhile { it != config.eos }.filter { it != config.pad }
            .map { requireNotNull(decoder.getOrNull(it.toInt())) }.toTypedArray()
        return native.decode(target, pieces).trim()
    }
    override fun close() {
        if (source != 0L) { native.release(source); source = 0 }
        if (target != 0L) { native.release(target); target = 0 }
    }
}