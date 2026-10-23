package com.yourapp.iptv.data.caption

/** A target token is an atomic vocabulary ID, not text to split with SentencePiece. */
internal object MarianTokenIds {
    fun encode(pieces: Array<String>, vocabulary: Map<String, Int>, unknown: Int, eos: Long,
        prefix: String?): LongArray {
        require(pieces.size <= 190)
        val offset = if (prefix == null) 0 else 1
        return LongArray(pieces.size + offset + 1).also { ids ->
            if (prefix != null) ids[0] = vocabulary.getValue(prefix).toLong()
            pieces.forEachIndexed { index, piece -> ids[index + offset] = (vocabulary[piece] ?: unknown).toLong() }
            ids[ids.lastIndex] = eos
        }
    }
}