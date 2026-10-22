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
import java.util.concurrent.atomic.AtomicBoolean

/** Read-only mapped weights, a reusable decoder, and a short-lived encoder session. */
internal class OnnxCaptionTranslator(private val files: VerifiedModelFiles,
    private val device: CaptionDevice = CaptionDevice { true }) : CaptionTranslator {
    private var target = "en"
    private var verifiedFolder: File? = null
    private var encoderFile: File? = null
    private var decoderFile: File? = null
    private var decoder: MappedCaptionSession? = null
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
        check(!cancelled)
        val codec = checkNotNull(tokenizer)
        val env = OrtEnvironment.getEnvironment()
        val ids = codec.encode(text, target)
        val started = System.nanoTime()
        val expired = AtomicBoolean(false)
        val timeout = deadlines.schedule({ expired.set(true); cancel() }, 8, TimeUnit.SECONDS)
        try {
            OnnxTensor.createTensor(env, LongBuffer.wrap(ids), longArrayOf(1, ids.size.toLong())).use { input ->
            OnnxTensor.createTensor(env, LongBuffer.wrap(LongArray(ids.size) { 1 }), longArrayOf(1, ids.size.toLong())).use { mask ->
                if (!device.assess(CaptionResourcePolicy.ENCODER).allowed) releaseWorkingMemory()
                requireMemory(CaptionResourcePolicy.ENCODER)
                val encoded = MappedCaptionSession.open(env, checkNotNull(encoderFile)).use { encoder ->
                    run(encoder.session, mapOf("input_ids" to input, "attention_mask" to mask))
                }
                encoded.use { result ->
                    nativeMemory.trimMemory()
                    currentCoroutineContext().ensureActive()
                    check(!cancelled)
                    requireMemory(if (decoder == null) CaptionResourcePolicy.MAPPED_DECODER else CaptionResourcePolicy.DECODER_WARM)
                    val session = decoder ?: MappedCaptionSession.open(env, checkNotNull(decoderFile))
                        .also { decoder = it }
                    val generated = MarianDecoder(env, session.session, ::run).decode(mask, result[0] as OnnxTensor)
                    currentCoroutineContext().ensureActive()
                    check(!cancelled)
                    return codec.decode(generated)
                }
            } }
        } catch (error: Exception) {
            currentCoroutineContext().ensureActive()
            if (expired.get()) throw CaptionDeadlineException(CaptionStage.TRANSLATION,
                (System.nanoTime() - started) / 1000000, 8000)
            throw error
        } finally { timeout.cancel(false); nativeMemory.trimMemory() }
    }
    private fun requireMemory(stage: String) {
        val check = device.assess(stage)
        if (!check.allowed) throw CaptionMemoryException(check)
    }
    private fun run(session: OrtSession, input: Map<String, OnnxTensorLike>): OrtSession.Result {
        val options = OrtSession.RunOptions()
        synchronized(lock) { if (cancelled) options.setTerminate(true); active = options }
        return try { session.run(input, options) }
        finally { synchronized(lock) { active = null; options.close() } }
    }
    override fun releaseWorkingMemory() {
        decoder?.close(); decoder = null
        if (tokenizer != null) nativeMemory.trimMemory()
    }
    override fun cancel() { synchronized(lock) { cancelled = true; active?.setTerminate(true) } }
    override fun close() {
        cancel(); releaseWorkingMemory(); tokenizer?.close(); tokenizer = null
        verifiedFolder = null; encoderFile = null; decoderFile = null
    }
    private companion object {
        val deadlines = Executors.newSingleThreadScheduledExecutor { action -> Thread(action, "caption-timeout").apply { isDaemon = true } }
    }
}