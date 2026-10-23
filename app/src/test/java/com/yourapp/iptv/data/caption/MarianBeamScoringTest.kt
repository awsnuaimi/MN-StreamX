package com.yourapp.iptv.data.caption

import org.junit.Assert.*
import org.junit.Test
import java.nio.FloatBuffer
import kotlin.math.exp

class MarianBeamScoringTest {
    @Test fun allVocabularyScoresContributeToNormalizationWhilePaddingCannotBeGenerated() {
        val choices = MarianBeamScoring.choices(FloatBuffer.wrap(floatArrayOf(1000f, 1001f, 1002f)), 3, 2, emptyList(), 2)
        assertEquals(listOf(1L, 0L), choices.map { it.token })
        val denominator = 1 + exp(-1.0) + exp(-2.0)
        assertEquals(exp(-1.0) / denominator, exp(choices.first().logProbability), 0.000001)
        assertTrue(choices.all { it.logProbability.isFinite() })
    }
    @Test fun scoresUseTheLastDecoderPositionAndStableOrderingOnTies() {
        val values = FloatBuffer.wrap(floatArrayOf(99f, 1f, 1f, 2f, 2f, 0f))
        assertEquals(listOf(0L, 1L), MarianBeamScoring.choices(values, 3, 2, emptyList(), 2).map { it.token })
    }
    @Test fun aRepeatedTrigramIsBlockedWithoutRemovingLegitimateSingleWordRepetitions() {
        val scores = FloatBuffer.wrap(floatArrayOf(0f, 5f, 4f, 1f))
        assertEquals(1L, MarianBeamScoring.choices(scores, 4, 3, listOf(1, 1), 1).single().token)
        assertEquals(2L, MarianBeamScoring.choices(scores, 4, 3, listOf(1, 1, 1), 1).single().token)
    }
    @Test(expected = IllegalArgumentException::class)
    fun nonFiniteScoresDoNotBecomePlausibleCaptions() {
        MarianBeamScoring.choices(FloatBuffer.wrap(floatArrayOf(Float.NaN, 1f)), 2, 1, emptyList(), 1)
    }
}