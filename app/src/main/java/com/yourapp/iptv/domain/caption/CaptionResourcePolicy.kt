package com.yourapp.iptv.domain.caption

import com.yourapp.iptv.domain.model.CaptionStatus
import com.yourapp.iptv.domain.model.CaptionStage
import java.io.IOException

/** A RAM decision, independent of the bytes available for downloading files. */
data class CaptionResourceCheck(val allowed: Boolean, val failure: CaptionStatus = CaptionStatus.LOW_MEMORY,
    val availableMb: Long? = null, val requiredMb: Long? = null,
    val stage: CaptionStage? = null, val systemThresholdMb: Long? = null)

object CaptionResourcePolicy {
    const val MIB = 1048576L
    const val STORAGE_RESERVE = 16 * MIB
    // Application headroom for playback and allocations outside the inference stage.
    // This is a conservative admission estimate, not a measurement of peak native RAM.
    const val PLAYBACK_HEADROOM = 96 * MIB
    const val SPEECH = "speech-window"
    const val ENCODER = "text-encoder"
    const val DECODER = "text-decoder"
    const val RUNNING = "running"
    const val SPEECH_WARM = "speech-warm"
    const val MAPPED_DECODER = "mapped-decoder"
    const val DECODER_WARM = "decoder-warm"
    fun memory(target: String, availableBytes: Long, systemThresholdBytes: Long,
        systemLowMemory: Boolean): CaptionResourceCheck {
        // Incremental estimates for serial inference. Keep these separate from Android's
        // background-process reclaim threshold: that value is not an allocation by us.
        val allocationMb = when (target) {
            RUNNING -> 32L
            SPEECH_WARM -> 64L
            // External weights avoid the extra protobuf-to-tensor copy of the original model.
            MAPPED_DECODER -> 128L
            DECODER_WARM -> 96L
            SPEECH, ENCODER, "en" -> 128L
            DECODER -> 192L
            else -> 256L
        }
        val allocation = allocationMb * MIB
        val required = PLAYBACK_HEADROOM + allocation
        val available = availableBytes.coerceAtLeast(0)
        val stage = when (target) {
            SPEECH, SPEECH_WARM, "en" -> CaptionStage.SPEECH
            else -> CaptionStage.TRANSLATION
        }
        // Android's live pressure signal remains an independent veto. A large threshold
        // must not silently inflate the caption budget (e.g. 900 + 128 = 1028 MiB).
        return CaptionResourceCheck(!systemLowMemory && available > required,
            if (systemLowMemory) CaptionStatus.MEMORY_PRESSURE else CaptionStatus.LOW_MEMORY,
            available / MIB, ceilingMb(required), stage, ceilingMb(systemThresholdBytes.coerceAtLeast(0)))
    }
    fun ceilingMb(bytes: Long) = bytes / MIB + if (bytes % MIB == 0L) 0 else 1
}

class CaptionStorageException(val availableBytes: Long, val requiredBytes: Long) : IOException("Insufficient model storage")
class CaptionMemoryException(val check: CaptionResourceCheck) : IOException("Insufficient caption working memory")