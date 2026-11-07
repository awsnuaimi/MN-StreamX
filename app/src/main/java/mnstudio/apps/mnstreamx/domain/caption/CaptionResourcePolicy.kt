package mnstudio.apps.mnstreamx.domain.caption

import mnstudio.apps.mnstreamx.domain.model.CaptionStatus
import mnstudio.apps.mnstreamx.domain.model.CaptionStage
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
    const val MOONSHINE_COLD = "moonshine-cold"
    const val MOONSHINE_STATE = "moonshine-state"
    const val TINY_COLD = "tiny-cold"
    const val TINY_STATE = "tiny-state"
    const val BASE_COLD = "base-cold"
    const val BASE_STATE = "base-state"
    const val VOSK_COLD = "vosk-cold"
    const val VOSK_STATE = "vosk-state"
    const val LOCAL_WARM = "local-warm"
    const val NATIVE_TEXT_COLD = "native-text-cold"
    private val localStages = setOf(TINY_COLD, TINY_STATE, MOONSHINE_COLD, MOONSHINE_STATE, BASE_COLD, BASE_STATE,
        VOSK_COLD, VOSK_STATE, LOCAL_WARM, NATIVE_TEXT_COLD)
    fun memory(target: String, availableBytes: Long, systemThresholdBytes: Long,
        systemLowMemory: Boolean): CaptionResourceCheck {
        // Incremental estimates for serial inference. Keep these separate from Android's
        // background-process reclaim threshold: that value is not an allocation by us.
        val allocationMb = when (target) {
            MOONSHINE_COLD, BASE_COLD, NATIVE_TEXT_COLD -> 160L
            TINY_COLD -> 128L
            TINY_STATE -> 64L
            VOSK_COLD -> 176L
            BASE_STATE -> 96L
            MOONSHINE_STATE -> 32L
            VOSK_STATE -> 16L
            LOCAL_WARM -> 8L
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
        // Available RAM already excludes resident playback and loaded model weights.
        val required = (if (target in localStages) 32 * MIB else PLAYBACK_HEADROOM) + allocation
        val available = availableBytes.coerceAtLeast(0)
        val stage = when (target) {
            SPEECH, SPEECH_WARM, "en", TINY_COLD, TINY_STATE, MOONSHINE_COLD, MOONSHINE_STATE, BASE_COLD, BASE_STATE,
                VOSK_COLD, VOSK_STATE, LOCAL_WARM -> CaptionStage.SPEECH
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