package com.yourapp.iptv.domain.caption

import com.yourapp.iptv.domain.model.CaptionStatus
import java.io.IOException

/** A RAM decision, independent of the bytes available for downloading files. */
data class CaptionResourceCheck(val allowed: Boolean, val failure: CaptionStatus = CaptionStatus.LOW_MEMORY,
    val availableMb: Long? = null, val requiredMb: Long? = null)

object CaptionResourcePolicy {
    const val MIB = 1048576L
    const val STORAGE_RESERVE = 16 * MIB
    fun memory(target: String, availableBytes: Long, systemThresholdBytes: Long,
        systemLowMemory: Boolean): CaptionResourceCheck {
        val budget = when (target) { "running" -> 96L; "en" -> 320L; else -> 576L } * MIB
        val threshold = systemThresholdBytes.coerceIn(0, Long.MAX_VALUE - 64 * MIB) + 64 * MIB
        val required = maxOf(budget, threshold)
        val available = availableBytes.coerceAtLeast(0)
        return CaptionResourceCheck(!systemLowMemory && available > required,
            if (systemLowMemory) CaptionStatus.MEMORY_PRESSURE else CaptionStatus.LOW_MEMORY,
            available / MIB, ceilingMb(required))
    }
    fun ceilingMb(bytes: Long) = bytes / MIB + if (bytes % MIB == 0L) 0 else 1
}

class CaptionStorageException(val availableBytes: Long, val requiredBytes: Long) : IOException("Insufficient model storage")