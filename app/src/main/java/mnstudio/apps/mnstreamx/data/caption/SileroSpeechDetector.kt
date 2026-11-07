package mnstudio.apps.mnstreamx.data.caption

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import mnstudio.apps.mnstreamx.domain.caption.CaptionSpeechDetector
import mnstudio.apps.mnstreamx.domain.caption.CaptionSpeechSpan
import java.io.File
import java.nio.FloatBuffer
import java.nio.LongBuffer

/** Pinned Silero v5.1.2 protocol: 512 samples + 64 context, state [2,1,128], scalar 16 kHz. */
internal class SileroSpeechDetector(file: File) : CaptionSpeechDetector {
    private val environment = OrtEnvironment.getEnvironment()
    private val options = OrtSession.SessionOptions().apply {
        setIntraOpNumThreads(1); setInterOpNumThreads(1)
        setCPUArenaAllocator(false); setMemoryPatternOptimization(false)
        setOptimizationLevel(OrtSession.SessionOptions.OptLevel.NO_OPT)
        addConfigEntry("optimization.disable_specified_optimizers", "CommonSubexpressionElimination")
        addConfigEntry("session.intra_op.allow_spinning", "0")
        addConfigEntry("session.inter_op.allow_spinning", "0")
    }
    private val session = try { environment.createSession(file.absolutePath, options) }
        catch (error: Throwable) { options.close(); throw error }
    private val calls = CaptionCallControl()
    private var closed = false

    override suspend fun speech(samples: FloatArray): CaptionSpeechSpan? {
        require(samples.size in 16000..96000)
        check(!closed)
        val context = FloatArray(64)
        val state = FloatArray(256)
        val frame = FloatArray(576)
        var first = -1; var last = -1; var count = 0; var run = 0; var longest = 0
        OrtSession.RunOptions().use { request ->
            val ticket = calls.begin { request.setTerminate(true) }
            try {
                OnnxTensor.createTensor(environment, LongBuffer.wrap(longArrayOf(16000)), longArrayOf()).use { rate ->
                    for (offset in samples.indices step 512) {
                        currentCoroutineContext().ensureActive(); calls.checkRunning(ticket)
                        context.copyInto(frame)
                        for (i in 0 until 512) frame[i + 64] = samples.getOrNull(offset + i)
                            ?.takeIf { it.isFinite() }?.coerceIn(-1f, 1f) ?: 0f
                        OnnxTensor.createTensor(environment, FloatBuffer.wrap(frame), longArrayOf(1, 576)).use { input ->
                            OnnxTensor.createTensor(environment, FloatBuffer.wrap(state), longArrayOf(2, 1, 128)).use { memory ->
                                session.run(mapOf("input" to input, "state" to memory, "sr" to rate), request).use { result ->
                                    val probability = (result[0] as OnnxTensor).floatBuffer.get(0)
                                    val next = (result[1] as OnnxTensor).floatBuffer
                                    check(next.remaining() == state.size); next.get(state)
                                    if (probability.isFinite() && probability >= 0.5f) {
                                        if (first < 0) first = offset
                                        last = minOf(offset + 512, samples.size); count++; run++
                                        longest = maxOf(longest, run)
                                    } else run = 0
                                }
                            }
                        }
                        frame.copyInto(context, 0, frame.size - context.size)
                    }
                }
                calls.checkRunning(ticket)
            } finally { calls.finish(ticket) }
        }
        return if (count >= 5 && longest >= 3) CaptionSpeechSpan(first, last).decodeRange(samples.size) else null
    }

    override fun cancel() = calls.cancel()
    override fun close() {
        if (closed) return
        closed = true; cancel()
        try { session.close() } finally { options.close() }
    }
}