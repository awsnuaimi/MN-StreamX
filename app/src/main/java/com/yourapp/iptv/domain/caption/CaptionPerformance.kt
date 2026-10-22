package com.yourapp.iptv.domain.caption

import com.yourapp.iptv.domain.model.*
import java.util.concurrent.TimeoutException

class CaptionDeadlineException(val stage: CaptionStage, val elapsedMillis: Long, val budgetMillis: Long) :
    TimeoutException("Caption stage exceeded its processing budget")

/** One warm-up window, then judge sustainable work against actual audio arrival cadence. */
class CaptionThroughput {
    private var previousStartUs: Long? = null
    private var slow = 0
    fun assess(window: AudioWindow, elapsedMillis: Long, positionUs: Long): CaptionProgress? {
        fun stopped(stage: CaptionStage, elapsed: Long, budget: Long) = CaptionProgress(CaptionStatus.SLOW_DEVICE,
            stage = stage, elapsedMillis = elapsed, budgetMillis = budget)
        if (elapsedMillis > 10000) return stopped(CaptionStage.THROUGHPUT, elapsedMillis, 10000)
        if (positionUs != Long.MIN_VALUE) {
            val lag = ((positionUs - window.startUs) / 1000 - window.durationMillis).coerceAtLeast(0)
            if (lag > 8000) return stopped(CaptionStage.LAG, lag, 8000)
        }
        val previous = previousStartUs
        previousStartUs = window.startUs
        if (previous == null) return null
        val cadence = if (window.startUs > previous) ((window.startUs - previous) / 1000).coerceAtMost(10000) else 0
        val budget = maxOf(window.durationMillis, cadence) * 6 / 5
        slow = if (elapsedMillis > budget) slow + 1 else 0
        return if (slow >= 2) stopped(CaptionStage.THROUGHPUT, elapsedMillis, budget) else null
    }
}