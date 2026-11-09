package mnstudio.apps.mnstreamx.domain.caption

import mnstudio.apps.mnstreamx.domain.model.*
import mnstudio.apps.mnstreamx.domain.repository.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeoutException

class CaptionPipelineState(val progress: CaptionProgress = CaptionProgress(), val cue: CaptionCue? = null,
    val processingMillis: Long? = null, val timing: CaptionTiming? = null)

/** Serial inference, one waiting window, bounded latency, and generation-checked publication. */
class CaptionPipeline(private val scope: CoroutineScope, private val worker: CoroutineDispatcher,
    private val models: CaptionModels, private val speech: SpeechEngineFactory,
    private val translators: CaptionTranslatorFactory, private val device: CaptionDevice,
    private val cloudVault: CloudCredentialVault? = null,
    private val cloudFactory: CloudCaptionSessionFactory? = null,
    private val now: () -> Long = { System.nanoTime() / 1000000 },
    private val storageCoordinator: CaptionStorageCoordinator? = null) {
    private val state = MutableStateFlow(CaptionPipelineState())
    val uiState = state.asStateFlow()
    @Volatile private var generation = 0L
    private var job: Job? = null
    private val inferenceLock = Mutex()
    @Volatile private var engine: SpeechEngine? = null
    @Volatile private var translator: CaptionTranslator? = null
    @Volatile private var cloudSession: CloudCaptionSession? = null

    fun start(input: CaptionAudioInput, settings: CaptionSettings) {
        stop()
        val token = generation
        fun status(value: CaptionStatus, percent: Int? = null) {
            if (generation == token) state.value = CaptionPipelineState(CaptionProgress(value, percent))
        }
        fun ramAllowed(stage: String, release: (() -> Unit)? = null): Boolean {
            var check = device.assess(stage)
            if (!check.allowed && release != null) { release(); check = device.assess(stage) }
            if (!check.allowed && generation == token) state.value = CaptionPipelineState(CaptionProgress(
                check.failure, availableMb = check.availableMb, requiredMb = check.requiredMb,
                stage = check.stage, systemThresholdMb = check.systemThresholdMb))
            return check.allowed
        }
        var source = if (settings.source == "auto") CaptionLanguage.fromTag(input.languageHint)?.tag ?: "auto"
            else CaptionLanguage.fromTag(settings.source)?.tag ?: "auto"
        val textTarget = settings.target.tag
        status(CaptionStatus.PREPARING)
        job = scope.launch {
            val admitted = storageCoordinator?.beginSession() ?: true
            if (!admitted) { status(CaptionStatus.UNAVAILABLE); return@launch }
            try {
                if (settings.translationBackend == TranslationBackend.CLOUD) {
                    val vault = cloudVault
                    val factory = cloudFactory
                    if (vault == null || factory == null) {
                        status(CaptionStatus.CLOUD_SETUP_REQUIRED)
                    } else {
                        withContext(worker) { inferenceLock.withLock {
                            CloudCaptionRunner(vault, factory, now).run(input, settings,
                                { generation == token }, { value -> if (generation == token) state.value = value },
                                { cloudSession = it })
                        } }
                    }
                    return@launch
                }
                withContext(worker) { inferenceLock.withLock {
                    if (!device.supported()) { status(CaptionStatus.UNAVAILABLE); return@withLock }
                    status(CaptionStatus.DOWNLOADING, 0)
                    val model = models.prepare(source) { status(CaptionStatus.DOWNLOADING, it) }
                    currentCoroutineContext().ensureActive()
                    val text = translators.create()
                    translator = text
                    try {
                        text.prepareRoute(source, textTarget) { status(CaptionStatus.DOWNLOADING, it) }
                        var preparedSource = source
                        currentCoroutineContext().ensureActive()
                        if (!ramAllowed(models.memoryStage(source))) return@withLock
                        status(CaptionStatus.LOADING)
                        var voice = speech.open(model)
                        var voiceClosed = false
                        engine = voice
                        try {
                            currentCoroutineContext().ensureActive()
                            val recovery = CaptionWindowRecovery(CaptionWorkingSetPolicy.initialWindow(device.snapshot()))
                            val translationContinuity = CaptionTranslationContinuity()
                            input.limitWindow(recovery.windowMillis)
                            input.open().use { feed ->
                                var throughput = CaptionThroughput()
                                status(CaptionStatus.LISTENING)
                                while (true) {
                                    val window = withTimeoutOrNull(12000) { feed.windows.receiveCatching() }?.getOrNull()
                                    if (window == null) { status(CaptionStatus.UNAVAILABLE); break }
                                    currentCoroutineContext().ensureActive()
                                    if (window.samples.size < 16000 || !recovery.accepts(window) ||
                                        CaptionPresentation.tooOld(window, input.positionUs, CaptionPresentation.MAX_WINDOW_LAG_MS)) {
                                        if (window.samples.size >= 16000) CaptionDiagnosticJournal.record(
                                            "CAPTION_SKIP", 0, "reason=stale_or_oversize")
                                        continue
                                    }
                                    // A parked speech engine needs a fresh compute state. Do not let a
                                    // retained Marian decoder overlap that allocation on low-memory TVs.
                                    val retain = CaptionWorkingSetPolicy.retain(device.snapshot())
                                    if (!retain) text.releaseWorkingMemory()
                                    if (!ramAllowed(voice.memoryStage, text::releaseWorkingMemory)) break
                                    val started = now()
                                    val result = try {
                                        voice.transcribe(window.samples, source, false)
                                    } catch (error: CaptionDeadlineException) {
                                        currentCoroutineContext().ensureActive()
                                        if (token != generation) break
                                        if (error.stage != CaptionStage.SPEECH || !recovery.retry()) throw error
                                        // The aborted decoder state may be incomplete. Keep only its model
                                        // weights and request shorter *future* windows, never retry old audio.
                                        voice.recoverAfterDeadline()
                                        input.limitWindow(recovery.windowMillis)
                                        throughput = CaptionThroughput()
                                        if (generation == token) state.value = CaptionPipelineState(CaptionProgress(
                                            CaptionStatus.ADAPTING, stage = error.stage,
                                            elapsedMillis = error.elapsedMillis, budgetMillis = error.budgetMillis))
                                        continue
                                    }
                                    currentCoroutineContext().ensureActive()
                                    val speechEnded = now()
                                    if (!result.languageOnly && !CaptionTextQuality.usable(result.text)) {
                                        CaptionDiagnosticJournal.record("CAPTION_SKIP", 0, "reason=speech_empty")
                                        continue
                                    }
                                    val detected = CaptionLanguage.fromTag(result.language)?.tag
                                    if (detected == null) {
                                        CaptionDiagnosticJournal.record("CAPTION_SKIP", 0, "reason=unsupported_language")
                                        status(CaptionStatus.UNSUPPORTED_LANGUAGE); continue
                                    }
                                    if (source == "auto") {
                                        source = detected
                                        if (source != "pl") {
                                            if (engine === voice) engine = null
                                            voice.close(); voiceClosed = true
                                            status(CaptionStatus.DOWNLOADING, 0)
                                            val selected = models.prepare(source) { status(CaptionStatus.DOWNLOADING, it) }
                                            text.prepareRoute(source, textTarget) { status(CaptionStatus.DOWNLOADING, it) }
                                            preparedSource = source
                                            currentCoroutineContext().ensureActive()
                                            if (!ramAllowed(models.memoryStage(source))) break
                                            voice = speech.open(selected); voiceClosed = false; engine = voice
                                            currentCoroutineContext().ensureActive()
                                            throughput = CaptionThroughput()
                                            status(CaptionStatus.LISTENING)
                                            continue
                                        }
                                    }
                                    // Language detection returns metadata only; never translate or publish it.
                                    if (result.languageOnly) {
                                        if (preparedSource != source) {
                                            text.prepareRoute(source, textTarget) { status(CaptionStatus.DOWNLOADING, it) }
                                            preparedSource = source
                                        }
                                        throughput = CaptionThroughput()
                                        status(CaptionStatus.LISTENING)
                                        continue
                                    }
                                    val needsText = detected != textTarget && result.text.isNotBlank()
                                    if (needsText && !retain) voice.releaseWorkingMemory()
                                    val translationStarted = now()
                                    var delayed = false
                                    val translated = when {
                                        result.text.isBlank() -> ""
                                        detected == textTarget -> result.text
                                        !translationContinuity.shouldTranslate() -> {
                                            delayed = true
                                            ""
                                        }
                                        else -> try {
                                            if (preparedSource != detected) {
                                                text.prepareRoute(detected, textTarget) { status(CaptionStatus.DOWNLOADING, it) }
                                                preparedSource = detected
                                            }
                                            text.translate(result.text)
                                        } catch (error: CaptionDeadlineException) {
                                            currentCoroutineContext().ensureActive()
                                            if (token != generation) break
                                            if (error.stage != CaptionStage.TRANSLATION) throw error
                                            // One slow Marian request must not kill the live feed.
                                            // Keep the requested language strict: intermediate English
                                            // must never become the subtitle after a missed deadline.
                                            translationContinuity.deadline()
                                            text.releaseWorkingMemory()
                                            delayed = true
                                            ""
                                        }
                                    }
                                    currentCoroutineContext().ensureActive()
                                    val finished = now()
                                    CaptionDiagnosticJournal.record("CAPTION_TRANSLATE",
                                        (finished - translationStarted).coerceAtLeast(0),
                                        "required=$needsText,delayed=$delayed")
                                    if (needsText && !delayed && result.text.isNotBlank()) {
                                        translationContinuity.success(
                                            (finished - translationStarted).coerceAtLeast(0),
                                            window.durationMillis)
                                    }
                                    val timing = CaptionTiming(window.durationMillis, (speechEnded - started).coerceAtLeast(0),
                                        (finished - translationStarted).coerceAtLeast(0),
                                        (translationStarted - speechEnded).coerceAtLeast(0), (finished - started).coerceAtLeast(0))
                                    if (token != generation) break
                                    if (delayed) {
                                        throughput = CaptionThroughput()
                                        state.value = CaptionPipelineState(CaptionProgress(CaptionStatus.TRANSLATION_DELAYED), timing = timing)
                                        continue
                                    }
                                    val stopped = throughput.assess(window,
                                        if (needsText) timing.speechMillis else timing.totalMillis,
                                        if (needsText) Long.MIN_VALUE else input.positionUs)
                                    if (stopped != null) {
                                        CaptionDiagnosticJournal.record("CAPTION_SLOW", stopped.elapsedMillis ?: 0L,
                                            "stage=${stopped.stage},budget_ms=${stopped.budgetMillis}")
                                        if (recovery.adaptThroughput()) {
                                            input.limitWindow(recovery.windowMillis)
                                            throughput = CaptionThroughput()
                                        }
                                        // Never publish truly stale dialogue. A slow but still
                                        // bounded result, however, is preferable to a blank screen.
                                        if (stopped.stage == CaptionStage.LAG) {
                                            CaptionDiagnosticJournal.record("CAPTION_SKIP", 0, "reason=lag")
                                            state.value = CaptionPipelineState(stopped.copy(status = CaptionStatus.ADAPTING),
                                                timing = timing)
                                            continue
                                        }
                                    }
                                    if (!CaptionTextQuality.usable(translated)) {
                                        CaptionDiagnosticJournal.record("CAPTION_SKIP", 0, "reason=translated_empty")
                                        continue
                                    }
                                    if (CaptionPresentation.tooOld(window, input.positionUs, CaptionPresentation.MAX_WINDOW_LAG_MS)) {
                                        CaptionDiagnosticJournal.record("CAPTION_SKIP", 0, "reason=stale_after_decode")
                                        continue
                                    }
                                    recovery.succeeded()
                                    val begin = window.startUs + result.startMillis.coerceIn(0, window.durationMillis) * 1000
                                    val end = window.startUs + result.endMillis.coerceIn(0, window.durationMillis) * 1000
                                    CaptionDiagnosticJournal.record("CAPTION_CUE", timing.totalMillis,
                                        "chars=${translated.length},lang=$detected")
                                    state.value = CaptionPipelineState(
                                        CaptionProgress(if (stopped != null) CaptionStatus.ADAPTING else CaptionStatus.LISTENING),
                                        CaptionCue(translated.trim().take(2000), begin, maxOf(begin, end), now()),
                                        timing.totalMillis, timing)
                                }
                            }
                        } finally {
                            if (engine === voice) engine = null
                            if (!voiceClosed) { voice.cancel(); voice.close() }
                        }
                    } finally {
                        if (translator === text) translator = null
                        text.cancel(); text.close()
                    }
                } }
            } catch (error: CancellationException) { throw error }
            catch (error: CaptionStorageException) {
                if (generation == token) state.value = CaptionPipelineState(CaptionProgress(CaptionStatus.LOW_STORAGE,
                    availableMb = error.availableBytes.coerceAtLeast(0) / CaptionResourcePolicy.MIB,
                    requiredMb = CaptionResourcePolicy.ceilingMb(error.requiredBytes)))
            }
            catch (error: CaptionDeadlineException) {
                CaptionDiagnosticJournal.record("CAPTION_FAILURE", error.elapsedMillis,
                    "stage=${error.stage},type=deadline")
                if (generation == token) state.value = CaptionPipelineState(CaptionProgress(CaptionStatus.SLOW_DEVICE,
                    stage = error.stage, elapsedMillis = error.elapsedMillis, budgetMillis = error.budgetMillis))
            }
            catch (_: TimeoutException) {
                CaptionDiagnosticJournal.record("CAPTION_FAILURE", 0, "type=timeout")
                status(CaptionStatus.SLOW_DEVICE)
            }
            catch (error: CaptionMemoryException) {
                CaptionDiagnosticJournal.record("CAPTION_FAILURE", 0, "type=memory")
                if (generation == token) state.value = CaptionPipelineState(CaptionProgress(error.check.failure,
                    availableMb = error.check.availableMb, requiredMb = error.check.requiredMb,
                    stage = error.check.stage, systemThresholdMb = error.check.systemThresholdMb))
            }
            catch (_: OutOfMemoryError) {
                CaptionDiagnosticJournal.record("CAPTION_FAILURE", 0, "type=oom")
                status(CaptionStatus.LOW_MEMORY)
            }
            catch (_: UnsatisfiedLinkError) {
                CaptionDiagnosticJournal.record("CAPTION_FAILURE", 0, "type=native_link")
                status(CaptionStatus.UNAVAILABLE)
            }
            catch (error: Exception) {
                CaptionDiagnosticJournal.record("CAPTION_FAILURE", 0,
                    "type=${error.javaClass.simpleName.take(32)}")
                status(if (settings.translationBackend == TranslationBackend.CLOUD) CaptionStatus.CLOUD_ERROR
                    else CaptionStatus.FAILED)
            } finally { storageCoordinator?.endSession() }
        }
    }
    /** Cancellation never waits for native work on the player thread. */
    fun stop() {
        generation++
        engine?.cancel(); translator?.cancel(); cloudSession?.cancel()
        job?.cancel(); job = null
        state.value = CaptionPipelineState()
    }
}