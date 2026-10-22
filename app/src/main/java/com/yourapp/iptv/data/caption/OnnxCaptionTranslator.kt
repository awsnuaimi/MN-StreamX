package com.yourapp.iptv.data.caption

import ai.onnxruntime.*
import com.yourapp.iptv.domain.caption.*
import com.yourapp.iptv.domain.repository.CaptionDevice
import com.yourapp.iptv.domain.repository.CaptionTranslator
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.nio.LongBuffer
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean

/** One ONNX session at a time. Output tensors remain owned by Result after the session closes. */
internal class OnnxCaptionTranslator(private val files: VerifiedModelFiles,
    private val device: CaptionDevice = CaptionDevice { true }) : CaptionTranslator {
    private var target = "en"
    private var verifiedFolder: File? = null
    private var decoderFile: File? = null
    private var tokenizer: MarianTokenizer? = null
    private val nativeMemory by lazy { NativeWhisper() }
    private val lock = Any()
    private var active: OrtSession.RunOptions? = null
    @Volatile private var cancelled = false

    override suspend fun download(target: String, progress: (Int) -> Unit) {
        this.target = target
        verifiedFolder = if (target == "en") null else files.prepare(CaptionModelCatalog.translation, progress)
    }
    override suspend fun prepare(target: String, progress: (Int) -> Unit) {
        if (this.target != target || (target != "en" && verifiedFolder == null)) download(target, progress)
        if (target == "en") return
        currentCoroutineContext().ensureActive()
        val folder = checkNotNull(verifiedFolder)
        decoderFile = MarianLowMemoryModel.prepare(folder)
        currentCoroutineContext().ensureActive()
        tokenizer = MarianTokenizer(folder)
    }
    override suspend fun translate(text: String): String {
        currentCoroutineContext().ensureActive()
        if (target == "en") return text
        check(!cancelled)
        val codec = checkNotNull(tokenizer)
        val env = OrtEnvironment.getEnvironment()
        val ids = codec.encode(text, target)
        val expired = AtomicBoolean(false)
        val timeout = deadlines.schedule({ expired.set(true); cancel() }, 8, TimeUnit.SECONDS)
        try {
            OnnxTensor.createTensor(env, LongBuffer.wrap(ids), longArrayOf(1, ids.size.toLong())).use { input ->
            OnnxTensor.createTensor(env, LongBuffer.wrap(LongArray(ids.size) { 1 }), longArrayOf(1, ids.size.toLong())).use { mask ->
                requireMemory(CaptionResourcePolicy.ENCODER)
                val encoded = options().use { options ->
                    env.createSession(checkNotNull(verifiedFolder).resolve("onnx/encoder_model_quantized.onnx").absolutePath, options).use { encoder ->
                        run(encoder, mapOf("input_ids" to input, "attention_mask" to mask))
                    }
                }
                encoded.use { result ->
                    nativeMemory.trimMemory()
                    currentCoroutineContext().ensureActive()
                    check(!cancelled)
                    requireMemory(CaptionResourcePolicy.DECODER)
                    return options().use { options ->
                        env.createSession(checkNotNull(decoderFile).absolutePath, options).use { decoder ->
                            val generated = MarianDecoder(env, decoder, ::run).decode(mask, result[0] as OnnxTensor)
                            currentCoroutineContext().ensureActive()
                            check(!cancelled)
                            codec.decode(generated)
                        }
                    }
                }
            } }
        } catch (error: Exception) {
            currentCoroutineContext().ensureActive()
            if (expired.get()) throw TimeoutException("Local translation exceeded its time budget")
            throw error
        } finally { timeout.cancel(false); nativeMemory.trimMemory() }
    }
    private fun requireMemory(stage: String) {
        val check = device.assess(stage)
        if (!check.allowed) throw CaptionMemoryException(check)
    }
    private fun options() = OrtSession.SessionOptions().apply {
        setIntraOpNumThreads(1); setInterOpNumThreads(1)
        setMemoryPatternOptimization(false); setCPUArenaAllocator(false)
        // Constant folding would materialize all float blocks at once and undo the memory saving.
        setOptimizationLevel(OrtSession.SessionOptions.OptLevel.NO_OPT)
        addConfigEntry("session.disable_prepacking", "1")
        addConfigEntry("session.intra_op.allow_spinning", "0")
        addConfigEntry("session.inter_op.allow_spinning", "0")
    }
    private fun run(session: OrtSession, input: Map<String, OnnxTensorLike>): OrtSession.Result {
        val options = OrtSession.RunOptions()
        synchronized(lock) { if (cancelled) options.setTerminate(true); active = options }
        return try { session.run(input, options) }
        finally { synchronized(lock) { active = null; options.close() } }
    }
    override fun cancel() { synchronized(lock) { cancelled = true; active?.setTerminate(true) } }
    override fun close() {
        cancel(); tokenizer?.close(); tokenizer = null; verifiedFolder = null; decoderFile = null
    }
    private companion object {
        val deadlines = Executors.newSingleThreadScheduledExecutor { action -> Thread(action, "caption-timeout").apply { isDaemon = true } }
    }
}