package mnstudio.apps.mnstreamx.domain.caption

import java.io.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import mnstudio.apps.mnstreamx.domain.model.*
import mnstudio.apps.mnstreamx.domain.repository.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PolishWorkerSafetyTest {
    @Test fun workerAndPlaybackCannotModifyTheSameModels() {
        val guard = File.createTempFile("caption-session", ".lock")
        val host = CaptionStorageCoordinator(guard)
        val worker = CaptionStorageCoordinator(guard)
        try {
            assertTrue(worker.beginSession())
            assertFalse(host.beginMaintenance())
            assertFalse(host.beginSession())
            worker.endSession()
            assertTrue(host.beginMaintenance())
            assertFalse(worker.beginSession())
            host.endMaintenance()
            assertTrue(worker.beginSession())
            worker.endSession()
        } finally { guard.delete() }
    }

    @Test fun nativeColdLoadPreservesSystemThreshold() {
        val mib = CaptionResourcePolicy.MIB
        fun check(available: Long) = CaptionResourcePolicy.memory(CaptionResourcePolicy.POLISH_VOSK_COLD,
            available * mib, 180 * mib, systemLowMemory = false, reserveSystemHeadroom = true)
        assertFalse(check(512).allowed)
        assertEquals(512L, check(512).requiredMb)
        assertTrue(check(513).allowed)
    }

    @Test fun cueTimingAndMemoryDetailsSurviveTransport() {
        val state = CaptionPipelineState(CaptionProgress(CaptionStatus.LOW_MEMORY, availableMb = 300,
            requiredMb = 512, stage = CaptionStage.SPEECH, systemThresholdMb = 180),
            CaptionCue("أهلًا", 100, 200, 20), 8, CaptionTiming(500, 6, 2, 0, 8))
        val bytes = ByteArrayOutputStream()
        ObjectOutputStream(bytes).use { it.writeObject(state) }
        val restored = ObjectInputStream(bytes.toByteArray().inputStream()).use { it.readObject() as CaptionPipelineState }
        assertEquals(state.progress, restored.progress)
        assertEquals(state.cue, restored.cue)
        assertEquals(state.timing, restored.timing)
        assertEquals(state.processingMillis, restored.processingMillis)
    }

    @Test fun workerTerminationCannotFallBackToLocalModelsInThePlayer() = runTest {
        var calls = 0
        val remote = object : PolishCaptionRunner {
            override fun arm() = true
            override suspend fun run(input: CaptionAudioInput, settings: CaptionSettings, publish: (CaptionPipelineState) -> Unit) {
                calls++
                publish(CaptionPipelineState(CaptionProgress(CaptionStatus.POLISH_STOPPED)))
            }
        }
        val models = object : CaptionModels {
            override suspend fun prepare(progress: (Int) -> Unit): File = error("Local model preparation")
        }
        val pipe = CaptionPipeline(this, StandardTestDispatcher(testScheduler), models,
            SpeechEngineFactory { error("Local speech") }, CaptionTranslatorFactory { error("Local translation") },
            CaptionDevice { error("Local native admission") }, polishRunner = remote)
        val input = object : CaptionAudioInput {
            override val positionUs = 0L
            override fun open(): CaptionFeed = error("Unexpected audio subscriber")
        }
        pipe.start(input, CaptionSettings(mode = CaptionMode.GENERATED,
            translationBackend = TranslationBackend.OFFLINE_POLISH_VOSK)).join()
        assertEquals(1, calls)
        assertEquals(CaptionStatus.POLISH_STOPPED, pipe.uiState.value.progress.status)
    }
}