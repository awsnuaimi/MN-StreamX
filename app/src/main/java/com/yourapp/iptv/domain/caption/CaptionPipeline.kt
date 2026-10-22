package com.yourapp.iptv.domain.caption

import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.domain.repository.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.TimeoutException

class CaptionPipelineState(val progress: CaptionProgress = CaptionProgress(), val cue: CaptionCue? = null,
    val processingMillis: Long? = null)

/** Serial inference, one waiting audio window, cancellable native calls and generation-checked output. */
class CaptionPipeline(private val scope: CoroutineScope, private val worker: CoroutineDispatcher,
    private val models: CaptionModels, private val speech: SpeechEngineFactory,
    private val translators: CaptionTranslatorFactory, private val device: CaptionDevice,
    private val now: () -> Long = { System.nanoTime() / 1000000 }) {
    private val state = MutableStateFlow(CaptionPipelineState())
    val uiState = state.asStateFlow()
    @Volatile private var generation = 0L
    private var job: Job? = null
    @Volatile private var engine: SpeechEngine? = null
    @Volatile private var translator: CaptionTranslator? = null

    fun start(input: CaptionAudioInput, settings: CaptionSettings) {
        stop()
        val token = generation
        fun status(value: CaptionStatus, percent: Int? = null) {
            if (generation == token) state.value = CaptionPipelineState(CaptionProgress(value, percent))
        }
        fun ramAllowed(target: String): Boolean {
            val check = device.assess(target)
            if (!check.allowed && generation == token) state.value = CaptionPipelineState(CaptionProgress(
                check.failure, availableMb = check.availableMb, requiredMb = check.requiredMb))
            return check.allowed
        }
        status(CaptionStatus.PREPARING)
        job = scope.launch {
            try {
                withContext(worker) {
                    if (!device.supported()) { status(CaptionStatus.UNAVAILABLE); return@withContext }
                    status(CaptionStatus.DOWNLOADING, 0)
                    val model = models.prepare { status(CaptionStatus.DOWNLOADING, it) }
                    currentCoroutineContext().ensureActive()
                    val text = translators.create()
                    translator = text
                    try {
                        text.download(settings.target.tag) { status(CaptionStatus.TRANSLATING, it) }
                        currentCoroutineContext().ensureActive()
                        if (!ramAllowed(CaptionResourcePolicy.SPEECH)) return@withContext
                        status(CaptionStatus.LOADING)
                        text.prepare(settings.target.tag) { status(CaptionStatus.TRANSLATING, it) }
                        currentCoroutineContext().ensureActive()
                        if (!ramAllowed(CaptionResourcePolicy.SPEECH)) return@withContext
                        status(CaptionStatus.LOADING)
                        val voice = speech.open(model)
                        engine = voice
                        try {
                            currentCoroutineContext().ensureActive()
                            input.open().use { feed ->
                                var slow = 0
                                status(CaptionStatus.LISTENING)
                                while (true) {
                                    val received = withTimeoutOrNull(12000) { feed.windows.receiveCatching() }
                                    val window = received?.getOrNull()
                                    if (window == null) { status(CaptionStatus.UNAVAILABLE); break }
                                    currentCoroutineContext().ensureActive()
                                    if (window.samples.size < 16000) continue
                                    if (CaptionPresentation.tooOld(window, input.positionUs)) continue
                                    if (!ramAllowed(if (settings.target.tag == "en") CaptionResourcePolicy.RUNNING else CaptionResourcePolicy.SPEECH)) break
                                    val started = now()
                                    val result = voice.transcribe(window.samples, settings.source)
                                    currentCoroutineContext().ensureActive()
                                    if (settings.target.tag != "en") voice.releaseWorkingMemory()
                                    val translated = if (result.text.isBlank()) "" else text.translate(result.text)
                                    currentCoroutineContext().ensureActive()
                                    val elapsed = (now() - started).coerceAtLeast(0)
                                    slow = if (elapsed > window.durationMillis * 1.2) slow + 1 else 0
                                    if (slow >= 2 || elapsed > 10000) { status(CaptionStatus.SLOW_DEVICE); break }
                                    if (token != generation) break
                                    if (translated.isBlank()) continue
                                    val begin = window.startUs + result.startMillis.coerceIn(0, window.durationMillis) * 1000
                                    val end = window.startUs + result.endMillis.coerceIn(0, window.durationMillis) * 1000
                                    state.value = CaptionPipelineState(CaptionProgress(CaptionStatus.LISTENING),
                                        CaptionCue(translated.trim().take(2000), begin, maxOf(begin, end), now()), elapsed)
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
                }
            } catch (error: CancellationException) { throw error }
            catch (error: CaptionStorageException) {
                if (generation == token) state.value = CaptionPipelineState(CaptionProgress(CaptionStatus.LOW_STORAGE,
                    availableMb = error.availableBytes.coerceAtLeast(0) / CaptionResourcePolicy.MIB,
                    requiredMb = CaptionResourcePolicy.ceilingMb(error.requiredBytes)))
            }
            catch (_: TimeoutException) { status(CaptionStatus.SLOW_DEVICE) }
            catch (error: CaptionMemoryException) {
                if (generation == token) state.value = CaptionPipelineState(CaptionProgress(error.check.failure,
                    availableMb = error.check.availableMb, requiredMb = error.check.requiredMb))
            }
            catch (_: OutOfMemoryError) { status(CaptionStatus.LOW_MEMORY) }
            catch (_: UnsatisfiedLinkError) { status(CaptionStatus.UNAVAILABLE) }
            catch (_: Exception) { status(CaptionStatus.FAILED) }
        }
    }

    /** Never joins native work on the UI/audio thread. The serial worker releases it before opening another engine. */
    fun stop() {
        generation++
        engine?.cancel(); translator?.cancel()
        job?.cancel(); job = null
        state.value = CaptionPipelineState()
    }
}