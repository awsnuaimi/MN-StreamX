package com.yourapp.iptv.data.caption

import ai.onnxruntime.*
import com.yourapp.iptv.domain.repository.CaptionTranslator
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean

internal class OnnxCaptionTranslator(private val files: VerifiedModelFiles) : CaptionTranslator {
    private var target = "en"
    private var environment: OrtEnvironment? = null
    private var encoder: OrtSession? = null
    private var decoder: OrtSession? = null
    private var tokenizer: MarianTokenizer? = null
    private val lock = Any()
    private var active: OrtSession.RunOptions? = null
    @Volatile private var cancelled = false

    override suspend fun prepare(target: String, progress: (Int) -> Unit) {
        this.target = target
        if (target == "en") return
        val folder = files.prepare(CaptionModelCatalog.translation, progress)
        currentCoroutineContext().ensureActive()
        val env = OrtEnvironment.getEnvironment()
        environment = env
        OrtSession.SessionOptions().use { options ->
            options.setIntraOpNumThreads(1); options.setInterOpNumThreads(1)
            options.setMemoryPatternOptimization(false); options.setCPUArenaAllocator(false)
            encoder = env.createSession(folder.resolve("onnx/encoder_model_quantized.onnx").absolutePath, options)
            decoder = env.createSession(folder.resolve("onnx/decoder_model_merged_quantized.onnx").absolutePath, options)
        }
        tokenizer = MarianTokenizer(folder)
    }
    override suspend fun translate(text: String): String {
        currentCoroutineContext().ensureActive()
        if (target == "en") return text
        check(!cancelled)
        val codec = checkNotNull(tokenizer)
        val expired = AtomicBoolean(false)
        val timeout = deadlines.schedule({ expired.set(true); cancel() }, 8, TimeUnit.SECONDS)
        try {
            val ids = codec.encode(text, target)
            val translated = MarianDecoder(checkNotNull(environment), checkNotNull(decoder), ::run)
                .decode(ids, checkNotNull(encoder))
            currentCoroutineContext().ensureActive()
            check(!cancelled)
            return codec.decode(translated)
        } catch (error: Exception) {
            currentCoroutineContext().ensureActive()
            if (expired.get()) throw TimeoutException("Local translation exceeded its time budget")
            throw error
        } finally { timeout.cancel(false) }
    }
    private fun run(session: OrtSession, input: Map<String, OnnxTensorLike>): OrtSession.Result {
        val options = OrtSession.RunOptions()
        synchronized(lock) { if (cancelled) options.setTerminate(true); active = options }
        return try { session.run(input, options) }
        finally { synchronized(lock) { active = null; options.close() } }
    }
    override fun cancel() { synchronized(lock) { cancelled = true; active?.setTerminate(true) } }
    override fun close() {
        cancel(); tokenizer?.close(); tokenizer = null
        decoder?.close(); decoder = null; encoder?.close(); encoder = null
    }
    private companion object {
        val deadlines = Executors.newSingleThreadScheduledExecutor { action -> Thread(action, "caption-timeout").apply { isDaemon = true } }
    }
}