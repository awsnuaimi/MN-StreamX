package com.yourapp.iptv.domain.caption

import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.domain.repository.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class CaptionPipelineTest {
    private class Input : CaptionAudioInput {
        override var positionUs: Long = 0
        var closed = false
        val audio = Channel<AudioWindow>(1)
        override fun open() = CaptionFeed(audio) { closed = true }
        suspend fun speech(pts: Long = 0, millis: Int = 5000) {
            audio.send(AudioWindow(FloatArray(millis * 16) { 0.2f }, pts))
        }
    }
    private class Engines {
        var downloads = 0; var prepares = 0; val stages = mutableListOf<String>()
        var modelReads = 0; var opened = 0; var closed = 0; var cancelled = 0; var translationsClosed = 0
        var gate: CompletableDeferred<Unit>? = null
        var uncooperative = false; var available = true; var failPrepare = false
        var time = 0L; var cost = 100L
        val models = object : CaptionModels {
            override suspend fun prepare(progress: (Int) -> Unit): File { modelReads++; stages += "speech-files"; progress(100); return File("verified-model") }
        }
        val factory = SpeechEngineFactory {
            opened++
            object : SpeechEngine {
                override suspend fun transcribe(samples: FloatArray, language: String): SpeechText {
                    if (uncooperative) withContext(NonCancellable) { gate?.await() } else gate?.await()
                    time += cost
                    return SpeechText("en", "Good morning", 0, 4000)
                }
                override fun cancel() { cancelled++ }
                override fun close() { closed++ }
            }
        }
        val text = CaptionTranslatorFactory {
            object : CaptionTranslator {
                override suspend fun download(target: String, progress: (Int) -> Unit) { downloads++; stages += "translation-files" }
                override suspend fun prepare(target: String, progress: (Int) -> Unit) { prepares++; if (failPrepare) error("load failed") }
                override suspend fun translate(text: String) = "translated:$text"
                override fun cancel() = Unit
                override fun close() { translationsClosed++ }
            }
        }
        fun TestScope.pipeline(device: CaptionDevice = CaptionDevice { available }) = CaptionPipeline(backgroundScope,
            StandardTestDispatcher(testScheduler), models, factory, text, device, now = { time })
    }
    private val enabled = CaptionSettings(mode = CaptionMode.GENERATED)
    @Test fun optionalPipelineDoesNothingBeforeExplicitActivation() = runTest {
        val e = Engines(); with(e) { pipeline() }; runCurrent()
        assertEquals(0, e.modelReads); assertEquals(0, e.opened)
    }
    @Test fun currentAudioIsTranscribedTranslatedAndReleasedOnStop() = runTest {
        val e = Engines(); val p = with(e) { pipeline() }; val input = Input()
        p.start(input, enabled); runCurrent(); input.speech(); runCurrent()
        assertEquals("translated:Good morning", p.uiState.value.cue?.text)
        p.stop(); runCurrent()
        assertTrue(input.closed); assertEquals(1, e.closed); assertEquals(1, e.translationsClosed)
        assertNull(p.uiState.value.cue)
    }
    @Test fun anUncooperativeOldChannelCannotPublishAfterSwitching() = runTest {
        val e = Engines().apply { gate = CompletableDeferred(); uncooperative = true }
        val p = with(e) { pipeline() }; val old = Input(); val fresh = Input()
        p.start(old, enabled); runCurrent(); old.speech(); runCurrent()
        p.start(fresh, enabled); runCurrent(); e.gate!!.complete(Unit); runCurrent()
        assertNull(p.uiState.value.cue); assertTrue(old.closed)
        fresh.speech(5000000); runCurrent()
        assertEquals(5000000L, p.uiState.value.cue?.startUs)
        p.stop(); runCurrent(); assertEquals(e.opened, e.closed)
    }
    @Test fun silenceAndStaleAudioDoNotReachNativeInference() = runTest {
        val e = Engines(); val p = with(e) { pipeline() }; val input = Input()
        p.start(input, enabled); runCurrent()
        input.audio.send(AudioWindow(FloatArray(0), 0)); runCurrent(); assertNull(p.uiState.value.cue)
        input.positionUs = 30000000; input.speech(); runCurrent()
        assertNull(p.uiState.value.cue); assertEquals(0L, e.time)
        p.stop(); runCurrent()
    }
    @Test fun insufficientRamStillDownloadsFilesWithoutOpeningAnyNativeEngine() = runTest {
        val e = Engines().apply { available = false }; val p = with(e) { pipeline() }
        p.start(Input(), enabled); runCurrent()
        assertEquals(CaptionStatus.LOW_MEMORY, p.uiState.value.progress.status)
        assertEquals(1, e.modelReads); assertEquals(1, e.downloads)
        assertEquals(0, e.prepares); assertEquals(0, e.opened); assertEquals(1, e.translationsClosed)
    }
    @Test fun losingAvailableMemoryStopsInferenceAndReleasesResources() = runTest {
        val e = Engines(); val p = with(e) { pipeline() }; val input = Input()
        p.start(input, enabled); runCurrent(); e.available = false; input.speech(); runCurrent()
        assertEquals(CaptionStatus.LOW_MEMORY, p.uiState.value.progress.status)
        assertEquals(1, e.closed); assertTrue(input.closed)
    }
    @Test fun sustainedSlowInferenceStopsInsteadOfAccumulatingDelay() = runTest {
        val e = Engines().apply { cost = 7000 }; val p = with(e) { pipeline() }; val input = Input()
        p.start(input, enabled); runCurrent()
        for (i in 0..2) { input.speech(i * 5000000L); runCurrent() }
        assertEquals(CaptionStatus.ADAPTING, p.uiState.value.progress.status)
        for (i in 3..5) { input.speech(i * 5000000L, 3500); runCurrent() }
        assertEquals(CaptionStatus.ADAPTING, p.uiState.value.progress.status)
        for (i in 6..8) { input.speech(i * 5000000L, 2500); runCurrent() }
        assertEquals(CaptionStatus.SLOW_DEVICE, p.uiState.value.progress.status)
        assertEquals(1, e.closed); assertTrue(input.closed)
    }
    @Test fun failedTranslationPreparationClosesPartialResourcesWithoutOpeningSpeech() = runTest {
        val e = Engines().apply { failPrepare = true }; val p = with(e) { pipeline() }
        p.start(Input(), enabled); runCurrent()
        assertEquals(CaptionStatus.FAILED, p.uiState.value.progress.status)
        assertEquals(0, e.opened); assertEquals(1, e.translationsClosed)
    }
    @Test fun absentDecodedAudioStopsAndReleasesModelsAfterTwelveSeconds() = runTest {
        val e = Engines(); val p = with(e) { pipeline() }; val input = Input()
        p.start(input, enabled); runCurrent(); advanceTimeBy(12001); runCurrent()
        assertEquals(CaptionStatus.UNAVAILABLE, p.uiState.value.progress.status)
        assertEquals(1, e.closed); assertTrue(input.closed)
    }
    @Test fun silentHeartbeatsKeepTheFeedAliveWithoutInference() = runTest {
        val e = Engines(); val p = with(e) { pipeline() }; val input = Input()
        p.start(input, enabled); runCurrent()
        repeat(4) { advanceTimeBy(5000); input.audio.send(AudioWindow(FloatArray(0), it * 5000000L)); runCurrent() }
        assertEquals(CaptionStatus.LISTENING, p.uiState.value.progress.status)
        assertEquals(0L, e.time); p.stop(); runCurrent()
    }
    @Test fun ramIsCheckedAfterBothDownloadsAndTheExactDeficitReachesTheUi() = runTest {
        val e = Engines()
        val device = object : CaptionDevice {
            override fun canRun(target: String) = false
            override fun assess(target: String): CaptionResourceCheck {
                assertEquals(listOf("speech-files", "translation-files"), e.stages)
                return CaptionResourcePolicy.memory(target, 200 * CaptionResourcePolicy.MIB, 64 * CaptionResourcePolicy.MIB, false)
            }
        }
        val p = with(e) { pipeline(device) }; p.start(Input(), enabled); runCurrent()
        assertEquals(CaptionStatus.LOW_MEMORY, p.uiState.value.progress.status)
        assertEquals(200L, p.uiState.value.progress.availableMb); assertEquals(224L, p.uiState.value.progress.requiredMb)
        assertEquals(0, e.opened); assertEquals(0, e.prepares)
    }
    @Test fun unsupportedArchitectureDoesNotMasqueradeAsLowRamOrDownloadModels() = runTest {
        val e = Engines(); val device = object : CaptionDevice {
            override fun canRun(target: String) = false
            override fun supported() = false
        }
        val p = with(e) { pipeline(device) }; p.start(Input(), enabled); runCurrent()
        assertEquals(CaptionStatus.UNAVAILABLE, p.uiState.value.progress.status); assertEquals(0, e.modelReads)
    }
    @Test fun pendingInferenceIsCancelledWhenThePlayerStops() = runTest {
        val e = Engines().apply { gate = CompletableDeferred() }; val p = with(e) { pipeline() }; val input = Input()
        p.start(input, enabled); runCurrent(); input.speech(); runCurrent(); p.stop(); runCurrent()
        assertTrue(e.cancelled > 0); assertEquals(1, e.closed); assertTrue(input.closed); assertNull(p.uiState.value.cue)
    }
}