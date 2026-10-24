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
class CloudCaptionRunnerTest {
    private class Vault(var value: CloudCredentials? = CloudCredentials("this-is-a-test-key", "germanywestcentral")) : CloudCredentialVault {
        override suspend fun read() = value
        override suspend fun save(value: CloudCredentials) { this.value = value }
        override suspend fun clear() { value = null }
    }
    private class Session : CloudCaptionSession {
        override val events = Channel<CloudCaptionEvent>(8)
        var started = 0
        var writes = 0
        var cancelled = 0
        var closed = 0
        override suspend fun start() { started++ }
        override suspend fun write(samples: FloatArray) { writes++ }
        override fun cancel() { cancelled++; events.close() }
        override fun close() { closed++ }
    }
    private class Input : CaptionAudioInput {
        val frames = Channel<AudioWindow>(2)
        var closed = false
        var limit = 0
        override val positionUs = 10_000_000L
        override fun limitWindow(millis: Int) { limit = millis }
        override fun open(): CaptionFeed = CaptionFeed(frames) { closed = true }
    }
    @Test fun cloudModeNeverPreparesLocalModelsAndStreamsAudio() = runTest {
        val session = Session()
        val input = Input()
        var localLoads = 0
        var voiceOpens = 0
        var localTranslationOpens = 0
        val pipeline = CaptionPipeline(backgroundScope, StandardTestDispatcher(testScheduler),
            object : CaptionModels {
                override suspend fun prepare(progress: (Int) -> Unit): File {
                    localLoads++; return File("unused")
                }
            }, SpeechEngineFactory { voiceOpens++; error("must not run") },
            CaptionTranslatorFactory { localTranslationOpens++; error("must not run") },
            CaptionDevice { false }, Vault(), CloudCaptionSessionFactory { _, _, _ -> session })
        pipeline.start(input, CaptionSettings(mode = CaptionMode.GENERATED, translationBackend = TranslationBackend.CLOUD))
        runCurrent()
        assertEquals(1, session.started)
        assertEquals(2000, input.limit)
        input.frames.send(AudioWindow(FloatArray(32000), 0))
        runCurrent()
        assertEquals(1, session.writes)
        session.events.send(CloudCaptionEvent.Translation("ترجمة عبر Azure"))
        runCurrent()
        assertEquals("ترجمة عبر Azure", pipeline.uiState.value.cue?.text)
        assertEquals(0, localLoads)
        assertEquals(0, voiceOpens)
        assertEquals(0, localTranslationOpens)
        pipeline.stop()
        runCurrent()
        assertTrue(input.closed)
        assertTrue(session.cancelled >= 1)
        assertEquals(1, session.closed)
    }
    @Test fun noKeyFailsClosedWithoutStartingTheCloudSession() = runTest {
        var opened = 0
        val pipeline = CaptionPipeline(backgroundScope, StandardTestDispatcher(testScheduler),
            object : CaptionModels { override suspend fun prepare(progress: (Int) -> Unit) = File("unused") },
            SpeechEngineFactory { error("unexpected") }, CaptionTranslatorFactory { error("unexpected") },
            CaptionDevice { true }, Vault(null), CloudCaptionSessionFactory { _, _, _ -> opened++; error("unexpected") })
        pipeline.start(Input(), CaptionSettings(mode = CaptionMode.GENERATED, translationBackend = TranslationBackend.CLOUD))
        runCurrent()
        assertEquals(CaptionStatus.CLOUD_SETUP_REQUIRED, pipeline.uiState.value.progress.status)
        assertEquals(0, opened)
        pipeline.stop()
    }
    @Test fun changingChannelStopsOldResultsFromReappearing() = runTest {
        val first = Session()
        val vault = Vault()
        val input = Input()
        val pipeline = CaptionPipeline(backgroundScope, StandardTestDispatcher(testScheduler),
            object : CaptionModels { override suspend fun prepare(progress: (Int) -> Unit) = File("unused") },
            SpeechEngineFactory { error("unexpected") }, CaptionTranslatorFactory { error("unexpected") },
            CaptionDevice { true }, vault, CloudCaptionSessionFactory { _, _, _ -> first })
        pipeline.start(input, CaptionSettings(mode = CaptionMode.GENERATED, translationBackend = TranslationBackend.CLOUD))
        runCurrent()
        pipeline.stop()
        first.events.trySend(CloudCaptionEvent.Translation("stale result"))
        runCurrent()
        assertNull(pipeline.uiState.value.cue)
        assertEquals(CaptionStatus.OFF, pipeline.uiState.value.progress.status)
    }
}