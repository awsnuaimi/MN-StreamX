package com.yourapp.iptv.domain.caption

import com.yourapp.iptv.domain.model.CaptionStatus
import java.io.IOException

/** A RAM decision, independent of the bytes available for downloading files. */
data class CaptionResourceCheck(val allowed: Boolean, val failure: CaptionStatus = CaptionStatus.LOW_MEMORY,
    val availableMb: Long? = null, val requiredMb: Long? = null)

object CaptionResourcePolicy {
    const val MIB = 1048576L
    const val STORAGE_RESERVE = 16 * MIB
    const val SPEECH = "speech-window"
    const val ENCODER = "text-encoder"
    const val DECODER = "text-decoder"
    const val RUNNING = "running"
    fun memory(target: String, availableBytes: Long, systemThresholdBytes: Long,
        systemLowMemory: Boolean): CaptionResourceCheck {
        // Incremental estimates for staged engines, above Android's own low-memory floor.
        // The compact decoder head is evaluated in 8 MiB blocks, with no concurrent ASR.
        val allocationMb = when (target) {
            RUNNING -> 32L
            SPEECH, ENCODER, "en" -> 128L
            DECODER -> 192L
            else -> 256L
        }
        val floor = maxOf(96 * MIB, systemThresholdBytes.coerceAtLeast(0))
        val allocation = allocationMb * MIB
        val required = floor.coerceAtMost(Long.MAX_VALUE - allocation) + allocation
        val available = availableBytes.coerceAtLeast(0)
        return CaptionResourceCheck(!systemLowMemory && available > required,
            if (systemLowMemory) CaptionStatus.MEMORY_PRESSURE else CaptionStatus.LOW_MEMORY,
            available / MIB, ceilingMb(required))
    }
    fun ceilingMb(bytes: Long) = bytes / MIB + if (bytes % MIB == 0L) 0 else 1
}

class CaptionStorageException(val availableBytes: Long, val requiredBytes: Long) : IOException("Insufficient model storage")
class CaptionMemoryException(val check: CaptionResourceCheck) : IOException("Insufficient caption working memory")