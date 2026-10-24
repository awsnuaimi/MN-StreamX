package com.yourapp.iptv.viewmodel

import androidx.lifecycle.ViewModelStore
import com.yourapp.iptv.domain.caption.*
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.domain.repository.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class CaptionViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { store.clear(); Dispatchers.resetMain() }
    private class Repository : CaptionRepository {
        val values = MutableStateFlow(CaptionSettings())
        var failRead = false; var failSave = false; var reads = 0
        override fun observe() = flow {
            reads++; if (failRead) throw IOException("read")
            emitAll(values)
        }
        override suspend fun save(value: CaptionSettings) {
            if (failSave) throw IOException("save")
            values.value = value
        }
    }
    private class Input : CaptionAudioInput {
        override val positionUs = 0L
        var closed = false
        override fun open() = CaptionFeed(Channel<AudioWindow>(1)) { closed = true }
    }
    private var modelsRead = 0
    private var enginesClosed = 0
    private val models = object : CaptionModels {
        override suspend fun prepare(progress: (Int) -> Unit): File { modelsRead++; return File("verified") }
    }
    private fun model(repository: Repository): CaptionViewModel {
        val speech = SpeechEngineFactory { object : SpeechEngine {
            override suspend fun transcribe(samples: FloatArray, language: String) = SpeechText("en", "hello", 0, 1000)
            override fun cancel() = Unit
            override fun close() { enginesClosed++ }
        } }
        val text = CaptionTranslatorFactory { object : CaptionTranslator {
            override suspend fun prepare(target: String, progress: (Int) -> Unit) = Unit
            override suspend fun translate(text: String) = text
            override fun cancel() = Unit
            override fun close() = Unit
        } }
        return CaptionViewModel(repository, dispatcher, models, speech, text, CaptionDevice { true },
            object : CloudCredentialVault {
                override suspend fun read(): CloudCredentials? = null
                override suspend fun save(value: CloudCredentials) = Unit
                override suspend fun clear() = Unit
            }, CloudCaptionSessionFactory { _, _, _ -> error("unused in offline tests") })
            .also { store.put("captions", it) }
    }
    @Test fun disabledAndEmbeddedModesNeverLoadModels() = runTest(dispatcher) {
        val repository = Repository(); val model = model(repository)
        model.bind(Input(), "channel"); runCurrent(); assertEquals(0, modelsRead)
        model.save(CaptionSettings(mode = CaptionMode.EMBEDDED)); runCurrent(); assertEquals(0, modelsRead)
        assertTrue(model.uiState.value.loaded)
    }
    @Test fun styleEditsKeepTheRunningEngineButTargetChangesRestartIt() = runTest(dispatcher) {
        val repository = Repository().apply { values.value = CaptionSettings(mode = CaptionMode.GENERATED) }
        val model = model(repository); model.bind(Input(), "channel"); runCurrent(); assertEquals(1, modelsRead)
        model.save(repository.values.value.copy(shaded = false, textSize = CaptionTextSize.LARGE)); runCurrent()
        assertEquals(1, modelsRead); assertEquals(0, enginesClosed)
        model.save(repository.values.value.copy(target = CaptionLanguage.GERMAN)); runCurrent()
        assertEquals(2, modelsRead); assertEquals(1, enginesClosed)
        model.bind(null, null); runCurrent(); assertEquals(2, enginesClosed)
    }
    @Test fun readFailureDoesNotActivateInferenceAndExplicitRetryRecovers() = runTest(dispatcher) {
        val repository = Repository().apply { failRead = true; values.value = CaptionSettings(mode = CaptionMode.GENERATED) }
        val model = model(repository); model.bind(Input(), "channel"); runCurrent()
        assertTrue(model.uiState.value.readFailed); assertEquals(0, modelsRead)
        advanceTimeBy(3000); runCurrent(); assertEquals(1, repository.reads)
        repository.failRead = false; model.reload(); runCurrent()
        assertTrue(model.uiState.value.loaded); assertEquals(1, modelsRead)
        model.bind(null, null); runCurrent()
    }
    @Test fun choosingCloudStopsLocalInferenceWithoutLoadingAnotherModel() = runTest(dispatcher) {
        val repository = Repository().apply { values.value = CaptionSettings(mode = CaptionMode.GENERATED) }
        val model = model(repository)
        model.bind(Input(), "channel"); runCurrent()
        assertEquals(1, modelsRead)
        model.save(repository.values.value.copy(translationBackend = TranslationBackend.CLOUD)); runCurrent()
        assertEquals(1, modelsRead)
        assertEquals(1, enginesClosed)
        assertEquals(CaptionStatus.CLOUD_SETUP_REQUIRED, model.generated.value.progress.status)
        model.bind(null, null); runCurrent()
    }
    @Test fun failedSaveKeepsTheActualSettingAndClearingReleasesInference() = runTest(dispatcher) {
        val repository = Repository().apply { failSave = true }
        val model = model(repository); model.bind(Input(), "channel"); runCurrent()
        model.save(CaptionSettings(mode = CaptionMode.GENERATED)); runCurrent()
        assertTrue(model.uiState.value.saveFailed); assertEquals(CaptionMode.OFF, model.uiState.value.settings.mode)
        assertEquals(0, modelsRead)
        repository.failSave = false; model.save(CaptionSettings(mode = CaptionMode.GENERATED)); runCurrent()
        store.clear(); runCurrent(); assertEquals(1, enginesClosed)
    }
}