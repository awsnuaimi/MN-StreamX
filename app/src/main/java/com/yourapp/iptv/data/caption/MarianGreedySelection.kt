package com.yourapp.iptv.data.caption

import java.nio.FloatBuffer

/** Single-pass selection for real-time decoding; avoids beam-width vocabulary scans. */
internal object MarianGreedySelection {
    fun next(scores: FloatBuffer, vocabulary: Int, pad: Long, start: Long, history: List<Long>): Long {
        require(vocabulary > 0 && scores.remaining() >= vocabulary)
        val offset = scores.limit() - vocabulary
        val banned = HashSet<Long>()
        banned += pad
        banned += start
        if (history.size >= 2) {
            val a = history[history.size - 2]
            val b = history.last()
            for (index in 0 until history.size - 2) {
                if (history[index] == a && history[index + 1] == b) banned += history[index + 2]
            }
        }
        // An excessive repeated subword is almost always a failed generation loop.
        if (history.size >= 3 && history.takeLast(3).distinct().size == 1) banned += history.last()
        var best = -1L
        var highest = Float.NEGATIVE_INFINITY
        for (index in 0 until vocabulary) {
            val value = scores.get(offset + index)
            require(value.isFinite()) { "Invalid Marian decoder score" }
            if (index.toLong() !in banned && value > highest) {
                highest = value
                best = index.toLong()
            }
        }
        check(best >= 0) { "No valid Marian token" }
        return best
    }
}