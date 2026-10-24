package com.yourapp.iptv.domain.caption

import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.domain.repository.*
import kotlinx.coroutines.*

/** Runs streaming Azure separately from local inference and its resource admission policy. */
internal class CloudCaptionRunner(private val vault: CloudCredentialVault,
    private val factory: CloudCaptionSessionFactory, private val now: () -> Long) {
    suspend fun run(input: CaptionAudioInput, settings: CaptionSettings,
        active: () -> Boolean, publish: (CaptionPipelineState) -> Unit,
        attach: (CloudCaptionSession?) -> Unit) = coroutineScope {
        val credentials = vault.read()
        if (credentials == null || !credentials.valid()) {
            publish(CaptionPipelineState(CaptionProgress(CaptionStatus.CLOUD_SETUP_REQUIRED)))
            return@coroutineScope
        }
        var session: CloudCaptionSession? = null
        try {
            publish(CaptionPipelineState(CaptionProgress(CaptionStatus.LOADING)))
            val opened = factory.open(credentials, settings.source, settings.target.tag)
            session = opened
            attach(opened)
            opened.start()
            currentCoroutineContext().ensureActive()
            input.limitWindow(2000)
            input.open().use { feed ->
                val output = launch {
                    for (event in opened.events) {
                        if (!active()) break
                        when (event) {
                            is CloudCaptionEvent.Translation -> {
                                val position = input.positionUs
                                val at = if (position == Long.MIN_VALUE) 0 else position
                                publish(CaptionPipelineState(CaptionProgress(CaptionStatus.LISTENING),
                                    CaptionCue(event.text, at, at + 5_000_000L, now())))
                            }
                            CloudCaptionEvent.Disconnected -> {
                                publish(CaptionPipelineState(CaptionProgress(CaptionStatus.CLOUD_ERROR)))
                                this@coroutineScope.cancel("Cloud translation connection ended")
                            }
                        }
                    }
                }
                publish(CaptionPipelineState(CaptionProgress(CaptionStatus.LISTENING)))
                try {
                    while (active()) {
                        val window = withTimeoutOrNull(15000L) { feed.windows.receiveCatching() }?.getOrNull()
                        if (window == null) break
                        currentCoroutineContext().ensureActive()
                        if (window.samples.isEmpty() || CaptionPresentation.tooOld(window, input.positionUs)) continue
                        opened.write(window.samples)
                    }
                } finally { output.cancel() }
            }
        } finally {
            attach(null)
            withContext(NonCancellable) {
                session?.cancel()
                session?.close()
            }
        }
    }
}