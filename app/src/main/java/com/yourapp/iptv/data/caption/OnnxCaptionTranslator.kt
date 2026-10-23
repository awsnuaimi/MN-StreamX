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

/** One selected language pack, mapped uint8 heads, and serial four-hypothesis decoding. */
internal class OnnxCaptionTranslator(private val files: VerifiedModelFiles,
    private val device: CaptionDevice = CaptionDevice { true },
    private val budgetMillis: () -> Long = { 8000L }) : CaptionTranslator {
    private var target = "en"
    private var folder: File? = null
    private var encoderFile: File? = null
    private var decoderFile: File? = null
    private var cachedFile: File? = null
    private var decoder: MappedCaptionSession? = null
    private var cached: MappedCaptionSession? = null
    private var tokenizer: MarianTokenizer? = null
    private var config: MarianModelConfig? = null
    private val nativeMemory by lazy { NativeWhisper() }
    private val calls = CaptionCallControl()

    override suspend fun download(target: String, progress: (Int) -> Unit) {
        if (this.target != target) reset()
        this.target = target
        folder = if (target == "en") null else files.prepare(CaptionTranslationCatalog.forTarget(target), progress)
    }
    override suspend fun prepare(target: String, progress: (Int) -> Unit) {
        if (this.target != target || (target != "en" && folder == null)) download(target, progress)
        if (target == "en" || tokenizer != null) return
        val directory = checkNotNull(folder)
        val bundle = CaptionTranslationCatalog.forTarget(target)
        val settings = MarianModelConfig.read(directory)
        // Preparation streams a maximum of two 2 MiB transpose blocks, never a full float head.
        requireMemory(CaptionResourcePolicy.MAPPED_DECODER)
        val encoder = CaptionTranslationCatalog.encoder(bundle)
        encoderFile = OnnxMappedModelCache().prepare(directory.resolve(encoder.path), encoder.sha256)
        suspend fun prepareDecoder(asset: ModelAsset): File {
            requireMemory(CaptionResourcePolicy.MAPPED_DECODER)
            val mapped = OnnxMappedModelCache(export = MarianLanguageProjection(settings)::export,
                variant = "language-beam-v1", addedWeightBytes = 32 * CaptionResourcePolicy.MIB)
                .prepare(directory.resolve(asset.path), asset.sha256)
            nativeMemory.trimMemory()
            requireMemory(CaptionResourcePolicy.MAPPED_DECODER)
            return OnnxOptimizedModelCache().prepare(mapped, asset.sha256)
        }
        decoderFile = prepareDecoder(CaptionTranslationCatalog.decoder(bundle))
        cachedFile = CaptionTranslationCatalog.cachedDecoder(bundle)?.let { prepareDecoder(it) }
        currentCoroutineContext().ensureActive()
        config = settings
        tokenizer = MarianTokenizer(directory, settings, CaptionTranslationCatalog.prefix(target))
        nativeMemory.trimMemory()
    }
    override suspend fun translate(text: String): String {
        currentCoroutineContext().ensureActive()
        if (target == "en") return text
        if (text.isBlank()) return ""
        val codec = checkNotNull(tokenizer)
        val env = OrtEnvironment.getEnvironment()
        val ids = codec.encode(text)
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
                    fun firstSession(): OrtSession {
                        checkCall(); requireMemory(if (decoder == null) CaptionResourcePolicy.MAPPED_DECODER else CaptionResourcePolicy.DECODER_WARM)
                        return (decoder ?: MappedCaptionSession.open(env, checkNotNull(decoderFile)).also { decoder = it }).session
                    }
                    fun cachedSession(): OrtSession {
                        val file = cachedFile ?: return firstSession()
                        // The Polish export separates the first and cached decoder; keep only one session resident.
                        decoder?.close(); decoder = null
                        checkCall(); requireMemory(if (cached == null) CaptionResourcePolicy.MAPPED_DECODER else CaptionResourcePolicy.DECODER_WARM)
                        return (cached ?: MappedCaptionSession.open(env, file).also { cached = it }).session
                    }
                    val timeout = deadlines.schedule({ calls.expire(call) }, budget, TimeUnit.MILLISECONDS)
                    try {
                        val shape = longArrayOf(1, ids.size.toLong())
                        OnnxTensor.createTensor(env, LongBuffer.wrap(ids), shape).use { input ->
                        OnnxTensor.createTensor(env, LongBuffer.wrap(LongArray(ids.size) { 1 }), shape).use { mask ->
                            releaseWorkingMemory()
                            requireMemory(CaptionResourcePolicy.ENCODER)
                            val encoded = MappedCaptionSession.open(env, checkNotNull(encoderFile)).use { encoder ->
                                run(encoder.session, mapOf("input_ids" to input, "attention_mask" to mask))
                            }
                            encoded.use { result ->
                                nativeMemory.trimMemory()
                                currentCoroutineContext().ensureActive()
                                val generated = MarianBeamDecoder(env, checkNotNull(config), ::firstSession, ::cachedSession, ::run)
                                    .decode(mask, result[0] as OnnxTensor)
                                currentCoroutineContext().ensureActive()
                                checkCall()
                                return codec.decode(generated)
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
        val first = decoder; decoder = null
        val past = cached; cached = null
        try { first?.close() } finally { past?.close() }
        if (tokenizer != null) nativeMemory.trimMemory()
    }
    override fun cancel() { calls.cancel() }
    private fun reset() {
        releaseWorkingMemory(); tokenizer?.close(); tokenizer = null
        folder = null; encoderFile = null; decoderFile = null; cachedFile = null; config = null
    }
    override fun close() { cancel(); reset() }
    private companion object {
        val deadlines = Executors.newSingleThreadScheduledExecutor { action ->
            Thread(action, "caption-timeout").apply { isDaemon = true }
        }
    }
}