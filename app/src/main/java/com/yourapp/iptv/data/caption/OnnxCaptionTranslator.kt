package com.yourapp.iptv.data.caption

import ai.onnxruntime.*
import com.yourapp.iptv.domain.caption.*
import com.yourapp.iptv.domain.model.CaptionStage
import com.yourapp.iptv.domain.repository.CaptionDevice
import com.yourapp.iptv.domain.repository.CaptionTranslator
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.nio.LongBuffer
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Read-only mapped weights, a reusable decoder, and a short-lived encoder session. */
internal class OnnxCaptionTranslator(private val files: VerifiedModelFiles,
    private val device: CaptionDevice = CaptionDevice { true },
    private val budgetMillis: () -> Long = { 8000L }) : CaptionTranslator {
    private var target = "en"
    private var verifiedFolder: File? = null
    private var encoderFile: File? = null
    private var decoderFile: File? = null
    private var decoder: MappedCaptionSession? = null
    private var tokenizer: MarianTokenizer? = null
    private val nativeMemory by lazy { NativeWhisper() }
    private val calls = CaptionCallControl()

    override suspend fun download(target: String, progress: (Int) -> Unit) {
        this.target = target
        verifiedFolder = if (target == "en") null else files.prepare(CaptionModelCatalog.translation, progress)
    }
    override suspend fun prepare(target: String, progress: (Int) -> Unit) {
        if (this.target != target || (target != "en" && verifiedFolder == null)) download(target, progress)
        if (target == "en") return
        val folder = checkNotNull(verifiedFolder)
        requireMemory(CaptionResourcePolicy.DECODER)
        val source = MarianLowMemoryModel.prepare(folder)
        nativeMemory.trimMemory()
        val cache = OnnxMappedModelCache()
        val encoderAsset = CaptionModelCatalog.translation.assets.first { it.path.contains("encoder_model") }
        encoderFile = cache.prepare(folder.resolve(encoderAsset.path), encoderAsset.sha256)
        requireMemory(CaptionResourcePolicy.DECODER)
        val preparedDecoder = OnnxMappedModelCache(export = MarianPreparedProjection::export,
            variant = "live-v2", addedWeightBytes = 32 * CaptionResourcePolicy.MIB)
            .prepare(source, MarianLowMemoryModel.OUTPUT_SHA256)
        nativeMemory.trimMemory()
        requireMemory(CaptionResourcePolicy.DECODER)
        decoderFile = OnnxOptimizedModelCache().prepare(preparedDecoder, MarianLowMemoryModel.OUTPUT_SHA256)
        currentCoroutineContext().ensureActive()
        tokenizer = MarianTokenizer(folder)
        nativeMemory.trimMemory()
    }
    override suspend fun translate(text: String): String {
        currentCoroutineContext().ensureActive()
        if (target == "en") return text
        val codec = checkNotNull(tokenizer)
        val env = OrtEnvironment.getEnvironment()
        val ids = codec.encode(text, target)
        try {
            OrtSession.RunOptions().use { options ->
                val call = calls.begin { options.setTerminate(true) }
                try {
                    val started = System.nanoTime()
                    val budget = budgetMillis().coerceIn(1L, 8000L)
                    fun elapsed() = (System.nanoTime() - started) / 1000000
                    fun checkCall() {
                        calls.checkRunning(call)
                        if (call.expired || elapsed() >= budget)
                            throw CaptionDeadlineException(CaptionStage.TRANSLATION, elapsed(), budget)
                    }
                    fun run(owner: OrtSession, feed: Map<String, OnnxTensorLike>): OrtSession.Result {
                        checkCall()
                        return owner.run(feed, options)
                    }
                    val timeout = deadlines.schedule({ calls.expire(call) }, budget, TimeUnit.MILLISECONDS)
                    try {
                        val shape = longArrayOf(1, ids.size.toLong())
                        OnnxTensor.createTensor(env, LongBuffer.wrap(ids), shape).use { input ->
                        OnnxTensor.createTensor(env, LongBuffer.wrap(LongArray(ids.size) { 1 }), shape).use { mask ->
                            if (!device.assess(CaptionResourcePolicy.ENCODER).allowed) releaseWorkingMemory()
                            requireMemory(CaptionResourcePolicy.ENCODER)
                            val encoded = MappedCaptionSession.open(env, checkNotNull(encoderFile)).use { encoder ->
                                run(encoder.session, mapOf("input_ids" to input, "attention_mask" to mask))
                            }
                            encoded.use { result ->
                                nativeMemory.trimMemory()
                                currentCoroutineContext().ensureActive()
                                checkCall()
                                requireMemory(if (decoder == null) CaptionResourcePolicy.MAPPED_DECODER else CaptionResourcePolicy.DECODER_WARM)
                                val session = decoder ?: MappedCaptionSession.open(env, checkNotNull(decoderFile))
                                    .also { decoder = it }
                                val generated = MarianDecoder(env, session.session, ::run).decode(mask, result[0] as OnnxTensor)
                                currentCoroutineContext().ensureActive()
                                val decoded = codec.decode(generated)
                                checkCall()
                                return decoded
                            }
                        } }
                    } catch (error: Exception) {
                        currentCoroutineContext().ensureActive()
                        calls.checkRunning(call)
                        if (call.expired) throw CaptionDeadlineException(CaptionStage.TRANSLATION, elapsed(), budget)
                        throw error
                    } finally { timeout.cancel(false) }
                } finally { calls.finish(call) }
            }
        } finally { nativeMemory.trimMemory() }
    }
    private fun requireMemory(stage: String) {
        val check = device.assess(stage)
        if (!check.allowed) throw CaptionMemoryException(check)
    }
    override fun releaseWorkingMemory() {
        decoder?.close(); decoder = null
        if (tokenizer != null) nativeMemory.trimMemory()
    }
    override fun cancel() { calls.cancel() }
    override fun close() {
        cancel(); releaseWorkingMemory(); tokenizer?.close(); tokenizer = null
        verifiedFolder = null; encoderFile = null; decoderFile = null
    }
    private companion object {
        val deadlines = Executors.newSingleThreadScheduledExecutor { action -> Thread(action, "caption-timeout").apply { isDaemon = true } }
    }
}