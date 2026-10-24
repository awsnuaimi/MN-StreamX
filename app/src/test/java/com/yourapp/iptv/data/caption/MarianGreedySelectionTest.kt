package com.yourapp.iptv.data.caption

import org.junit.Assert.*
import org.junit.Test
import java.nio.FloatBuffer

class MarianGreedySelectionTest {
    @Test fun highestScoreWinsInOneLinearPass() {
        assertEquals(2L, MarianGreedySelection.next(
            FloatBuffer.wrap(floatArrayOf(0f, 1f, 4f, 3f)), 4, 0, 3, emptyList()))
    }
    @Test fun padAndDecoderStartCannotAppearAsCaptions() {
        assertEquals(1L, MarianGreedySelection.next(
            FloatBuffer.wrap(floatArrayOf(100f, 2f, 99f, 1f)), 4, 0, 2, emptyList()))
    }
    @Test fun repeatedTrigramIsPreventedButOrdinaryRepetitionIsAllowed() {
        val scores = FloatBuffer.wrap(floatArrayOf(0f, 6f, 5f, 1f, 0f))
        assertEquals(1L, MarianGreedySelection.next(scores, 5, 0, 4, listOf(1, 2)))
        assertEquals(2L, MarianGreedySelection.next(scores, 5, 0, 4, listOf(1, 2, 1, 2)))
    }
    @Test fun consecutiveTokenLoopCannotRunForever() {
        assertEquals(2L, MarianGreedySelection.next(FloatBuffer.wrap(floatArrayOf(0f, 8f, 7f, 0f)),
            4, 0, 3, listOf(1, 1, 1)))
    }
    @Test(expected = IllegalArgumentException::class)
    fun malformedScoresFailRatherThanProduceGibberish() {
        MarianGreedySelection.next(FloatBuffer.wrap(floatArrayOf(0f, Float.NaN, 2f)),
            3, 0, 2, emptyList())
    }
}