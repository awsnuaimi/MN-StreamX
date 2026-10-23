package com.yourapp.iptv.domain.caption

import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.domain.repository.*
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
    private val now: () -> Long = { System.nanoTime() / 1000000 }) {
    private val state = MutableStateFlow(CaptionPipelineState())
    val uiState = state.asStateFlow()
    @Volatile private var generation = 0L
    private var job: Job? = null
    private val inferenceLock = Mutex()
    @Volatile private var engine: SpeechEngine? = null
    @Volatile private var translator: CaptionTranslator? = null

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
        val direct = settings.source != "auto" && settings.source == settings.target.tag
        val textTarget = if (direct) "en" else settings.target.tag
        status(CaptionStatus.PREPARING)
        job = scope.launch {
            try {
                withContext(worker) { inferenceLock.withLock {
                    if (!device.supported()) { status(CaptionStatus.UNAVAILABLE); return@withLock }
                    status(CaptionStatus.DOWNLOADING, 0)
                    val model = models.prepare { status(CaptionStatus.DOWNLOADING, it) }
                    currentCoroutineContext().ensureActive()
                    val text = translators.create()
                    translator = text
                    try {
                        text.download(textTarget) { status(CaptionStatus.TRANSLATING, it) }
                        currentCoroutineContext().ensureActive()
                        if (!ramAllowed(CaptionResourcePolicy.SPEECH)) return@withLock
                        status(CaptionStatus.LOADING)
                        text.prepare(textTarget) { status(CaptionStatus.TRANSLATING, it) }
                        currentCoroutineContext().ensureActive()
                        if (!ramAllowed(CaptionResourcePolicy.SPEECH, text::releaseWorkingMemory)) return@withLock
                        val voice = speech.open(model)
                        engine = voice
                        try {
                            currentCoroutineContext().ensureActive()
                            val recovery = CaptionWindowRecovery()
                            input.limitWindow(recovery.windowMillis)
                            input.open().use { feed ->
                                var throughput = CaptionThroughput()
                                status(CaptionStatus.LISTENING)
                                while (true) {
                                    val window = withTimeoutOrNull(12000) { feed.windows.receiveCatching() }?.getOrNull()
                                    if (window == null) { status(CaptionStatus.UNAVAILABLE); break }
                                    currentCoroutineContext().ensureActive()
                                    if (window.samples.size < 16000 || !recovery.accepts(window) ||
                                        CaptionPresentation.tooOld(window, input.positionUs)) continue
                                    // A parked speech engine needs a fresh compute state. Do not let a
                                    // retained Marian decoder overlap that allocation on low-memory TVs.
                                    if (textTarget != "en") text.releaseWorkingMemory()
                                    if (!ramAllowed(voice.memoryStage, text::releaseWorkingMemory)) break
                                    val started = now()
                                    val result = voice.transcribe(window.samples, settings.source, settings.target.tag)
                                    currentCoroutineContext().ensureActive()
                                    val speechEnded = now()
                                    val needsText = textTarget != "en" && !direct &&
                                        result.language != settings.target.tag && result.text.isNotBlank()
                                    if (needsText) voice.releaseWorkingMemory()
                                    val translationStarted = now()
                                    val translated = when {
                                        result.text.isBlank() -> ""
                                        direct || result.language == settings.target.tag -> result.text
                                        else -> try { text.translate(result.text) }
                                        catch (error: CaptionDeadlineException) {
                                            currentCoroutineContext().ensureActive()
                                            if (token != generation) break
                                            if (error.stage != CaptionStage.TRANSLATION || !recovery.retry()) throw error
                                            text.releaseWorkingMemory()
                                            input.limitWindow(recovery.windowMillis)
                                            throughput = CaptionThroughput()
                                            status(CaptionStatus.ADAPTING)
                                            continue
                                        }
                                    }
                                    currentCoroutineContext().ensureActive()
                                    val finished = now()
                                    val timing = CaptionTiming(window.durationMillis, (speechEnded - started).coerceAtLeast(0),
                                        (finished - translationStarted).coerceAtLeast(0),
                                        (translationStarted - speechEnded).coerceAtLeast(0), (finished - started).coerceAtLeast(0))
                                    if (token != generation) break
                                    val stopped = throughput.assess(window, timing.totalMillis, input.positionUs)
                                    if (stopped != null) { state.value = CaptionPipelineState(stopped, timing = timing); break }
                                    if (translated.isBlank()) continue
                                    recovery.succeeded()
                                    val begin = window.startUs + result.startMillis.coerceIn(0, window.durationMillis) * 1000
                                    val end = window.startUs + result.endMillis.coerceIn(0, window.durationMillis) * 1000
                                    state.value = CaptionPipelineState(CaptionProgress(CaptionStatus.LISTENING),
                                        CaptionCue(translated.trim().take(2000), begin, maxOf(begin, end), now()), timing.totalMillis, timing)
                                }
                            }
                        } finally {
                            if (engine === voice) engine = null
                            voice.cancel(); voice.close()
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
                if (generation == token) state.value = CaptionPipelineState(CaptionProgress(CaptionStatus.SLOW_DEVICE,
                    stage = error.stage, elapsedMillis = error.elapsedMillis, budgetMillis = error.budgetMillis))
            }
            catch (_: TimeoutException) { status(CaptionStatus.SLOW_DEVICE) }
            catch (error: CaptionMemoryException) {
                if (generation == token) state.value = CaptionPipelineState(CaptionProgress(error.check.failure,
                    availableMb = error.check.availableMb, requiredMb = error.check.requiredMb,
                    stage = error.check.stage, systemThresholdMb = error.check.systemThresholdMb))
            }
            catch (_: OutOfMemoryError) { status(CaptionStatus.LOW_MEMORY) }
            catch (_: UnsatisfiedLinkError) { status(CaptionStatus.UNAVAILABLE) }
            catch (_: Exception) { status(CaptionStatus.FAILED) }
        }
    }
    /** Cancellation never waits for native work on the player thread. */
    fun stop() {
        generation++
        engine?.cancel(); translator?.cancel()
        job?.cancel(); job = null
        state.value = CaptionPipelineState()
    }
}