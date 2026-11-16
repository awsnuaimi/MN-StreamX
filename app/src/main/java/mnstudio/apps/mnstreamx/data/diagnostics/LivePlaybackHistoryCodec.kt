package mnstudio.apps.mnstreamx.data.diagnostics

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import mnstudio.apps.mnstreamx.domain.model.LiveRecoveryPhase
import mnstudio.apps.mnstreamx.domain.performance.LivePlaybackError
import mnstudio.apps.mnstreamx.domain.performance.LivePlaybackErrorOrigin
import mnstudio.apps.mnstreamx.domain.performance.LivePlaybackErrorStage
import mnstudio.apps.mnstreamx.domain.performance.LivePlaybackHealthSession
import mnstudio.apps.mnstreamx.domain.performance.LivePlaybackHistoryEntry
import mnstudio.apps.mnstreamx.domain.performance.LivePlaybackInterruption
import mnstudio.apps.mnstreamx.domain.performance.LivePlaybackInterruptionError
import mnstudio.apps.mnstreamx.domain.performance.LivePlaybackInterruptionOutcome
import mnstudio.apps.mnstreamx.domain.performance.LivePlaybackInterruptionTrigger

/** A versioned format containing numeric observations and fixed enum names. */
internal object LivePlaybackHistoryCodec {
    const val MAX_BYTES = 16 * 1024L
    private const val MAGIC = 0x4D4E5849
    private const val VERSION = 1

    fun write(stream: OutputStream, entries: List<LivePlaybackHistoryEntry>) {
        val output = DataOutputStream(stream.buffered())
        output.writeInt(MAGIC)
        output.writeInt(VERSION)
        output.writeInt(entries.size)
        entries.forEach { entry ->
            output.writeLong(entry.startedAtMs)
            output.writeLong(entry.observedAtMs)
            output.writeUTF(entry.trigger.name)
            output.writeUTF(entry.phase.name)
            output.writeBoolean(entry.httpOpened)
            output.writeInterruption(entry.interruption)
        }
        output.flush()
    }

    fun read(stream: InputStream): List<LivePlaybackHistoryEntry> {
        val input = DataInputStream(stream.buffered())
        require(input.readInt() == MAGIC && input.readInt() == VERSION)
        val count = input.readInt()
        require(count in 0..LivePlaybackHistoryEntry.LIMIT)
        val entries = List(count) {
            LivePlaybackHistoryEntry(
                startedAtMs = input.readLong(),
                observedAtMs = input.readLong(),
                trigger = enumValueOf<LivePlaybackInterruptionTrigger>(input.readUTF()),
                phase = enumValueOf<LiveRecoveryPhase>(input.readUTF()),
                httpOpened = input.readBoolean(),
                interruption = input.readInterruption()
            )
        }
        require(input.read() == -1)
        return entries
    }

    private fun DataOutputStream.writeInterruption(item: LivePlaybackInterruption) {
        writeLong(item.durationMs)
        writeLong(item.networkWaitMs)
        writeLong(item.retryAfterWaitMs)
        writeLong(item.retryDelayMs)
        writeLong(item.loadingMs)
        writeInt(item.attempts)
        writeUTF(item.outcome.name)
        writeLong(item.connectingMs)
        writeInt(item.errorCount)
        val details = item.errors.take(LivePlaybackHealthSession.MAX_ERROR_DETAILS)
        writeInt(details.size)
        details.forEach { detail ->
            writeUTF(detail.error.origin.name)
            writeInt(detail.error.code)
            writeInt(detail.error.httpStatus ?: 0)
            writeBoolean(detail.error.retryable)
            writeLong(detail.offsetMs)
            writeUTF(detail.stage.name)
            writeInt(detail.attempt)
        }
    }

    private fun DataInputStream.readInterruption() = LivePlaybackInterruption(
        durationMs = readLong(),
        networkWaitMs = readLong(),
        retryAfterWaitMs = readLong(),
        retryDelayMs = readLong(),
        loadingMs = readLong(),
        attempts = readInt(),
        outcome = enumValueOf<LivePlaybackInterruptionOutcome>(readUTF()),
        connectingMs = readLong(),
        errorCount = readInt(),
        errors = readErrors()
    )

    private fun DataInputStream.readErrors(): List<LivePlaybackInterruptionError> {
        val count = readInt()
        require(count in 0..LivePlaybackHealthSession.MAX_ERROR_DETAILS)
        return List(count) {
            LivePlaybackInterruptionError(
                error = LivePlaybackError(
                    origin = enumValueOf<LivePlaybackErrorOrigin>(readUTF()),
                    code = readInt(),
                    httpStatus = readInt().takeIf { it in 100..599 },
                    retryable = readBoolean()
                ),
                offsetMs = readLong(),
                stage = enumValueOf<LivePlaybackErrorStage>(readUTF()),
                attempt = readInt()
            )
        }
    }
}