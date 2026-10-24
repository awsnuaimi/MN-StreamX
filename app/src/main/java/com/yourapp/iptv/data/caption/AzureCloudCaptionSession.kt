package com.yourapp.iptv.data.caption

import com.microsoft.cognitiveservices.speech.AutoDetectSourceLanguageConfig
import com.microsoft.cognitiveservices.speech.ResultReason
import com.microsoft.cognitiveservices.speech.audio.AudioConfig
import com.microsoft.cognitiveservices.speech.audio.AudioInputStream
import com.microsoft.cognitiveservices.speech.translation.SpeechTranslationConfig
import com.microsoft.cognitiveservices.speech.translation.TranslationRecognizer
import com.yourapp.iptv.domain.repository.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/** One continuous cloud recognizer per playback session; no microphone permission. */
internal class AzureCloudCaptionSession(credentials: CloudCredentials, source: String, target: String) : CloudCaptionSession {
    private val language = AzureSpeechLanguages.targetTag(target)
    private val directCaptioning = source == target && source != "auto"
    private val config = SpeechTranslationConfig.fromSubscription(credentials.key, credentials.region)
    private val stream = AudioInputStream.createPushStream()
    private val audio = AudioConfig.fromStreamInput(stream)
    private val detection: AutoDetectSourceLanguageConfig?
    private val recognizer: TranslationRecognizer
    private val queue = Channel<CloudCaptionEvent>(8, BufferOverflow.DROP_OLDEST)
    override val events: ReceiveChannel<CloudCaptionEvent> = queue
    @Volatile private var canceled = false

    init {
        config.addTargetLanguage(language)
        detection = if (source == "auto") AutoDetectSourceLanguageConfig.fromLanguages(
            AzureSpeechLanguages.autoCandidates()) else null
        if (detection == null) config.setSpeechRecognitionLanguage(
            AzureSpeechLanguages.sourceTag(source) ?: throw IllegalArgumentException("Unsupported source language"))
        recognizer = if (detection != null) TranslationRecognizer(config, detection, audio)
            else TranslationRecognizer(config, audio)
        recognizer.recognized.addEventListener { _, event ->
            if (!canceled) {
                val text = when (event.result.reason) {
                    ResultReason.TranslatedSpeech -> event.result.translations[language]?.trim().orEmpty()
                    ResultReason.RecognizedSpeech -> if (directCaptioning) event.result.text.trim() else ""
                    else -> ""
                }
                if (text.isNotEmpty()) queue.trySend(CloudCaptionEvent.Translation(text.take(2000)))
            }
        }
        recognizer.canceled.addEventListener { _, _ ->
            if (!canceled) queue.trySend(CloudCaptionEvent.Disconnected)
        }
    }

    override suspend fun start() {
        withContext(Dispatchers.IO) {
            recognizer.startContinuousRecognitionAsync().get(12, TimeUnit.SECONDS)
            Unit
        }
    }

    override suspend fun write(samples: FloatArray) = withContext(Dispatchers.IO) {
        if (!canceled) stream.write(CloudPcm16.encode(samples))
    }

    override fun cancel() { canceled = true; queue.close() }

    override fun close() {
        canceled = true
        queue.close()
        // This function runs on the caption worker, never on the player/UI thread.
        runCatching { stream.close() }
        runCatching { recognizer.stopContinuousRecognitionAsync().get(2, TimeUnit.SECONDS) }
        runCatching { recognizer.close() }
        runCatching { audio.close() }
        runCatching { detection?.close() }
        runCatching { config.close() }
    }
}