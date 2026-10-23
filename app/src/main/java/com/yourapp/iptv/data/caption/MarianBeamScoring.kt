package com.yourapp.iptv.data.caption

import java.nio.FloatBuffer
import kotlin.math.exp
import kotlin.math.ln

/** Stable log probabilities, pad suppression, and a bounded no-repeat-trigram search. */
internal object MarianBeamScoring {
    data class Choice(val token: Long, val logProbability: Double)
    fun choices(scores: FloatBuffer, vocabulary: Int, pad: Long, history: List<Long>, count: Int): List<Choice> {
        require(vocabulary > 0 && scores.remaining() >= vocabulary && count in 1..16)
        val offset = scores.limit() - vocabulary
        var maximum = Double.NEGATIVE_INFINITY
        for (index in 0 until vocabulary) {
            val value = scores.get(offset + index).toDouble()
            require(value.isFinite()) { "Non-finite decoder scores" }
            if (value > maximum) maximum = value
        }
        var sum = 0.0
        for (index in 0 until vocabulary) sum += exp(scores.get(offset + index) - maximum)
        val denominator = maximum + ln(sum)
        val banned = mutableSetOf<Long>()
        if (history.size >= 2) {
            for (index in 0 until history.size - 2) {
                if (history[index] == history[history.size - 2] && history[index + 1] == history.last())
                    banned += history[index + 2]
            }
        }
        val best = ArrayList<Choice>(count)
        for (index in 0 until vocabulary) {
            if (index.toLong() == pad || index.toLong() in banned) continue
            val score = scores.get(offset + index) - denominator
            if (best.size == count && score <= best.last().logProbability) continue
            var at = 0
            while (at < best.size && best[at].logProbability >= score) at++
            best.add(at, Choice(index.toLong(), score))
            if (best.size > count) best.removeAt(best.lastIndex)
        }
        return best
    }
}